package de.jw.einkaufsliste.feature.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Camera permission of a screen: whether it is [granted], whether Android was [asked] for it on
 * this screen, and [request] to ask (again).
 */
class CameraPermission(val granted: Boolean, val asked: Boolean, val request: () -> Unit)

/**
 * Tracks the camera permission. Android is asked only via [CameraPermission.request], so the
 * screen can explain first what the camera is for ([CameraPermissionMissing]).
 * Re-checks on resume, so granting it in the system settings is picked up on return.
 */
@Composable
fun rememberCameraPermission(): CameraPermission {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasCameraPermission()
    }
    return CameraPermission(granted, asked) {
        asked = true
        launcher.launch(Manifest.permission.CAMERA)
    }
}

/**
 * Shown instead of the camera while the permission is missing; [text] says what the camera is for.
 * Before Android was asked it is a short explanation with "Kamera erlauben"; after a refusal it
 * also leads to the app settings (Android stops asking after the second refusal).
 */
@Composable
fun CameraPermissionMissing(text: String, permission: CameraPermission, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val refused = permission.asked
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                if (refused) Icons.Outlined.NoPhotography else Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (refused) "Kein Zugriff auf die Kamera" else "Kamera benötigt",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = permission.request) { Text("Kamera erlauben") }
            if (refused) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.openAppSettings() }) { Text("App-Einstellungen öffnen") }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (refused) {
                    "Falls keine Abfrage erscheint: In den App-Einstellungen unter „Berechtigungen“ die Kamera erlauben."
                } else {
                    "Android fragt danach, ob die App die Kamera verwenden darf."
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Opens this app's page in the system settings (permissions live there). */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: Exception) {
        // No settings app that handles this (very unusual); nothing else to offer.
    }
}
