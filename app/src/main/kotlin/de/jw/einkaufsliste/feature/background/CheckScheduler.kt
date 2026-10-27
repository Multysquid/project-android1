package de.jw.einkaufsliste.feature.background

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import de.jw.einkaufsliste.core.schedule.CheckMode
import de.jw.einkaufsliste.core.schedule.CheckSchedule
import de.jw.einkaufsliste.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Turns the settings into scheduled work: WorkManager for intervals of 15 minutes and more,
 * the foreground [ShopCheckService] (plus 15-minute work as a backup) below that.
 */
class CheckScheduler(
    private val context: Context,
    private val settings: SettingsRepository,
    private val locationProvider: LocationProvider,
) {
    private val geofences by lazy { GeofenceManager(context, locationProvider) }
    private val lock = Mutex()

    /**
     * Brings scheduling in line with the current settings and permissions: cancels
     * everything when the check is off or location is not allowed; otherwise uses
     * WorkManager (>= 15 min) or the foreground service (< 15 min). Geofences of an
     * older radius setting are replaced by a check right away. Safe to call often and
     * from any context; never throws.
     *
     * Callers that are certainly in the foreground (an activity in onStart or later, a
     * screen) pass [appInForeground] = true: the process lifecycle may not have caught up yet.
     */
    suspend fun apply(appInForeground: Boolean = false) {
        runLogging(TAG, "Could not apply the check schedule") { lock.withLock { applyLocked(appInForeground) } }
    }

    /** Enqueues a single check right away (it notifies like a periodic one). Never throws. */
    fun runNow(trigger: CheckTrigger = CheckTrigger.PERIODIC) {
        ShopCheckWorker.enqueueOnce(context, trigger)
    }

    private suspend fun applyLocked(appInForeground: Boolean) {
        val current = settings.current()
        val workManager = WorkManager.getInstance(context)
        if (!current.checkEnabled || !locationProvider.hasForegroundPermission()) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            ShopCheckService.stop(context)
            geofences.removeAll()
            return
        }
        // Circles of the old radius would fire too early or too late until the next check.
        if (geofences.removeIfRadiusChanged(current.radiusMeters)) runNow()
        when (CheckSchedule.modeFor(current.intervalMinutes)) {
            CheckMode.WORK_MANAGER -> {
                enqueuePeriodic(workManager, current.intervalMinutes)
                ShopCheckService.stop(context)
            }
            CheckMode.FOREGROUND_SERVICE -> {
                // Backup in case the system kills the service.
                enqueuePeriodic(workManager, CheckSchedule.WORK_MANAGER_MIN_MINUTES)
                // Android 14 refuses a location service started in the background without "Immer zulassen".
                if (locationProvider.hasBackgroundPermission() || appInForeground || isAppInForeground()) {
                    ShopCheckService.start(context, current.intervalMinutes)
                } else {
                    Log.i(TAG, "Foreground service not started: app in background without background location")
                }
            }
        }
    }

    private fun enqueuePeriodic(workManager: WorkManager, intervalMinutes: Int) {
        val minutes = intervalMinutes.coerceAtLeast(CheckSchedule.WORK_MANAGER_MIN_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<ShopCheckWorker>(minutes, TimeUnit.MINUTES).build()
        // UPDATE keeps the current period when nothing changed, so frequent calls do not postpone checks.
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun isAppInForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    companion object {
        private const val TAG = "CheckScheduler"

        /** Unique name of the periodic WorkManager check. */
        const val PERIODIC_WORK_NAME = "shop_check"
    }
}
