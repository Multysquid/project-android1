package de.jw.einkaufsliste.feature.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.catalog.StoreCatalog
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.repo.AssortmentService
import de.jw.einkaufsliste.ui.common.CategoryPicker
import kotlinx.coroutines.flow.first

/**
 * Add ([item] == null) or edit dialog for an item. When adding, the name field gets
 * the focus and the category follows the typed name (see [CategorySuggestion]) until
 * the user picks one. Below, the dialog says in which shops the app will look for the
 * item ([assortmentAnswers] are Gemini's answers by name). Editing also offers deleting.
 */
@Composable
internal fun ItemEditDialog(
    item: ItemEntity?,
    categories: List<CategoryEntity>,
    existingItems: List<ItemEntity>,
    assortmentAnswers: Map<String, Set<Assortment>>,
    onDismiss: () -> Unit,
    onSave: (ItemDraft) -> Unit,
    onDelete: () -> Unit,
) {
    val isNew = item == null
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = item?.name.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    var quantity by rememberSaveable { mutableStateOf(item?.quantity.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(item?.note.orEmpty()) }
    // Null while adding until the user picks a category by hand.
    var chosenCategoryId by rememberSaveable {
        mutableStateOf(
            item?.let { it.categoryId ?: categories.firstOrNull { c -> c.slug == CategoryCatalog.FALLBACK_SLUG }?.id },
        )
    }

    val suggestedCategoryId = remember(name.text, categories, existingItems) {
        CategorySuggestion.suggest(name.text, categories, existingItems)
    }
    val categoryId = chosenCategoryId ?: suggestedCategoryId
    val canSave = name.text.isNotBlank()

    val submit: () -> Unit = {
        if (canSave) {
            onSave(
                ItemDraft(
                    name = name.text.trim(),
                    quantity = quantity.trim().ifEmpty { null },
                    note = note.trim().ifEmpty { null },
                    categoryId = categoryId,
                ),
            )
        }
    }
    val doneKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
    val doneAction = KeyboardActions(onDone = { submit() })

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "Artikel hinzufügen" else "Artikel bearbeiten", modifier = Modifier.weight(1f))
                if (!isNew) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Artikel löschen")
                    }
                }
            }
        },
        text = {
            val focusRequester = remember { FocusRequester() }
            val windowInfo = LocalWindowInfo.current
            val keyboard = LocalSoftwareKeyboardController.current
            if (isNew) {
                LaunchedEffect(Unit) {
                    // Focus once the dialog window has focus, otherwise the keyboard may not open.
                    snapshotFlow { windowInfo.isWindowFocused }.first { it }
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = doneKeyboard,
                    keyboardActions = doneAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Menge (optional)") },
                    placeholder = { Text("z. B. 2 Stück oder 500 g") },
                    singleLine = true,
                    keyboardOptions = doneKeyboard,
                    keyboardActions = doneAction,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    singleLine = true,
                    keyboardOptions = doneKeyboard,
                    keyboardActions = doneAction,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(
                    categories = categories,
                    selectedId = categoryId,
                    onSelect = { chosenCategoryId = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (name.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    val categorySlug = categories.firstOrNull { it.id == categoryId }?.slug
                    SearchedInField(
                        assortments = remember(name.text, categorySlug, assortmentAnswers) {
                            AssortmentService.resolve(name.text, categorySlug, assortmentAnswers)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = canSave) {
                Text(if (isNew) "Hinzufügen" else "Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/** "Wird automatisch gesucht": what kind of product the item is and example shops that sell it. */
@Composable
private fun SearchedInField(assortments: Set<Assortment>) {
    val text = if (assortments.isEmpty()) {
        val broad = StoreCatalog.exampleChains(emptySet(), limit = 3)
        "Die Art des Artikels ist unbekannt. Die App sucht ihn in Läden mit großem Sortiment" +
            if (broad.isEmpty()) "." else ", z. B. bei ${broad.joinToString(", ")}."
    } else {
        val kinds = assortments.joinToString(", ") { it.label }
        val chains = StoreCatalog.exampleChains(assortments)
        val examples = if (chains.isNotEmpty()) {
            "z. B. bei ${chains.joinToString(", ")}"
        } else {
            "z. B. ${StoreCatalog.shopTypeLabels(assortments).joinToString(", ")}"
        }
        "$kinds: in allen passenden Läden im Umkreis, $examples."
    }
    Column {
        Text("Wird automatisch gesucht", style = MaterialTheme.typography.labelLarge)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
