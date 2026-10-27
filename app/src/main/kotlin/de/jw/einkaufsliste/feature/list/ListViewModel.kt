package de.jw.einkaufsliste.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.db.ShopEntity
import de.jw.einkaufsliste.data.repo.ListSection
import de.jw.einkaufsliste.feature.shops.ShopRefreshResult
import de.jw.einkaufsliste.feature.shops.ShopRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the list screen shows. [loaded] is false until the database answered once. */
data class ListUiState(
    val loaded: Boolean = false,
    val sections: List<ListSection> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    /**
     * The card that says the shop check is off: shown while it is off and the user did not
     * hide the card. False until the settings are read, so the card does not flash.
     */
    val showCheckBanner: Boolean = false,
    /** Cached shops by id, for pinned-shop names and the shop picker. */
    val shops: Map<String, ShopEntity> = emptyMap(),
    /** False until the shop cache was read; until then a missing pinned shop is not reported as gone. */
    val shopsLoaded: Boolean = false,
) {
    val allItems: List<ItemEntity> = sections.flatMap { it.items }
    val openCount: Int get() = sections.sumOf { it.openCount }
    val doneCount: Int get() = allItems.size - openCount
    val isEmpty: Boolean get() = allItems.isEmpty()
}

/**
 * "Läden laden" in the shop picker: [loading] while it runs, then [message] (German) when it
 * did not bring any shops. [permissionMissing]: the location permission was refused.
 */
data class ShopLoadState(
    val loading: Boolean = false,
    val message: String? = null,
    val permissionMissing: Boolean = false,
)

/** The fields of the add/edit dialog, already trimmed; blank optional fields are null. */
data class ItemDraft(
    val name: String,
    val quantity: String?,
    val note: String?,
    val categoryId: Long?,
    val pinnedShopId: String?,
)

class ListViewModel(private val container: AppContainer) : ViewModel() {
    private val items = container.itemRepository
    private val categories = container.categoryRepository

    // Starts with null so the list does not wait for the shop cache.
    private val shopsById: Flow<Map<String, ShopEntity>?> =
        flow { emitAll(container.shopRepository.observeShops()) }
            .map<List<ShopEntity>, Map<String, ShopEntity>?> { shops -> shops.associateBy { it.id } }
            .catch { emit(emptyMap()) }
            .onStart { emit(null) }

    val state: StateFlow<ListUiState> =
        combine(
            items.observeSections(),
            categories.observeAll(),
            container.settingsRepository.settings.map { !it.checkEnabled && !it.hideCheckBanner }.distinctUntilChanged(),
            shopsById,
        ) { sections, categories, showCheckBanner, shops ->
            ListUiState(
                loaded = true,
                sections = sections,
                categories = categories,
                showCheckBanner = showCheckBanner,
                shops = shops.orEmpty(),
                shopsLoaded = shops != null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    private val _here = MutableStateFlow<GeoPoint?>(null)

    /** The current location once [locate] found one; used to show distances in the shop picker. */
    val here: StateFlow<GeoPoint?> = _here.asStateFlow()
    private var locating = false

    private val _shopLoad = MutableStateFlow(ShopLoadState())

    /** Progress and outcome of [loadShops]. */
    val shopLoad: StateFlow<ShopLoadState> = _shopLoad.asStateFlow()

    /** Looks up the current location once, if the permission is granted. Never throws. */
    fun locate() {
        if (_here.value != null || locating) return
        locating = true
        viewModelScope.launch {
            try {
                val provider = container.locationProvider
                if (provider.hasForegroundPermission()) _here.value = provider.currentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // No distances then; the picker works without them.
            } finally {
                locating = false
            }
        }
    }

    /**
     * "Läden laden" in the shop picker, when no shops are cached yet: locates once and downloads
     * the shops around (the same download "In der Nähe" and the background check do). The
     * screen asks for the location permission first. Never throws.
     */
    fun loadShops() {
        if (_shopLoad.value.loading) return
        _shopLoad.value = ShopLoadState(loading = true)
        viewModelScope.launch {
            _shopLoad.value = try {
                downloadShops()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ShopLoadState(message = ShopRepository.FAILED_MESSAGE)
            }
        }
    }

    private suspend fun downloadShops(): ShopLoadState {
        val provider = container.locationProvider
        if (!provider.hasForegroundPermission()) {
            return ShopLoadState(
                message = "Ohne Zugriff auf deinen Standort kann die App keine Läden in deiner Nähe laden.",
                permissionMissing = true,
            )
        }
        val location = provider.currentLocation()
            ?: return ShopLoadState(message = "Kein Standort verfügbar. Ist die Ortung an?")
        _here.value = location
        val found = when (val result = container.shopRepository.ensureShopsAround(location)) {
            is ShopRefreshResult.Failed -> return ShopLoadState(message = result.message)
            is ShopRefreshResult.Refreshed -> result.count
            is ShopRefreshResult.UpToDate -> result.cachedCount
        }
        return if (found == 0) ShopLoadState(message = "In deiner Umgebung sind bei OpenStreetMap keine Läden eingetragen.") else ShopLoadState()
    }

    fun setDone(id: Long, done: Boolean) {
        viewModelScope.launch { items.setDone(id, done) }
    }

    fun add(draft: ItemDraft) {
        viewModelScope.launch {
            items.add(
                name = draft.name,
                categoryId = existingCategoryId(draft.categoryId),
                quantity = draft.quantity,
                note = draft.note,
            )
        }
    }

    fun save(id: Long, draft: ItemDraft) {
        viewModelScope.launch {
            val current = items.getById(id) ?: return@launch
            items.update(
                current.copy(
                    name = draft.name,
                    quantity = draft.quantity,
                    note = draft.note,
                    categoryId = existingCategoryId(draft.categoryId),
                    pinnedShopId = draft.pinnedShopId,
                ),
            )
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { items.delete(id) }
    }

    /**
     * Puts a deleted item back ("Rückgängig") as it was, in one write, so it reappears in
     * its old place and no purchase is recorded again.
     */
    fun restore(item: ItemEntity) {
        viewModelScope.launch { items.restore(item.copy(categoryId = existingCategoryId(item.categoryId))) }
    }

    /** "Ausblenden" on the card that says the shop check is off. */
    fun hideCheckBanner() {
        viewModelScope.launch {
            try {
                container.settingsRepository.update { it.copy(hideCheckBanner = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The card just stays; tapping again retries.
            }
        }
    }

    fun clearDone() {
        viewModelScope.launch { items.clearDone() }
    }

    /** [id] if that category still exists (it may have been deleted meanwhile), else "Sonstiges". */
    private suspend fun existingCategoryId(id: Long?): Long? =
        id?.takeIf { categories.getById(it) != null } ?: categories.idForSlug(CategoryCatalog.FALLBACK_SLUG)
}
