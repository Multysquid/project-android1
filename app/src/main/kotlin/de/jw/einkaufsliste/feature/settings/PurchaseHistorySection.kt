package de.jw.einkaufsliste.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.jw.einkaufsliste.data.db.PurchaseEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One shop of the "Gelernte Einkäufe" dialog: what was ticked off there, newest first. */
internal data class LearnedShop(
    val shopId: String,
    val title: String,
    /** Distinct item names, newest first, with a capital first letter. */
    val items: List<String>,
    val lastBoughtAt: Long,
) {
    companion object {
        /**
         * Groups [purchases] by shop, the shop bought at most recently first. [titles] maps shop
         * ids to names; shops no longer in the cache get [missingTitle].
         */
        fun group(purchases: List<PurchaseEntity>, titles: Map<String, String>, missingTitle: String): List<LearnedShop> =
            purchases.groupBy { it.shopId }
                .map { (shopId, bought) ->
                    val newestFirst = bought.sortedByDescending { it.at }
                    LearnedShop(
                        shopId = shopId,
                        title = titles[shopId] ?: missingTitle,
                        items = newestFirst.map { displayItemName(it.itemName) }.distinct(),
                        lastBoughtAt = newestFirst.first().at,
                    )
                }
                .sortedByDescending { it.lastBoughtAt }

        /** Purchases store the normalized (lower-case) name; "milch" is shown as "Milch". */
        private fun displayItemName(normalized: String): String =
            normalized.replaceFirstChar { it.titlecase(Locale.GERMAN) }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)

private fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

/**
 * "Gelernte Einkäufe": the purchase history (item ticked off next to a shop) that ranks shops
 * for an item. It can be looked at and deleted; [count] is null until counted.
 */
@Composable
internal fun PurchaseHistorySection(
    count: Int?,
    history: List<LearnedShop>?,
    onRefreshCount: () -> Unit,
    onLoadHistory: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Items may have been ticked off in a shop since the screen was last shown.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefreshCount() }

    SectionHeader("Gelernte Einkäufe")
    SectionText(
        "Hakst du einen Artikel direkt in einem Laden ab, merkt sich die App den Laden und weist dich " +
            "dort künftig auch auf diesen Artikel hin. Die Einträge bleiben nur auf dem Handy und " +
            "werden nach 2 Jahren gelöscht.",
    )
    HistoryRow(
        title = "Gelernte Einkäufe ansehen",
        subtitle = when (count) {
            null -> "Wird gezählt …"
            0 -> "Noch nichts gelernt"
            1 -> "1 Eintrag"
            else -> "$count Einträge"
        },
        icon = Icons.Outlined.History,
        enabled = count != null && count > 0,
        onClick = { showHistory = true },
    )
    HistoryRow(
        title = "Gelernte Einkäufe löschen",
        subtitle = "Deine Liste bleibt, wie sie ist",
        icon = Icons.Outlined.DeleteOutline,
        enabled = count != null && count > 0,
        onClick = { confirmDelete = true },
    )

    if (showHistory) {
        // Also when the dialog comes back after the activity was recreated.
        LaunchedEffect(Unit) { onLoadHistory() }
        PurchaseHistoryDialog(
            history = history,
            onDismiss = { showHistory = false },
            onDeleteAll = { confirmDelete = true },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Gelernte Einkäufe löschen?") },
            text = {
                Text(
                    "Die App vergisst, in welchen Läden du welche Artikel abgehakt hast. " +
                        "Deine Liste und deine Unterlisten bleiben, wie sie sind.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        showHistory = false
                        onDeleteAll()
                    },
                ) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun PurchaseHistoryDialog(history: List<LearnedShop>?, onDismiss: () -> Unit, onDeleteAll: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gelernte Einkäufe") },
        text = {
            when {
                history == null -> Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                history.isEmpty() -> Text("Die App hat noch nichts gelernt.")
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(history, key = { it.shopId }) { shop ->
                        Column {
                            Text(shop.title, style = MaterialTheme.typography.titleSmall)
                            Text(shop.items.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Zuletzt am ${formatDate(shop.lastBoughtAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
        dismissButton = {
            if (!history.isNullOrEmpty()) {
                TextButton(onClick = onDeleteAll) { Text("Alle löschen") }
            }
        },
    )
}

@Composable
private fun HistoryRow(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
    )
}
