package de.jw.einkaufsliste.core.geo

import de.jw.einkaufsliste.core.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in meters (haversine). */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** A lat/lon box that contains the circle around [center]; for cheap pre-filtering in SQL. */
    fun boundingBox(center: GeoPoint, radiusMeters: Double): BoundingBox {
        val dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_M)
        val cosLat = cos(Math.toRadians(center.lat)).coerceAtLeast(1e-6)
        val dLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_M * cosLat))
        return BoundingBox(
            minLat = center.lat - dLat,
            maxLat = center.lat + dLat,
            minLon = center.lon - dLon,
            maxLon = center.lon + dLon,
        )
    }

    /** "350 m", "1,2 km" or "2 km". */
    fun formatDistance(meters: Double): String {
        if (meters < 1000) return "${(meters / 10).toInt() * 10} m"
        val km = String.format(java.util.Locale.GERMAN, "%.1f", meters / 1000)
        return "${km.removeSuffix(",0")} km"
    }
}

data class BoundingBox(val minLat: Double, val maxLat: Double, val minLon: Double, val maxLon: Double)
