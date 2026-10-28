package de.jw.einkaufsliste.feature.background

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import de.jw.einkaufsliste.core.model.GeoPoint
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Location permissions and one-shot fixes from the fused location provider. */
class LocationProvider(private val context: Context) {
    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /** Precise or approximate location while the app is in use. */
    fun hasForegroundPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Precise location ("Genauer Standort"); geofences need it. */
    fun hasFinePermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /** ACCESS_BACKGROUND_LOCATION ("Immer zulassen"). */
    fun hasBackgroundPermission(): Boolean =
        hasForegroundPermission() && isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    /** A current fix, or a recent last-known one; null without permission or when none is available in time. */
    suspend fun currentLocation(): GeoPoint? = currentFix()?.let { GeoPoint(it.latitude, it.longitude) }

    /**
     * Like [currentLocation], but returns the [Location] with its accuracy. [precise] asks for
     * GPS-level accuracy and accepts only younger cached fixes (to tell which shop the user is in).
     * Never throws except for cancellation.
     */
    suspend fun currentFix(precise: Boolean = false): Location? {
        if (!hasForegroundPermission()) return null
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val maxAge = if (precise) PRECISE_MAX_AGE_MS else CURRENT_MAX_AGE_MS
        val lastMaxAge = if (precise) PRECISE_MAX_AGE_MS else LAST_KNOWN_MAX_AGE_MS
        runLogging(TAG, "Current location failed") { freshFix(priority, maxAge) }?.let { return it }
        return runLogging(TAG, "Last known location failed") { lastKnownFix(lastMaxAge) }
    }

    @SuppressLint("MissingPermission") // checked in currentFix; SecurityException is caught there
    private suspend fun freshFix(priority: Int, maxAgeMillis: Long): Location? {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priority)
            .setMaxUpdateAgeMillis(maxAgeMillis)
            .setDurationMillis(FIX_DURATION_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(FIX_DURATION_MS + TIMEOUT_SLACK_MS) {
                client.getCurrentLocation(request, cancellation.token).await()
            }
        } finally {
            cancellation.cancel()
        }
    }

    @SuppressLint("MissingPermission") // checked in currentFix; SecurityException is caught there
    private suspend fun lastKnownFix(maxAgeMillis: Long): Location? {
        val last: Location = client.lastLocation.await() ?: return null
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - last.elapsedRealtimeNanos) / NANOS_PER_MILLI
        return last.takeIf { ageMillis in 0..maxAgeMillis }
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "LocationProvider"
        const val NANOS_PER_MILLI = 1_000_000L
        val FIX_DURATION_MS: Long = TimeUnit.SECONDS.toMillis(20)
        val TIMEOUT_SLACK_MS: Long = TimeUnit.SECONDS.toMillis(5)
        val CURRENT_MAX_AGE_MS: Long = TimeUnit.MINUTES.toMillis(2)
        val LAST_KNOWN_MAX_AGE_MS: Long = TimeUnit.MINUTES.toMillis(10)
        val PRECISE_MAX_AGE_MS: Long = TimeUnit.SECONDS.toMillis(30)
    }
}
