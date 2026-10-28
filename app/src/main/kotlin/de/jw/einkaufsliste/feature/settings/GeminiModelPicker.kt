package de.jw.einkaufsliste.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.gemini.GeminiModel
import de.jw.einkaufsliste.core.gemini.GeminiModels

/**
 * "Modell": a dropdown of [choices] (see GeminiModels.choices) with the [selected] one shown,
 * plus "Anderes Modell eingeben" for a name that is not listed. [note] explains where the list
 * comes from; [onReload] (null to hide it) loads Google's list again. Picking saves at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GeminiModelPicker(
    choices: List<GeminiModel>,
    selected: String,
    loading: Boolean,
    note: String?,
    onSelect: (String) -> Unit,
    onReload: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var enterOwn by rememberSaveable { mutableStateOf(false) }
    val current = GeminiModels.find(choices, selected)

    Column(modifier) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = current.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Modell") },
                supportingText = {
                    Text(listOfNotNull(current.id.takeIf { it != current.name }, current.hint).joinToString(" · "))
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.name)
                                Text(
                                    text = listOfNotNull(model.id.takeIf { it != model.name }, model.hint).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        trailingIcon = if (model.id == current.id) {
                            { Icon(Icons.Filled.Check, contentDescription = "Ausgewählt") }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            if (model.id != current.id) onSelect(model.id)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Anderes Modell eingeben …") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        enterOwn = true
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
        if (loading || note != null || onReload != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Lade die Modellliste von Google …",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                } else {
                    Text(
                        text = note.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (onReload != null) {
                    TextButton(onClick = onReload, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Neu laden", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        }
    }

    if (enterOwn) {
        OwnModelDialog(
            initial = current.id,
            onDismiss = { enterOwn = false },
            onSave = { model ->
                enterOwn = false
                if (model != current.id) onSelect(model)
            },
        )
    }
}

/** Free text for a model name the list does not show; blank means the default model. */
@Composable
private fun OwnModelDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial) }
    val save = { onSave(GeminiModels.normalizeId(input)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anderes Modell") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Modellname") },
                supportingText = { Text("So wie Google ihn schreibt, etwa ${GeminiModels.KNOWN.first().id}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = save) { Text("Übernehmen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}
