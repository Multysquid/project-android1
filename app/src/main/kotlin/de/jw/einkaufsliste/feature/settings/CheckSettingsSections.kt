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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.schedule.CheckSchedule

@Composable
internal fun RadiusSection(radiusMeters: Int, onRadiusChange: (Int) -> Unit) {
    val options = SettingsOptions.RADIUS_METERS
    // Local while dragging; reset whenever the stored value changes.
    var position by remember(radiusMeters) {
        mutableFloatStateOf(SettingsOptions.nearestIndex(options, radiusMeters).toFloat())
    }
    val label = SettingsOptions.radiusLabel(SettingsOptions.optionAt(options, position))

    SectionHeader("Umkreis")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Läden in diesem Umkreis um deinen Standort",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }
    Slider(
        value = position,
        onValueChange = { position = it },
        valueRange = 0f..options.lastIndex.toFloat(),
        steps = options.size - 2,
        onValueChangeFinished = {
            val chosen = SettingsOptions.optionAt(options, position)
            if (chosen != radiusMeters) onRadiusChange(chosen)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .semantics { stateDescription = label },
    )
    SectionText(OSM_AREA_NOTE)
}

/**
 * The check interval. [backgroundLocation] ("Immer zulassen") decides whether the service of
 * the short intervals can start without the app being opened (e.g. after a restart).
 */
@Composable
internal fun IntervalSection(intervalMinutes: Int, backgroundLocation: Boolean, onIntervalChange: (Int) -> Unit) {
    SectionHeader("Prüfintervall")
    Column(Modifier.selectableGroup()) {
        CheckSchedule.INTERVAL_OPTIONS.forEach { minutes ->
            val selected = minutes == intervalMinutes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onIntervalChange(minutes) })
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Spacer(Modifier.width(16.dp))
                Text(SettingsOptions.intervalLabel(minutes), style = MaterialTheme.typography.bodyLarge)
                if (minutes == CheckSchedule.DEFAULT_INTERVAL_MINUTES) {
                    Text(
                        " (Standard)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (intervalMinutes < CheckSchedule.WORK_MANAGER_MIN_MINUTES) {
        val notice = "Unter 15 Minuten zeigt die App eine dauerhafte Benachrichtigung und braucht spürbar mehr Akku."
        NoticeCard(
            if (backgroundLocation) {
                notice
            } else {
                // CheckScheduler starts the service from the background (boot, update) only with "Immer zulassen".
                "$notice Ohne „Immer zulassen“ startet diese Prüfung erst, wenn du die App öffnest – " +
                    "auch nach einem Neustart des Handys."
            },
        )
    }
    SectionText("Zusätzlich meldet sich die App, sobald du in die Nähe eines passenden Ladens kommst.")
}

private enum class QuietEdge { START, END }

@Composable
internal fun QuietHoursSection(
    enabled: Boolean,
    startMinute: Int,
    endMinute: Int,
    onEnabledChange: (Boolean) -> Unit,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<QuietEdge?>(null) }

    SectionHeader("Ruhezeiten")
    SwitchRow(
        title = "Ruhezeiten",
        subtitle = "In dieser Zeit meldet sich die App nicht.",
        checked = enabled,
        onCheckedChange = onEnabledChange,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Von", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { editing = QuietEdge.START }, enabled = enabled) {
            Text(SettingsOptions.formatTime(startMinute))
        }
        Text("bis", style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { editing = QuietEdge.END }, enabled = enabled) {
            Text(SettingsOptions.formatTime(endMinute))
        }
    }

    when (editing) {
        QuietEdge.START -> TimePickerDialog(
            title = "Ruhezeit beginnt um",
            initialMinuteOfDay = startMinute,
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onStartChange(it)
            },
        )
        QuietEdge.END -> TimePickerDialog(
            title = "Ruhezeit endet um",
            initialMinuteOfDay = endMinute,
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                onEndChange(it)
            },
        )
        null -> Unit
    }
}

/** Opening hours and per-shop cooldown. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationRulesSection(
    respectOpeningHours: Boolean,
    cooldownHours: Int,
    onRespectOpeningHoursChange: (Boolean) -> Unit,
    onCooldownChange: (Int) -> Unit,
) {
    val options = SettingsOptions.COOLDOWN_HOURS
    val selected = options[SettingsOptions.nearestIndex(options, cooldownHours)]

    SectionHeader("Hinweise")
    SwitchRow(
        title = "Öffnungszeiten beachten",
        subtitle = "Keine Hinweise auf Läden, die gerade geschlossen sind (wenn die Öffnungszeiten bekannt sind).",
        checked = respectOpeningHours,
        onCheckedChange = onRespectOpeningHoursChange,
    )
    Text(
        SettingsOptions.cooldownLabel(selected),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { hours ->
            FilterChip(
                selected = hours == selected,
                onClick = { onCooldownChange(hours) },
                label = { Text("$hours Std.") },
            )
        }
    }
}

/** "Jetzt prüfen" with a spinner while the check runs; the result arrives as a snackbar. */
@Composable
internal fun CheckNowRow(running: Boolean, onCheckNow: () -> Unit) {
    SectionText(
        "Prüft sofort, ob ein Laden in der Nähe etwas von deiner Liste hat.",
        modifier = Modifier.padding(top = 12.dp),
    )
    FilledTonalButton(
        onClick = onCheckNow,
        enabled = !running,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        if (running) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Prüfe …")
        } else {
            Text("Jetzt prüfen")
        }
    }
}
