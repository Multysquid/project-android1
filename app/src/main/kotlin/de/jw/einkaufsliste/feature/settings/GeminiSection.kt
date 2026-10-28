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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.gemini.GeminiModels

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

/**
 * "Gemini": the user's own API key (stored encrypted, never shown again once saved), the
 * model and whether new items are classified with Gemini ([classifyItems]). [storedModel] is
 * the saved model and [models] Google's list for the stored key; the section asks for that
 * list ([onLoadModels]) whenever a key is stored, and without one offers the known models.
 * Google also lists models a key may not use, so picking a model with a key stored runs
 * "Prüfen" right away. [modifier] goes to the section as a whole (the settings scroll to it
 * when opened from the photo screen).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GeminiSection(
    hasKey: Boolean,
    storedModel: String,
    models: GeminiModelList,
    classifyItems: Boolean,
    keyTestRunning: Boolean,
    onSaveKey: (String) -> Unit,
    onTestKey: (enteredKey: String?) -> Unit,
    onDeleteKey: () -> Unit,
    onSaveModel: (String) -> Unit,
    onLoadModels: (force: Boolean) -> Unit,
    onClassifyItemsChange: (Boolean) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    // Not saveable on purpose: the key must not end up in the saved instance state.
    var keyInput by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val latestOnLoadModels by rememberUpdatedState(onLoadModels)
    LaunchedEffect(hasKey) {
        if (hasKey) latestOnLoadModels(false)
    }
    val modelChoices = remember(models.fromGoogle, storedModel) { GeminiModels.choices(models.fromGoogle, storedModel) }
    // Same test as GeminiModels.choices: an empty list from Google shows the known models.
    val googleListShown = !models.fromGoogle.isNullOrEmpty()
    val modelNote = when {
        !hasKey -> "Mit Schlüssel zeigt die App die Modelle, die Google dafür auflistet."
        models.failed && googleListShown -> "Die Liste konnte nicht neu geladen werden. Gezeigt wird die zuletzt geladene."
        models.failed -> "Die Liste von Google konnte nicht geladen werden. Gezeigt werden bekannte Modelle."
        models.fromGoogle?.isEmpty() == true -> "Google hat keine passenden Modelle gemeldet. Gezeigt werden bekannte Modelle."
        googleListShown ->
            "Die Modelle, die Google für deinen Schlüssel auflistet. Nach der Wahl prüft die App, " +
                "ob dein Schlüssel das Modell nutzen darf."
        else -> null
    }

    Column(modifier) {
        SectionHeader("Gemini")
        SectionText(
            "Mit einem eigenen Gemini-API-Schlüssel erkennt die App Artikel auf Fotos und weiß bei " +
                "neuen Artikeln genauer, in welchen Läden es sie gibt.",
        )
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
                    focusManager.clearFocus()
                    onTestKey(keyInput.trim().ifEmpty { null })
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

        GeminiModelPicker(
            choices = modelChoices,
            selected = storedModel,
            loading = models.loading,
            note = modelNote,
            onSelect = { model ->
                onSaveModel(model)
                if (hasKey) onTestKey(null)
            },
            onReload = if (hasKey) ({ onLoadModels(true) }) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        SwitchRow(
            title = "Artikel mit Gemini einordnen",
            subtitle = if (hasKey) {
                "Gemini sagt, was für ein Produkt ein neuer Artikel ist, damit die App ihn in den passenden Läden sucht."
            } else {
                "Braucht einen Schlüssel. Bis dahin ordnet die App Artikel mit ihrer eingebauten Wortliste ein."
            },
            checked = classifyItems,
            onCheckedChange = onClassifyItemsChange,
        )
        SectionText(
            "Die App schickt nur in zwei Fällen etwas an Google Gemini, mit deinem eigenen Schlüssel: " +
                "beim Foto das Foto und die Namen deiner Unterlisten, beim Einordnen den Namen des Artikels.",
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Schlüssel löschen?") },
            text = {
                Text(
                    "Danach erkennt die App keine Fotos mehr und ordnet neue Artikel nur mit ihrer Wortliste ein, " +
                        "bis du wieder einen Schlüssel einträgst.",
                )
            },
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
