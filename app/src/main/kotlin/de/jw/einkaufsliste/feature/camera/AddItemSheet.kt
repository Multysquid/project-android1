package de.jw.einkaufsliste.feature.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.catalog.CategoryGuesser
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.ui.common.CategoryPicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Values the add sheet starts with, and what it hands back. [categoryId] null means "Sonstiges". */
data class ItemDraft(
    val name: String = "",
    val quantity: String = "",
    val categoryId: Long? = null,
)

/**
 * Bottom sheet to check a scanned or photographed item before it goes on the list:
 * name, Menge and Unterliste, then "Hinzufügen" or "Abbrechen".
 *
 * [message] is shown above the fields (e.g. why nothing was found), [info] under the
 * name (e.g. where it came from). With [guessCategory] the Unterliste follows the typed
 * name (CategoryGuesser) until the user picks one. [onAdd] gets a draft with a trimmed,
 * non-empty name once the sheet is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemSheet(
    title: String,
    categories: List<CategoryEntity>,
    initial: ItemDraft,
    onAdd: (ItemDraft) -> Unit,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    message: String? = null,
    info: String? = null,
    guessCategory: Boolean = false,
    photo: ImageBitmap? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(initial) { mutableStateOf(initial.name) }
    var quantity by rememberSaveable(initial) { mutableStateOf(initial.quantity) }
    var pickedId by rememberSaveable(initial) { mutableStateOf(initial.categoryId) }
    var userPicked by rememberSaveable(initial) { mutableStateOf(false) }

    val guessedId = if (guessCategory && !userPicked) guessCategoryId(name, categories) else null
    val fallbackId = categories.firstOrNull { it.slug == CategoryCatalog.FALLBACK_SLUG }?.id
    val categoryId = guessedId ?: pickedId ?: fallbackId

    // Set once "Hinzufügen" or "Abbrechen" was tapped, so the hide animation cannot also count as a dismiss.
    var closing by remember { mutableStateOf(false) }

    fun closeThen(action: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val draft = ItemDraft(name = trimmed, quantity = quantity.trim(), categoryId = categoryId)
        closeThen { onAdd(draft) }
    }

    ModalBottomSheet(onDismissRequest = { if (!closing) onDismiss() }, sheetState = sheetState) {
        val focusRequester = remember { FocusRequester() }
        val windowInfo = LocalWindowInfo.current
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            if (initial.name.isEmpty()) {
                // Focus once the sheet's window has focus, otherwise the keyboard may not open.
                snapshotFlow { windowInfo.isWindowFocused }.first { it }
                runCatching { focusRequester.requestFocus() }
                keyboard?.show()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = "Foto",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                supportingText = if (info != null) {
                    { Text(info) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryPicker(
                categories = categories,
                selectedId = categoryId,
                onSelect = {
                    pickedId = it
                    userPicked = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { closeThen(onDismiss) }) { Text("Abbrechen") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { submit() }, enabled = name.isNotBlank()) { Text("Hinzufügen") }
            }
        }
    }
}

/** Id of the category CategoryGuesser suggests for [name], if that category exists. */
private fun guessCategoryId(name: String, categories: List<CategoryEntity>): Long? {
    if (name.isBlank()) return null
    val slug = runCatching { CategoryGuesser.guess(name) }.getOrNull() ?: return null
    return categories.firstOrNull { it.slug == slug }?.id
}
