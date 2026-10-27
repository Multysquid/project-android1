package de.jw.einkaufsliste.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Explanatory text in a section. */
@Composable
internal fun SectionText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** A whole-row toggle: tapping anywhere on the row flips the switch. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

internal enum class NoticeTone { WARNING, INFO }

/** A highlighted hint (missing permission, battery cost) with optional [actions] as [NoticeAction]s. */
@Composable
internal fun NoticeCard(
    text: String,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.WARNING,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (tone == NoticeTone.WARNING) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (tone == NoticeTone.WARNING) colors.onErrorContainer else colors.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = if (actions == null) 12.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (tone == NoticeTone.WARNING) Icons.Outlined.Warning else Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    content = actions,
                )
            }
        }
    }
}

@Composable
internal fun NoticeAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
        Text(text)
    }
}

/** A row that opens another screen. */
@Composable
internal fun NavigationRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** 24-hour time picker in a dialog; returns the minute of the day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    title: String,
    initialMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}
