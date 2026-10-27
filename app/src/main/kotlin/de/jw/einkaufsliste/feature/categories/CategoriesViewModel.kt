package de.jw.einkaufsliste.feature.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.toOsmTagsColumn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

/** Categories in list order, and how many items each holds. Null until loaded. */
data class CategoriesUiState(
    val categories: List<CategoryEntity>,
    val itemCounts: Map<Long, Int>,
)

/** The fields of the category dialog; [name] and [emoji] are trimmed. */
data class CategoryDraft(
    val name: String,
    val emoji: String,
    val osmTags: List<String>,
)

class CategoriesViewModel(container: AppContainer) : ViewModel() {
    private val repository = container.categoryRepository
    private val reorderLock = Mutex()

    val state: StateFlow<CategoriesUiState?> =
        combine(repository.observeAll(), container.itemRepository.observeSections()) { categories, sections ->
            val counts = sections.mapNotNull { section -> section.category?.let { it.id to section.items.size } }.toMap()
            CategoriesUiState(categories, counts)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off German messages for the snackbar. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Moves a category one place up ([delta] = -1) or down (+1). Reads the stored order, so fast taps add up. */
    fun move(categoryId: Long, delta: Int) {
        viewModelScope.launch {
            reorderLock.withLock {
                val ordered = repository.getAll().toMutableList()
                val from = ordered.indexOfFirst { it.id == categoryId }
                val to = from + delta
                if (from < 0 || to !in ordered.indices) return@withLock
                Collections.swap(ordered, from, to)
                repository.reorder(ordered)
            }
        }
    }

    /** Creates a category when [categoryId] is null, otherwise updates it. */
    fun save(categoryId: Long?, draft: CategoryDraft) {
        viewModelScope.launch {
            if (categoryId == null) {
                repository.add(draft.name, draft.emoji, draft.osmTags)
            } else {
                val current = repository.getById(categoryId) ?: return@launch
                repository.update(
                    current.copy(
                        name = draft.name,
                        emoji = draft.emoji.ifEmpty { current.emoji },
                        osmTags = draft.osmTags.toOsmTagsColumn(),
                    ),
                )
            }
        }
    }

    fun resetToDefault(categoryId: Long) {
        viewModelScope.launch {
            repository.getById(categoryId)?.let { repository.resetToDefault(it) }
        }
    }

    /** "Standard-Unterlisten wiederherstellen": adds the deleted default categories again. */
    fun restoreDefaults() {
        viewModelScope.launch {
            reorderLock.withLock {
                val message = try {
                    when (val restored = repository.restoreMissingDefaults()) {
                        0 -> "Alle Standard-Unterlisten sind schon da."
                        1 -> "1 Unterliste wiederhergestellt"
                        else -> "$restored Unterlisten wiederhergestellt"
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "Die Unterlisten konnten nicht wiederhergestellt werden."
                }
                _messages.send(message)
            }
        }
    }

    fun delete(categoryId: Long) {
        viewModelScope.launch {
            repository.getById(categoryId)?.let { repository.delete(it) }
        }
    }
}
