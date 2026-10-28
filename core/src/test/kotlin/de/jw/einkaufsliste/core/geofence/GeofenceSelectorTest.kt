package de.jw.einkaufsliste.core.geofence

import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceSelectorTest {
    private val here = GeoPoint(48.137, 11.575)

    /** A match [northMeters] north of [here]; [staleDistance] is what the match claims. */
    private fun match(id: String, northMeters: Double, staleDistance: Double = 0.0, score: Double = 1.0): ShopMatch {
        val shop = ShopInfo(
            id = id,
            name = null,
            brand = null,
            lat = here.lat + northMeters / 111_195.08,
            lon = here.lon,
            tag = "shop=supermarket",
            openingHours = null,
        )
        return ShopMatch(shop = shop, distanceMeters = staleDistance, items = emptyList(), learnedItemIds = emptySet(), score = score)
    }

    @Test
    fun emptyForNonPositiveMaxOrNoMatches() {
        val matches = listOf(match("node/1", 100.0))
        assertTrue(GeofenceSelector.select(here, matches, 0).isEmpty())
        assertTrue(GeofenceSelector.select(here, matches, -1).isEmpty())
        assertTrue(GeofenceSelector.select(here, emptyList()).isEmpty())
    }

    @Test
    fun sortsByDistanceFromHereNotByStoredDistance() {
        val matches = listOf(
            match("node/1", 900.0, staleDistance = 10.0),
            match("node/2", 100.0, staleDistance = 5_000.0),
            match("node/3", 400.0, staleDistance = 1.0),
        )
        val result = GeofenceSelector.select(here, matches)
        assertEquals(listOf("node/2", "node/3", "node/1"), result.map { it.shop.id })
    }

    @Test
    fun deduplicatesByShopIdKeepingFirst() {
        val matches = listOf(
            match("node/1", 300.0, score = 3.0),
            match("node/2", 200.0),
            match("node/1", 300.0, score = 1.0),
        )
        val result = GeofenceSelector.select(here, matches)
        assertEquals(listOf("node/2", "node/1"), result.map { it.shop.id })
        assertEquals(3.0, result[1].score, 0.0)
    }

    @Test
    fun takesAtMostMaxNearest() {
        val matches = (1..10).map { match("node/$it", it * 100.0) }.shuffled(java.util.Random(42))
        val result = GeofenceSelector.select(here, matches, 3)
        assertEquals(listOf("node/1", "node/2", "node/3"), result.map { it.shop.id })
    }

    @Test
    fun defaultMaxIsNinety() {
        val matches = (1..120).map { match("node/$it", it * 10.0) }.reversed()
        val result = GeofenceSelector.select(here, matches)
        assertEquals(GeofenceSelector.MAX_GEOFENCES, result.size)
        assertEquals(90, result.size)
        assertEquals("node/1", result.first().shop.id)
        assertEquals("node/90", result.last().shop.id)
    }

    @Test
    fun equalDistanceOrderedByShopId() {
        val matches = listOf(match("way/2", 100.0), match("node/7", 100.0))
        val result = GeofenceSelector.select(here, matches)
        assertEquals(listOf("node/7", "way/2"), result.map { it.shop.id })
    }

    @Test
    fun fewerMatchesThanMaxReturnsAll() {
        val result = GeofenceSelector.select(here, listOf(match("node/1", 50.0), match("node/2", 20.0)), 90)
        assertEquals(listOf("node/2", "node/1"), result.map { it.shop.id })
    }
}
