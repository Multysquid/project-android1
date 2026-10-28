package de.jw.einkaufsliste.feature.settings

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The permissions the shop check depends on, as currently granted. */
internal data class CheckPermissions(
    /** Notifications may be posted (runtime permission on Android 13+, and not switched off). */
    val notifications: Boolean,
    /** Fine or coarse location while the app is in use. */
    val location: Boolean,
    /** Fine ("Genauer Standort") rather than only approximate location. */
    val preciseLocation: Boolean,
    /** "Immer zulassen". */
    val backgroundLocation: Boolean,
)

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun Context.readCheckPermissions(): CheckPermissions {
    val fine = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    return CheckPermissions(
        notifications = !needsNotificationPermission() && NotificationManagerCompat.from(this).areNotificationsEnabled(),
        location = fine || coarse,
        preciseLocation = fine,
        backgroundLocation = hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
    )
}

/** True on Android 13+ while POST_NOTIFICATIONS has not been granted. */
internal fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)

/** Starts [intent]; false when no app handles it or the target refuses (e.g. a non-exported OEM activity). */
internal fun Context.tryStartActivity(intent: Intent): Boolean {
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: RuntimeException) {
        // ActivityNotFoundException, SecurityException, or OEM oddities.
        false
    }
}

/** This app's page in the system settings (permissions, battery, notifications). */
internal fun Context.openAppDetailsSettings(): Boolean =
    tryStartActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))

/** This app's notification settings; falls back to the app details page. */
internal fun Context.openAppNotificationSettings(): Boolean =
    tryStartActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) ||
        openAppDetailsSettings()

internal fun Context.openUrl(url: String): Boolean = tryStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

internal fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) ?: false

/** Asks the system to exempt this app from battery optimization; falls back to the settings lists. */
@SuppressLint("BatteryLife")
internal fun Context.requestIgnoreBatteryOptimizations(): Boolean =
    tryStartActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) ||
        tryStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
        openAppDetailsSettings()

/** Samsung's battery page (Device Care, two known class names), else the generic battery settings. */
internal fun Context.openSamsungBatterySettings(): Boolean =
    SAMSUNG_BATTERY_ACTIVITIES.any { tryStartActivity(Intent().setComponent(it)) } ||
        tryStartActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) ||
        openAppDetailsSettings()

/** versionName from the package manager (BuildConfig is not generated). */
internal fun Context.appVersionName(): String? = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    info.versionName
} catch (e: PackageManager.NameNotFoundException) {
    null
}

private val SAMSUNG_BATTERY_ACTIVITIES = listOf(
    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
    ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
)
