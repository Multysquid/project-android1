package de.jw.einkaufsliste.feature.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.backup.ListBackup
import java.time.LocalDate

/**
 * "Liste sichern": saves the list (items and Unterlisten) to a file the user picks, and
 * merges such a file back in. The actual work happens in [onExport] / [onImport]; a
 * cancelled picker does nothing.
 */
@Composable
internal fun BackupSection(
    running: Boolean,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onMessage: (String) -> Unit,
) {
    val latestOnExport by rememberUpdatedState(onExport)
    val latestOnImport by rememberUpdatedState(onImport)
    val exportLauncher = rememberLauncherForActivityResult(CreateBackupDocument()) { uri ->
        if (uri != null) latestOnExport(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(OpenBackupDocument()) { uri ->
        if (uri != null) latestOnImport(uri)
    }

    SectionHeader("Liste sichern")
    SectionText(
        "Speichert Artikel und Unterlisten als Datei, zum Beispiel für ein neues Handy. " +
            "Beim Importieren kommt nur dazu, was noch fehlt; nichts wird gelöscht.",
    )
    BackupRow(
        title = "Exportieren",
        subtitle = "Liste als Datei speichern",
        icon = Icons.Outlined.Save,
        enabled = !running,
        onClick = {
            try {
                exportLauncher.launch(ListBackup.fileName(LocalDate.now()))
            } catch (e: ActivityNotFoundException) {
                onMessage("Keine App zum Speichern von Dateien gefunden.")
            }
        },
    )
    BackupRow(
        title = "Importieren",
        subtitle = "Artikel aus einer Sicherung hinzufügen",
        icon = Icons.Outlined.FileOpen,
        enabled = !running,
        onClick = {
            try {
                importLauncher.launch(BACKUP_MIME_TYPES)
            } catch (e: ActivityNotFoundException) {
                onMessage("Keine App zum Öffnen von Dateien gefunden.")
            }
        },
    )
    if (running) {
        ListItem(
            headlineContent = { Text("Einen Moment …") },
            leadingContent = { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) },
        )
    }
}

@Composable
private fun BackupRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
    )
}
