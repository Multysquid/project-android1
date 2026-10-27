package de.jw.einkaufsliste.feature.background

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import de.jw.einkaufsliste.core.geofence.GeofenceSelector
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Geofences around the nearest matching shops, so that walking up to one triggers a check
 * between the periodic ones (see [GeofenceReceiver]). Needs precise and background location.
 *
 * What is registered is kept in SharedPreferences (shared by all instances, and by the short-lived
 * processes that run workers and broadcasts), so an unchanged set is not registered again: every
 * registration fires ENTER for the shops the user is already near.
 */
class GeofenceManager(private val context: Context, private val locationProvider: LocationProvider) {
    private val client: GeofencingClient by lazy { LocationServices.getGeofencingClient(context) }

    private val pendingIntent: PendingIntent by lazy {
        checkNotNull(receiverIntent(context, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    /** Geofences need precise location and "Immer zulassen" (Android 10+). */
    fun canUseGeofences(): Boolean = locationProvider.hasFinePermission() && locationProvider.hasBackgroundPermission()

    /**
     * Replaces all geofences by one circle of [radiusMeters] around each shop of [matches] (at most
     * [GeofenceSelector.MAX_GEOFENCES]). Removes them all when [matches] is empty or the permissions
     * are missing. Skipped when the same set was registered within the last hours. Never throws.
     */
    @SuppressLint("MissingPermission") // checked by canUseGeofences(); SecurityException is caught
    suspend fun replaceAll(matches: List<ShopMatch>, radiusMeters: Int) {
        val shops = matches.map { it.shop }.distinctBy { it.id }.take(GeofenceSelector.MAX_GEOFENCES)
        if (shops.isEmpty() || radiusMeters <= 0 || !canUseGeofences()) {
            removeAll()
            return
        }
        val key = keyOf(shops, radiusMeters)
        lock.withLock {
            if (key == registeredKey(context) && isFresh(context)) return
            forget(context)
            runLogging(TAG, "Could not register geofences") {
                // GEOFENCE_NOT_AVAILABLE (ApiException) when location is switched off.
                client.removeGeofences(pendingIntent).await()
                val request = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofences(shops.map { geofenceFor(it, radiusMeters) })
                    .build()
                client.addGeofences(request, pendingIntent).await()
                remember(context, key, radiusMeters)
            }
        }
    }

    /** Removes all geofences of the app. Never throws. */
    suspend fun removeAll() {
        lock.withLock {
            if (registeredKey(context) == NONE) return
            removeLocked()
        }
    }

    /**
     * Removes the geofences when they were registered with another radius than [radiusMeters]
     * (the setting changed) and returns true; the next check registers new ones. Never throws.
     */
    suspend fun removeIfRadiusChanged(radiusMeters: Int): Boolean {
        lock.withLock {
            val key = registeredKey(context)
            if (key == null || key == NONE || registeredRadius(context) == radiusMeters) return false
            removeLocked()
        }
        return true
    }

    private suspend fun removeLocked() {
        forget(context)
        runLogging(TAG, "Could not remove geofences") {
            client.removeGeofences(pendingIntent).await()
            remember(context, NONE, 0)
        }
    }

    private fun geofenceFor(shop: ShopInfo, radiusMeters: Int): Geofence =
        Geofence.Builder()
            .setRequestId(shop.id)
            .setCircularRegion(shop.lat, shop.lon, radiusMeters.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MS)
            .build()

    companion object {
        private const val TAG = "GeofenceManager"
        private const val REQUEST_CODE = 0
        private const val NOTIFICATION_RESPONSIVENESS_MS = 60_000

        /** Marks "known to have no geofences". */
        private const val NONE = ""

        /** Re-registered at least this often, in case the system dropped them without telling us. */
        private val REFRESH_AFTER_MS: Long = TimeUnit.HOURS.toMillis(3)

        private const val PREFS_NAME = "geofences"
        private const val PREF_KEY = "key"
        private const val PREF_RADIUS = "radius_meters"
        private const val PREF_REGISTERED_AT = "registered_at_elapsed_ms"

        private val lock = Mutex()

        /** True when geofences were registered within the last hours (by this or an earlier process). */
        fun hasFreshRegistration(context: Context): Boolean {
            val key = registeredKey(context)
            return key != null && key != NONE && isFresh(context)
        }

        /** Forgets what is registered, so the next check registers again (the system removed them). */
        fun invalidate(context: Context) {
            prefs(context).edit().clear().apply()
        }

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** Shop ids and radius of what is registered; null when unknown (never registered, or a failure). */
        private fun registeredKey(context: Context): String? = prefs(context).getString(PREF_KEY, null)

        private fun registeredRadius(context: Context): Int = prefs(context).getInt(PREF_RADIUS, 0)

        private fun remember(context: Context, key: String, radiusMeters: Int) {
            prefs(context).edit()
                .putString(PREF_KEY, key)
                .putInt(PREF_RADIUS, radiusMeters)
                .putLong(PREF_REGISTERED_AT, SystemClock.elapsedRealtime())
                .apply()
        }

        private fun forget(context: Context) {
            prefs(context).edit().remove(PREF_KEY).apply()
        }

        /**
         * Registered recently, and the PendingIntent the geofences deliver to still exists: a reboot
         * or a force stop cancels it, and the system drops the geofences then. Every use of it in a
         * process comes after [forget], so a new one does not count as old geofences.
         */
        private fun isFresh(context: Context): Boolean {
            val age = SystemClock.elapsedRealtime() - prefs(context).getLong(PREF_REGISTERED_AT, Long.MAX_VALUE)
            return age in 0 until REFRESH_AFTER_MS && receiverIntent(context, PendingIntent.FLAG_NO_CREATE) != null
        }

        /** With [PendingIntent.FLAG_NO_CREATE], null when it does not exist. */
        private fun receiverIntent(context: Context, flags: Int): PendingIntent? =
            // Must be mutable: Play services adds the event to the intent (required on Android 12+).
            PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, GeofenceReceiver::class.java),
                flags or PendingIntent.FLAG_MUTABLE,
            )

        private fun keyOf(shops: List<ShopInfo>, radiusMeters: Int): String =
            "$radiusMeters|" + shops.map { it.id }.sorted().joinToString(",")
    }
}
