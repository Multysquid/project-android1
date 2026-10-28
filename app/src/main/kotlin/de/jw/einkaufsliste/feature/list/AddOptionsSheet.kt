package de.jw.einkaufsliste.feature.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Bottom sheet behind the + button: type, photo or barcode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddOptionsSheet(
    onDismiss: () -> Unit,
    onType: () -> Unit,
    onPhoto: () -> Unit,
    onBarcode: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Staying on the list: let the sheet slide away first, then act.
    val stay: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }
    // Leaving the screen: close at once, so the list's saved state has the sheet closed.
    val leave: (() -> Unit) -> Unit = { action ->
        onDismiss()
        action()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "Hinzufügen",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        AddOption(Icons.Outlined.Edit, "Eintippen", "Namen selbst eingeben") { stay(onType) }
        AddOption(Icons.Outlined.PhotoCamera, "Foto", "Artikel fotografieren und erkennen lassen") { leave(onPhoto) }
        AddOption(Icons.Outlined.QrCodeScanner, "Barcode scannen", "Produkt über den Barcode finden") {
            leave(onBarcode)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AddOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}
