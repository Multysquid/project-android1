package de.jw.einkaufsliste.feature.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.data.db.ShopEntity
import de.jw.einkaufsliste.feature.camera.openAppSettings
import java.text.Collator
import java.util.Locale

private data class ShopRow(
    val shop: ShopEntity,
    val title: String,
    val type: String,
    val distanceMeters: Double?,
)

/**
 * Picks one of the cached shops for "Nur in diesem Laden". Searchable by name,
 * brand and shop type; sorted by name, then distance when [here] is known.
 * Without cached shops it offers [onLoadShops] ("Läden laden"), whose progress is [loadState].
 */
@Composable
internal fun ShopPickerDialog(
    shops: Collection<ShopEntity>,
    selectedId: String?,
    here: GeoPoint?,
    loadState: ShopLoadState,
    onLoadShops: () -> Unit,
    onSelect: (ShopEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val collator = remember { Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY } }
    val allRows = remember(shops, here) {
        shops
            .map { shop ->
                ShopRow(
                    shop = shop,
                    title = shop.displayName(),
                    type = shop.typeLabel(),
                    distanceMeters = here?.let { GeoMath.distanceMeters(it, GeoPoint(shop.lat, shop.lon)) },
                )
            }
            .sortedWith(
                compareBy<ShopRow, String>(collator) { it.title }
                    .thenBy { it.distanceMeters ?: Double.MAX_VALUE },
            )
    }
    val rows = remember(allRows, query) {
        val needle = query.trim().lowercase(Locale.GERMAN)
        if (needle.isEmpty()) {
            allRows
        } else {
            allRows.filter { row ->
                row.title.lowercase(Locale.GERMAN).contains(needle) ||
                    row.type.lowercase(Locale.GERMAN).contains(needle) ||
                    row.shop.brand?.lowercase(Locale.GERMAN)?.contains(needle) == true
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nur in diesem Laden") },
        text = {
            Column {
                if (shops.isEmpty()) {
                    NoShopsYet(loadState, onLoadShops)
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Name oder Art, z. B. Bäckerei") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (rows.isEmpty()) {
                        Text("Kein Laden gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(rows, key = { it.shop.id }) { row ->
                                ShopRowItem(row, selected = row.shop.id == selectedId) { onSelect(row.shop) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/** Explains why the location is needed, then "Läden laden" (with progress and the last error). */
@Composable
private fun NoShopsYet(loadState: ShopLoadState, onLoadShops: () -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Noch keine Läden gespeichert. Die App kann jetzt die Läden in deiner Umgebung von " +
                "OpenStreetMap laden; dafür braucht sie einmal deinen Standort.",
        )
        if (loadState.loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Läden werden geladen …")
            }
        } else {
            loadState.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = onLoadShops, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (loadState.message == null) "Läden laden" else "Erneut versuchen")
            }
            if (loadState.permissionMissing) {
                // Android stops asking after the permission was refused twice.
                TextButton(onClick = { context.openAppSettings() }) { Text("App-Einstellungen öffnen") }
            }
        }
    }
}

@Composable
private fun ShopRowItem(row: ShopRow, selected: Boolean, onClick: () -> Unit) {
    val details = listOfNotNull(
        row.type.takeIf { it != row.title },
        row.distanceMeters?.let { GeoMath.formatDistance(it) },
    ).joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        trailingContent = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = "Ausgewählt", tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}
