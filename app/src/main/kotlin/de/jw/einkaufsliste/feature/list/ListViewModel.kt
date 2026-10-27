package de.jw.einkaufsliste.feature.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.repo.ListSection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    /** Gemini's answers by normalized item name (see AssortmentService), for "Wird gesucht in". */
    val assortmentAnswers: Map<String, Set<Assortment>> = emptyMap(),
) {
    val allItems: List<ItemEntity> = sections.flatMap { it.items }
    val openCount: Int get() = sections.sumOf { it.openCount }
    val doneCount: Int get() = allItems.size - openCount
    val isEmpty: Boolean get() = allItems.isEmpty()
}

/** The fields of the add/edit dialog, already trimmed; blank optional fields are null. */
data class ItemDraft(
    val name: String,
    val quantity: String?,
    val note: String?,
    val categoryId: Long?,
)

class ListViewModel(private val container: AppContainer) : ViewModel() {
    private val items = container.itemRepository
    private val categories = container.categoryRepository

    // Starts empty so the list does not wait for the answers.
    private val assortmentAnswers: Flow<Map<String, Set<Assortment>>> =
        flow { emitAll(container.assortmentService.observeCache()) }
            .catch { emit(emptyMap()) }
            .onStart { emit(emptyMap()) }

    val state: StateFlow<ListUiState> =
        combine(
            items.observeSections(),
            categories.observeAll(),
            container.settingsRepository.settings.map { !it.checkEnabled && !it.hideCheckBanner }.distinctUntilChanged(),
            assortmentAnswers,
        ) { sections, categories, showCheckBanner, answers ->
            ListUiState(
                loaded = true,
                sections = sections,
                categories = categories,
                showCheckBanner = showCheckBanner,
                assortmentAnswers = answers,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

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
