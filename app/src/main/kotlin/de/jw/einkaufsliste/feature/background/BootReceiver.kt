package de.jw.einkaufsliste.feature.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.jw.einkaufsliste.appContainer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * After a reboot or an app update: re-applies the schedule (the service is gone) and enqueues one
 * check, which registers the geofences again (the system drops them on reboot).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val container = appContext.appContainer
        // Geofences are gone after a reboot and may be after an update: the boot check registers them again.
        GeofenceManager.invalidate(appContext)
        val pending = goAsync()
        container.appScope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) {
                    container.checkScheduler.apply()
                    val enabled = container.settingsRepository.current().checkEnabled
                    if (enabled && container.locationProvider.hasForegroundPermission()) {
                        // Short delay: right after boot, network and location are often not ready yet.
                        ShopCheckWorker.enqueueOnce(appContext, CheckTrigger.BOOT, delayMinutes = 1)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not reschedule after $action", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
        val TIMEOUT_MS: Long = TimeUnit.SECONDS.toMillis(8)
    }
}
