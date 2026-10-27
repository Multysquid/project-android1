package de.jw.einkaufsliste.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.gemini.GeminiApi

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

private fun normalizeModel(input: String): String = input.trim().ifEmpty { GeminiApi.DEFAULT_MODEL }

/**
 * "Foto-Erkennung (Gemini)": the user's own API key (stored encrypted, never shown again
 * once saved) and the model name. [storedModel] is the saved model; edits are saved when
 * the field loses focus, on "Fertig" and when the screen goes away. [modifier] goes to the
 * section as a whole (the settings scroll to it when opened from the photo screen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeminiSection(
    hasKey: Boolean,
    storedModel: String,
    keyTestRunning: Boolean,
    onSaveKey: (String) -> Unit,
    onTestKey: (enteredKey: String?, model: String) -> Unit,
    onDeleteKey: () -> Unit,
    onSaveModel: (String) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Not saveable on purpose: the key must not end up in the saved instance state.
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var modelInput by rememberSaveable { mutableStateOf(storedModel) }
    var modelFocused by remember { mutableStateOf(false) }

    fun commitModel() {
        val model = normalizeModel(modelInput)
        modelInput = model
        if (model != storedModel) onSaveModel(model)
    }

    val latestModelInput by rememberUpdatedState(modelInput)
    val latestStoredModel by rememberUpdatedState(storedModel)
    val latestOnSaveModel by rememberUpdatedState(onSaveModel)
    DisposableEffect(Unit) {
        onDispose {
            val model = normalizeModel(latestModelInput)
            if (model != latestStoredModel) latestOnSaveModel(model)
        }
    }

    Column(modifier) {
        SectionHeader("Foto-Erkennung (Gemini)")
        SectionText("Mit einem eigenen Gemini-API-Schlüssel erkennt die App Artikel auf Fotos.")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (hasKey) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                contentDescription = null,
                tint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (hasKey) "Schlüssel gespeichert" else "Kein Schlüssel", style = MaterialTheme.typography.bodyLarge)
        }

        OutlinedTextField(
            value = keyInput,
            onValueChange = { keyInput = it },
            label = { Text(if (hasKey) "Neuer API-Schlüssel" else "API-Schlüssel") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Schlüssel verbergen" else "Schlüssel anzeigen",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    onSaveKey(keyInput.trim())
                    keyInput = ""
                    keyVisible = false
                    focusManager.clearFocus()
                },
                enabled = keyInput.isNotBlank(),
            ) { Text("Speichern") }
            OutlinedButton(
                onClick = {
                    // Leaving the model field saves it; the test then runs against what is shown.
                    focusManager.clearFocus()
                    onTestKey(keyInput.trim().ifEmpty { null }, normalizeModel(modelInput))
                },
                enabled = !keyTestRunning && (hasKey || keyInput.isNotBlank()),
            ) {
                if (keyTestRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Prüfen")
            }
            TextButton(onClick = { confirmDelete = true }, enabled = hasKey) { Text("Löschen") }
        }
        TextButton(
            onClick = { if (!context.openUrl(AI_STUDIO_URL)) onMessage("Kein Browser gefunden.") },
            modifier = Modifier.padding(horizontal = 4.dp),
        ) { Text("Schlüssel bei Google AI Studio holen") }

        OutlinedTextField(
            value = modelInput,
            onValueChange = { modelInput = it },
            label = { Text("Modell") },
            supportingText = { Text("Standard: ${GeminiApi.DEFAULT_MODEL}") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .onFocusChanged { state ->
                    if (modelFocused && !state.isFocused) commitModel()
                    modelFocused = state.isFocused
                },
        )
        SectionText(
            "Nur wenn du ein Foto aufnimmst, schickt die App das Foto und die Namen deiner Unterlisten " +
                "an Google Gemini, mit deinem eigenen Schlüssel.",
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Schlüssel löschen?") },
            text = { Text("Danach kann die App keine Fotos mehr erkennen, bis du wieder einen Schlüssel einträgst.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteKey()
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}
