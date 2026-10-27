package de.jw.einkaufsliste.core.geo

import de.jw.einkaufsliste.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    private val berlin = GeoPoint(52.5200, 13.4050)
    private val hamburg = GeoPoint(53.5511, 9.9937)

    @Test
    fun berlinToHamburgIsAbout255Km() {
        val d = GeoMath.distanceMeters(berlin, hamburg)
        assertEquals(255_000.0, d, 255_000.0 * 0.01)
    }

    @Test
    fun distanceIsSymmetricAndZeroForSamePoint() {
        assertEquals(0.0, GeoMath.distanceMeters(berlin, berlin), 1e-9)
        assertEquals(GeoMath.distanceMeters(berlin, hamburg), GeoMath.distanceMeters(hamburg, berlin), 1e-6)
    }

    @Test
    fun oneDegreeOfLatitudeIsAbout111Km() {
        val d = GeoMath.distanceMeters(GeoPoint(50.0, 8.0), GeoPoint(51.0, 8.0))
        assertEquals(111_195.0, d, 5.0)
    }

    @Test
    fun shortDistancesAreAccurate() {
        val a = GeoPoint(48.137, 11.575)
        val b = GeoPoint(48.137 + 100.0 / 111_195.08, 11.575)
        assertEquals(100.0, GeoMath.distanceMeters(a, b), 0.01)
    }

    @Test
    fun antipodesDoNotProduceNaN() {
        val d = GeoMath.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0))
        assertEquals(Math.PI * 6_371_008.8, d, 1.0)
    }

    @Test
    fun boundingBoxContainsTheCircle() {
        val center = GeoPoint(52.52, 13.405)
        val radius = 3_000.0
        val box = GeoMath.boundingBox(center, radius)
        assertTrue(box.minLat < center.lat && center.lat < box.maxLat)
        assertTrue(box.minLon < center.lon && center.lon < box.maxLon)
        // The box edges are (at least) radius away from the center.
        assertEquals(radius, GeoMath.distanceMeters(center, GeoPoint(box.maxLat, center.lon)), 1.0)
        assertEquals(radius, GeoMath.distanceMeters(center, GeoPoint(box.minLat, center.lon)), 1.0)
        assertTrue(GeoMath.distanceMeters(center, GeoPoint(center.lat, box.maxLon)) >= radius - 1.0)
        assertTrue(GeoMath.distanceMeters(center, GeoPoint(center.lat, box.minLon)) >= radius - 1.0)
    }

    @Test
    fun formatDistanceBelowOneKilometer() {
        assertEquals("0 m", GeoMath.formatDistance(0.0))
        assertEquals("0 m", GeoMath.formatDistance(9.9))
        assertEquals("350 m", GeoMath.formatDistance(350.0))
        assertEquals("350 m", GeoMath.formatDistance(359.9))
        assertEquals("990 m", GeoMath.formatDistance(999.9))
    }

    @Test
    fun formatDistanceInKilometersUsesGermanDecimalComma() {
        assertEquals("1 km", GeoMath.formatDistance(1_000.0))
        assertEquals("1,2 km", GeoMath.formatDistance(1_234.0))
        assertEquals("12,5 km", GeoMath.formatDistance(12_499.0))
        assertEquals("255,3 km", GeoMath.formatDistance(GeoMath.distanceMeters(berlin, hamburg)))
    }
}
