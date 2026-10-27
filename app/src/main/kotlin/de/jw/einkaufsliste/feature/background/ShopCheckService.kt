package de.jw.einkaufsliste.feature.background

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.MainActivity
import de.jw.einkaufsliste.R
import de.jw.einkaufsliste.appContainer
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.notify.NotificationPolicy
import de.jw.einkaufsliste.core.schedule.CheckMode
import de.jw.einkaufsliste.core.schedule.CheckSchedule
import de.jw.einkaufsliste.data.settings.AppSettings
import de.jw.einkaufsliste.ui.Notifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service of type `location` for check intervals below 15 minutes: fused location
 * updates at the interval, a check per update, and a permanent notification. Started and
 * stopped by [CheckScheduler]; also stops itself when the settings no longer ask for it.
 */
class ShopCheckService : LifecycleService() {
    private val container: AppContainer get() = applicationContext.appContainer
    private val fused: FusedLocationProviderClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var watchingSettings = false
    private var shownIntervalMinutes = 0
    private var updatesIntervalMinutes = 0
    private var checkJob: Job? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            onLocation(GeoPoint(location.latitude, location.longitude))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val requested = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, 0) ?: 0
        if (requested > 0) shownIntervalMinutes = requested
        // startForeground first: a service started with startForegroundService must always call it.
        val inForeground = startInForeground()
        if (requested > 0) pendingStarts.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (!inForeground || !container.locationProvider.hasForegroundPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!watchingSettings) {
            watchingSettings = true
            watchSettings()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            fused.removeLocationUpdates(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Could not remove location updates", e)
        }
        super.onDestroy()
    }

    private fun startInForeground(): Boolean =
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.SERVICE_NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) when started from the background.
            Log.w(TAG, "Not allowed to start in the foreground", e)
            false
        } catch (e: SecurityException) {
            // Android 14+: location permission missing or not usable from the background.
            Log.w(TAG, "Not allowed to start in the foreground", e)
            false
        }

    /**
     * Follows check on/off, interval and quiet-hours changes and whether the list has open items;
     * stops the service when it is no longer wanted. The list is watched so that the first item
     * added to an empty list brings back the user's interval at once, not after the next idle fix.
     */
    private fun watchSettings() {
        lifecycleScope.launch {
            try {
                val settings = container.settingsRepository.settings
                    .distinctUntilChanged { old, new ->
                        old.checkEnabled == new.checkEnabled &&
                            old.intervalMinutes == new.intervalMinutes &&
                            old.quietHours == new.quietHours
                    }
                val hasOpenItems = container.itemRepository.observeOpenCount()
                    .map { it > 0 }
                    .distinctUntilChanged()
                combine(settings, hasOpenItems) { s, open -> s to open }
                    .collect { (s, open) -> onSettings(s, hasOpenItems = open) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the settings or the list", e)
                stopSelf()
            }
        }
    }

    private fun onSettings(settings: AppSettings, hasOpenItems: Boolean) {
        val wanted = settings.checkEnabled &&
            CheckSchedule.modeFor(settings.intervalMinutes) == CheckMode.FOREGROUND_SERVICE &&
            container.locationProvider.hasForegroundPermission()
        if (!wanted) {
            stopSelf()
            return
        }
        val interval = CheckSchedule.coerce(settings.intervalMinutes)
        if (interval != shownIntervalMinutes) {
            shownIntervalMinutes = interval
            updateNotification()
        }
        val updates = updatesInterval(settings, quietMinutesLeft(settings), listEmpty = !hasOpenItems)
        if (updates != updatesIntervalMinutes) requestUpdates(updates)
    }

    /** Replaces an earlier request for the same callback. */
    @SuppressLint("MissingPermission") // checked in onSettings; SecurityException is caught
    private fun requestUpdates(intervalMinutes: Int) {
        val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(intervalMillis)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { e ->
                    Log.w(TAG, "Location updates failed", e)
                    stopSelf()
                }
            updatesIntervalMinutes = intervalMinutes
        } catch (e: SecurityException) {
            Log.w(TAG, "Location permission missing", e)
            stopSelf()
        }
    }

    private fun onLocation(here: GeoPoint) {
        // Slow networks can make a check take longer than the interval; skip instead of piling up.
        if (checkJob?.isActive == true) return
        checkJob = lifecycleScope.launch {
            val settings = runLogging(TAG, "Could not read settings") { container.settingsRepository.current() }
                ?: return@launch
            val quietLeft = quietMinutesLeft(settings)
            val listEmpty = quietLeft == null && runLogging(TAG, "Could not read the list") {
                container.itemRepository.openItemsForMatching().isEmpty()
            } == true
            val interval = updatesInterval(settings, quietLeft, listEmpty)
            if (interval != updatesIntervalMinutes) requestUpdates(interval)
            if (quietLeft == null && !listEmpty) {
                withContext(Dispatchers.Default) {
                    container.shopCheckRunner.run(CheckTrigger.SERVICE, emptyList(), here)
                }
            }
        }
    }

    /**
     * Interval of the location updates. During quiet hours or with an empty list no check can
     * notify: locate only every 15 minutes until that changes (the WorkManager backup keeps checking
     * meanwhile; an item added to the list re-arms the interval via [watchSettings]). Quiet hours
     * end at a known time, so the last quiet fix is timed to come right after their end.
     */
    private fun updatesInterval(settings: AppSettings, quietMinutesLeft: Int?, listEmpty: Boolean): Int = when {
        quietMinutesLeft != null -> minOf(CheckSchedule.WORK_MANAGER_MIN_MINUTES, quietMinutesLeft)
        listEmpty -> CheckSchedule.WORK_MANAGER_MIN_MINUTES
        else -> CheckSchedule.coerce(settings.intervalMinutes)
    }

    /** Minutes until the quiet hours end (at least 1), or null when it is not quiet time now. */
    private fun quietMinutesLeft(settings: AppSettings): Int? {
        val quiet = settings.quietHours ?: return null
        val now = LocalTime.now()
        val minuteOfDay = now.hour * 60 + now.minute
        if (!NotificationPolicy.isQuietTime(minuteOfDay, quiet)) return null
        return Math.floorMod(quiet.endMinute - minuteOfDay, MINUTES_PER_DAY).coerceAtLeast(1)
    }

    @SuppressLint("MissingPermission") // checked by canPostNotifications
    private fun updateNotification() {
        if (!canPostNotifications(this)) return
        try {
            NotificationManagerCompat.from(this).notify(Notifications.SERVICE_NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.w(TAG, "Could not update the notification", e)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val interval = shownIntervalMinutes
        val text = when {
            interval == 1 -> "Jede Minute · Tippen zum Öffnen"
            interval > 1 -> "Alle $interval Minuten · Tippen zum Öffnen"
            else -> "Tippen zum Öffnen"
        }
        return NotificationCompat.Builder(this, Notifications.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Einkaufsliste prüft Läden in der Nähe")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val TAG = "ShopCheckService"
        private const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        private const val MINUTES_PER_DAY = 24 * 60

        /** Distinct from the shop notifications' request code (different extras). */
        private const val REQUEST_OPEN_APP = 101

        /**
         * Starts not yet handled by onStartCommand. Stopping the service in that window would crash
         * the app (startForeground not called yet); the service stops itself anyway once it sees the
         * settings or the missing permission.
         */
        private val pendingStarts = AtomicInteger(0)

        /** Starts (or updates) the service. Only works while the app may start a foreground service. */
        fun start(context: Context, intervalMinutes: Int): Boolean {
            val interval = intervalMinutes.coerceAtLeast(1)
            val intent = Intent(context, ShopCheckService::class.java).putExtra(EXTRA_INTERVAL_MINUTES, interval)
            pendingStarts.incrementAndGet()
            return try {
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (e: IllegalStateException) {
                // ForegroundServiceStartNotAllowedException (Android 12+) from the background.
                pendingStarts.decrementAndGet()
                Log.w(TAG, "Could not start the service", e)
                false
            } catch (e: SecurityException) {
                pendingStarts.decrementAndGet()
                Log.w(TAG, "Could not start the service", e)
                false
            }
        }

        /** Stops the service, unless a start is still on its way (then it stops itself). */
        fun stop(context: Context) {
            if (pendingStarts.get() > 0) return
            try {
                context.stopService(Intent(context, ShopCheckService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop the service", e)
            }
        }
    }
}
