package de.jw.einkaufsliste.feature.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.data.db.CategoryEntity
import kotlinx.coroutines.flow.first
import java.text.BreakIterator
import java.util.Locale

/** Most emoji are one or two user-perceived characters; more is probably a typo. */
private const val MAX_EMOJI_CHARACTERS = 2

/**
 * Create ([category] == null) or edit a category: name and emoji. [onReset] is offered
 * for default categories, [onDelete] for all but "Sonstiges"; pass null to hide them.
 */
@Composable
internal fun CategoryEditDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryDraft) -> Unit,
    onReset: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = category?.name.orEmpty()
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    var emoji by rememberSaveable { mutableStateOf(category?.emoji ?: "🛒") }
    val canSave = name.text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (category == null) "Neue Unterliste" else "Unterliste bearbeiten",
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Unterliste löschen")
                    }
                }
            }
        },
        text = {
            val focusRequester = remember { FocusRequester() }
            val windowInfo = LocalWindowInfo.current
            if (category == null) {
                LaunchedEffect(Unit) {
                    snapshotFlow { windowInfo.isWindowFocused }.first { it }
                    focusRequester.requestFocus()
                }
            }

            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { if (characterCount(it) <= MAX_EMOJI_CHARACTERS) emoji = it },
                        label = { Text("Emoji") },
                        singleLine = true,
                        modifier = Modifier.width(88.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                }

                if (onReset != null) {
                    TextButton(onClick = onReset, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                        Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Zurücksetzen")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        CategoryDraft(
                            name = name.text.trim(),
                            emoji = emoji.trim(),
                        ),
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/** User-perceived characters (grapheme clusters), so "👍🏽" or "🇩🇪" count as one. */
private fun characterCount(text: String): Int {
    val iterator = BreakIterator.getCharacterInstance(Locale.GERMAN)
    iterator.setText(text)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}
