package de.jw.einkaufsliste.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemSource
import de.jw.einkaufsliste.feature.camera.ItemDraft
import de.jw.einkaufsliste.feature.products.LookupResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    /** Camera is looking for a barcode. */
    data object Scanning : ScanState

    data class LookingUp(val barcode: String) : ScanState

    /** Lookup done; the add sheet is open with [draft]. */
    data class Ready(val barcode: String, val result: LookupResult, val draft: ItemDraft) : ScanState
}

class BarcodeScanViewModel(private val container: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = container.categoryRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off snackbar texts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var lookupJob: Job? = null

    /** A barcode from the camera or typed in; ignored while another one is being handled. */
    fun onBarcode(barcode: String) {
        if (_state.value != ScanState.Scanning) return
        _state.value = ScanState.LookingUp(barcode)
        lookupJob = viewModelScope.launch {
            val result = try {
                container.productLookup.lookup(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LookupResult.Error("Die Suche ist fehlgeschlagen. Du kannst den Namen selbst eintippen.")
            }
            val repo = container.categoryRepository
            val draft = if (result is LookupResult.Found) {
                ItemDraft(
                    name = result.product.name,
                    quantity = result.product.quantity.orEmpty(),
                    categoryId = repo.idForSlug(result.product.categorySlug),
                )
            } else {
                ItemDraft(categoryId = repo.idForSlug(null))
            }
            _state.value = ScanState.Ready(barcode, result, draft)
        }
    }

    fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
        if (_state.value is ScanState.LookingUp) _state.value = ScanState.Scanning
    }

    /** Sheet closed without adding. */
    fun dismiss() {
        if (_state.value is ScanState.Ready) _state.value = ScanState.Scanning
    }

    fun add(draft: ItemDraft) {
        val ready = _state.value as? ScanState.Ready ?: return
        val name = draft.name.trim()
        if (name.isEmpty()) return
        _state.value = ScanState.Scanning
        viewModelScope.launch {
            val saved = withContext(NonCancellable) {
                val added = runCatching {
                    container.itemRepository.add(
                        name = name,
                        categoryId = draft.categoryId,
                        quantity = draft.quantity,
                        barcode = ready.barcode,
                        source = ItemSource.BARCODE,
                    )
                }.isSuccess
                if (added) rememberUserChoice(ready, name, draft.categoryId)
                added
            }
            _messages.send(if (saved) "„$name“ hinzugefügt" else "„$name“ konnte nicht gespeichert werden.")
        }
    }

    /**
     * Remembers the name and Unterliste for this barcode when the lookup found nothing or the user
     * corrected what it found. The cache is read before Open Food Facts, so the next scan shows them
     * without asking again.
     */
    private suspend fun rememberUserChoice(ready: ScanState.Ready, name: String, categoryId: Long?) {
        val found = ready.result as? LookupResult.Found
        val corrected = found != null && (name != ready.draft.name.trim() || categoryId != ready.draft.categoryId)
        if (ready.result !is LookupResult.NotFound && !corrected) return
        val slug = categories.value.firstOrNull { it.id == categoryId }?.slug ?: CategoryCatalog.FALLBACK_SLUG
        runCatching {
            container.productLookup.remember(
                barcode = ready.barcode,
                name = name,
                categorySlug = slug,
                brand = found?.product?.brand,
                quantity = found?.product?.quantity,
            )
        }
    }
}
