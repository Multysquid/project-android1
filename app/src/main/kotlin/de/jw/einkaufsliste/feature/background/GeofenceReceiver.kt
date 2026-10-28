package de.jw.einkaufsliste.feature.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import de.jw.einkaufsliste.appContainer
import de.jw.einkaufsliste.core.model.GeoPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Runs a check when the user enters the geofence of a matching shop (registered by [GeofenceManager]). */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence error ${event.errorCode}")
            // Location was switched off: the system dropped all geofences.
            if (event.errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) GeofenceManager.invalidate(context)
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val shopIds = event.triggeringGeofences.orEmpty().map { it.requestId }
        if (shopIds.isEmpty()) return
        val location = event.triggeringLocation?.let { GeoPoint(it.latitude, it.longitude) }

        val container = context.appContainer
        val pending = goAsync()
        // The check keeps running in the app scope if it takes longer; the broadcast must finish in time.
        val check = container.appScope.launch {
            container.shopCheckRunner.run(CheckTrigger.GEOFENCE, shopIds, location)
        }
        container.appScope.launch {
            try {
                withTimeoutOrNull(TIMEOUT_MS) { check.join() }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "GeofenceReceiver"
        val TIMEOUT_MS: Long = TimeUnit.SECONDS.toMillis(25)
    }
}
