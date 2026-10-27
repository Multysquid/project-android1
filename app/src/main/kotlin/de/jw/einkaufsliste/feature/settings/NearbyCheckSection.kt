package de.jw.einkaufsliste.feature.settings

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.jw.einkaufsliste.core.schedule.CheckSchedule

internal val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** A refusal faster than this came without any UI: nobody reads and answers a permission page that quickly. */
internal const val INSTANT_REFUSAL_MILLIS = 1_000L

/** Where the shops come from; said before the location is asked for. */
internal const val OSM_AREA_NOTE = "Die Läden kommen von OpenStreetMap: Dafür schickt die App den ungefähren Bereich " +
    "um deinen Standort (auf etwa 100 m gerundet) an einen OpenStreetMap-Kartenserver (Overpass)."

/** The permissions of the shop check as last read; [refresh] re-reads them and reports a change. */
@Stable
internal class CheckPermissionsState(private val context: Context, private val onChanged: () -> Unit) {
    var current: CheckPermissions by mutableStateOf(context.readCheckPermissions())
        private set

    fun refresh(): CheckPermissions {
        val now = context.readCheckPermissions()
        if (now != current) {
            current = now
            onChanged()
        }
        return now
    }
}

/** [CheckPermissionsState], re-read on every resume; [onChanged] runs whenever they differ from before. */
@Composable
internal fun rememberCheckPermissionsState(onChanged: () -> Unit): CheckPermissionsState {
    val context = LocalContext.current
    val latestOnChanged by rememberUpdatedState(onChanged)
    val state = remember(context) { CheckPermissionsState(context) { latestOnChanged() } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { state.refresh() }
    return state
}

/**
 * "Läden in der Nähe prüfen" with its permission flow: a short explanation, then notifications
 * (Android 13+), then location while in use, then - after another explanation - "Immer
 * zulassen". The check is switched on as soon as location while in use is granted.
 */
@Composable
internal fun NearbyCheckSection(
    checkEnabled: Boolean,
    batterySetupDone: Boolean,
    intervalMinutes: Int,
    permissionsState: CheckPermissionsState,
    onCheckEnabledChange: (Boolean) -> Unit,
    onOpenBatterySetup: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val permissions = permissionsState.current
    // Shown before Android's first permission page, so the user knows why it comes.
    var showExplanation by rememberSaveable { mutableStateOf(false) }
    var showBackgroundDialog by rememberSaveable { mutableStateOf(false) }
    // The location request was refused; while the check is off, a card then offers the app settings.
    var locationDenied by rememberSaveable { mutableStateOf(false) }
    // The switch's permission chain is running. Android cancels a second request at once.
    var requesting by remember { mutableStateOf(false) }
    var backgroundAskedAt by remember { mutableLongStateOf(0L) }
    var notificationsAskedAt by remember { mutableLongStateOf(0L) }

    fun refresh(): CheckPermissions = permissionsState.refresh()

    fun enable(now: CheckPermissions) {
        onCheckEnabledChange(true)
        if (!now.backgroundLocation) showBackgroundDialog = true
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val now = refresh()
        // Refused without any UI ("nicht mehr fragen", or location allowed only once): only the app settings help.
        val instant = SystemClock.elapsedRealtime() - backgroundAskedAt < INSTANT_REFUSAL_MILLIS
        if (!granted && !now.backgroundLocation && instant) {
            onMessage("Bitte in den App-Einstellungen unter „Berechtigungen → Standort“ „Immer zulassen“ wählen.")
            context.openAppDetailsSettings()
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        requesting = false
        // Empty: the request was interrupted, not answered.
        if (result.isEmpty()) return@rememberLauncherForActivityResult
        val now = refresh()
        if (now.location) {
            enable(now)
        } else {
            locationDenied = true
            // While the check is off, the card below the switch says this and links the app settings.
            if (checkEnabled) {
                onMessage("Ohne Standort kann die App keine Läden in der Nähe finden. Du kannst ihn in den App-Einstellungen erlauben.")
            }
        }
    }
    // Only upgrades approximate to precise location; unlike the switch it does not ask for background again.
    val preciseLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
    }

    fun continueWithLocation() {
        val now = refresh()
        if (now.location) {
            requesting = false
            enable(now)
        } else {
            locationLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Asked first, but a refusal does not stop the flow: the check still feeds the "In der Nähe" screen.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        continueWithLocation()
    }

    // "Benachrichtigungen erlauben" on the card: Android's question while it still asks, else the app's notification settings.
    val notificationRetryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val now = refresh()
        val instant = SystemClock.elapsedRealtime() - notificationsAskedAt < INSTANT_REFUSAL_MILLIS
        if (!granted && !now.notifications && instant) context.openAppNotificationSettings()
    }

    fun allowNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.needsNotificationPermission()) {
            notificationsAskedAt = SystemClock.elapsedRealtime()
            notificationRetryLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Granted, but switched off in Android's settings: only they can switch it on again.
            context.openAppNotificationSettings()
        }
    }

    fun startPermissionChain() {
        if (requesting) return
        requesting = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.needsNotificationPermission()) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueWithLocation()
        }
    }

    /** The switch: explains first when Android is about to ask for something. */
    fun turnOn() {
        if (requesting) return
        if (context.needsNotificationPermission() || !refresh().location) {
            showExplanation = true
        } else {
            startPermissionChain()
        }
    }

    SectionHeader("Läden in der Nähe")
    SwitchRow(
        title = "Läden in der Nähe prüfen",
        subtitle = "Die App meldet sich, wenn ein Laden in der Nähe wahrscheinlich etwas von deiner Liste hat.",
        checked = checkEnabled,
        onCheckedChange = { on -> if (on) turnOn() else onCheckEnabledChange(false) },
    )

    if (!checkEnabled && locationDenied && !permissions.location) {
        NoticeCard(
            "Ohne Standort kann die App keine Läden in der Nähe finden. " +
                "Du kannst ihn in den App-Einstellungen unter „Berechtigungen → Standort“ erlauben.",
        ) {
            NoticeAction("App-Einstellungen") { context.openAppDetailsSettings() }
        }
    }

    if (checkEnabled) {
        when {
            !permissions.location -> NoticeCard("Kein Zugriff auf den Standort. Ohne Standort kann die App nicht prüfen.") {
                NoticeAction("App-Einstellungen") { context.openAppDetailsSettings() }
                // The card already says why.
                NoticeAction("Erlauben") { startPermissionChain() }
            }
            !permissions.backgroundLocation -> NoticeCard(
                if (intervalMinutes < CheckSchedule.WORK_MANAGER_MIN_MINUTES) {
                    // The service keeps running once the app started it, but cannot start from the background.
                    "Ohne „Immer zulassen“ startet die Prüfung nach einem Neustart des Handys nicht von selbst."
                } else {
                    "Ohne „Immer zulassen“ prüft die App nur, solange sie offen ist."
                },
            ) {
                NoticeAction("App-Einstellungen") { context.openAppDetailsSettings() }
                NoticeAction("Immer zulassen") { showBackgroundDialog = true }
            }
        }
        if (permissions.location && !permissions.preciseLocation) {
            NoticeCard(
                "Nur ungefährer Standort erlaubt. Für Läden in wenigen hundert Metern ist „Genauer Standort“ besser.",
                tone = NoticeTone.INFO,
            ) {
                NoticeAction("App-Einstellungen") { context.openAppDetailsSettings() }
                NoticeAction("Genauer Standort") { preciseLauncher.launch(LOCATION_PERMISSIONS) }
            }
        }
        if (!permissions.notifications) {
            NoticeCard("Benachrichtigungen sind aus. Dann kann dich die App nicht auf Läden hinweisen.") {
                NoticeAction("Benachrichtigungen erlauben") { allowNotifications() }
            }
        }
        if (permissions.location && !batterySetupDone) {
            NoticeCard(
                "Samsung beendet Apps im Hintergrund oft. Richte den Akku ein, damit die Prüfung zuverlässig läuft.",
                tone = NoticeTone.INFO,
            ) {
                NoticeAction("Einrichten", onOpenBatterySetup)
            }
        }
    }

    if (showExplanation) {
        val askNotifications = context.needsNotificationPermission()
        val askLocation = !permissions.location
        val what = when {
            askNotifications && askLocation -> "ob sie dir Benachrichtigungen senden und deinen Standort verwenden darf"
            askNotifications -> "ob sie dir Benachrichtigungen senden darf"
            else -> "ob sie deinen Standort verwenden darf"
        }
        AlertDialog(
            onDismissRequest = { showExplanation = false },
            title = { Text("Läden in der Nähe prüfen") },
            text = {
                Text(
                    "Damit die App dich auf Läden mit Artikeln von deiner Liste hinweisen kann, fragt Android " +
                        "gleich, $what.\n\n$OSM_AREA_NOTE",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExplanation = false
                        startPermissionChain()
                    },
                ) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = { showExplanation = false }) { Text("Abbrechen") }
            },
        )
    }

    if (showBackgroundDialog) {
        val where = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "auf der nächsten Seite" else "im nächsten Fenster"
        AlertDialog(
            onDismissRequest = { showBackgroundDialog = false },
            title = { Text("Auch im Hintergrund prüfen") },
            text = { Text("Damit die App auch prüft, wenn sie geschlossen ist, wähle $where „Immer zulassen“.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackgroundDialog = false
                        backgroundAskedAt = SystemClock.elapsedRealtime()
                        backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    },
                ) { Text("Weiter") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundDialog = false }) { Text("Später") }
            },
        )
    }
}
