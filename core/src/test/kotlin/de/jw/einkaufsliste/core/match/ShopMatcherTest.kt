package de.jw.einkaufsliste.core.match

import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ItemInfo
import de.jw.einkaufsliste.core.model.PurchaseInfo
import de.jw.einkaufsliste.core.model.ShopInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopMatcherTest {
    private val here = GeoPoint(52.52, 13.405)

    /** Meters per degree of latitude for GeoMath's earth radius. */
    private val metersPerDegreeLat = 111_195.08

    private fun shop(
        id: String,
        tag: String = SUPERMARKET,
        northMeters: Double = 100.0,
        eastMeters: Double = 0.0,
        name: String? = null,
        brand: String? = null,
    ): ShopInfo {
        val lat = here.lat + northMeters / metersPerDegreeLat
        val lon = here.lon + eastMeters / (metersPerDegreeLat * Math.cos(Math.toRadians(here.lat)))
        return ShopInfo(id = id, name = name, brand = brand, lat = lat, lon = lon, tag = tag, openingHours = null)
    }

    private fun item(id: Long, name: String, vararg tags: String, pinnedShopId: String? = null) =
        ItemInfo(id = id, name = name, categorySlug = null, osmTags = tags.toList(), pinnedShopId = pinnedShopId)

    private fun match(
        shops: List<ShopInfo>,
        items: List<ItemInfo>,
        purchases: List<PurchaseInfo> = emptyList(),
        radius: Double = 500.0,
    ) = ShopMatcher.match(here, radius, shops, items, purchases)

    @Test
    fun emptyForNonPositiveRadiusOrNoItems() {
        val shops = listOf(shop("node/1"))
        val items = listOf(item(1, "Milch", SUPERMARKET))
        assertTrue(match(shops, items, radius = 0.0).isEmpty())
        assertTrue(match(shops, items, radius = -10.0).isEmpty())
        assertTrue(match(shops, items, radius = Double.NaN).isEmpty())
        assertTrue(match(shops, emptyList()).isEmpty())
        assertTrue(match(emptyList(), items).isEmpty())
    }

    @Test
    fun matchesByCategoryTag() {
        val supermarket = shop("node/1", SUPERMARKET, northMeters = 100.0)
        val bakery = shop("node/2", BAKERY, northMeters = 50.0)
        val items = listOf(item(1, "Milch", SUPERMARKET, "shop=dairy"), item(2, "Brot", BAKERY, SUPERMARKET))

        val result = match(listOf(supermarket, bakery), items)

        assertEquals(listOf("node/1", "node/2"), result.map { it.shop.id })
        assertEquals(listOf("Brot", "Milch"), result[0].items.map { it.name })
        assertEquals(listOf("Brot"), result[1].items.map { it.name })
        assertTrue(result.all { it.learnedItemIds.isEmpty() })
        assertEquals(2.0, result[0].score, 1e-9)
        assertEquals(1.0, result[1].score, 1e-9)
        assertEquals(100.0, result[0].distanceMeters, 0.5)
    }

    @Test
    fun shopsWithoutMatchingItemsAreDropped() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET), shop("node/2", "shop=shoes")),
            listOf(item(1, "Milch", SUPERMARKET)),
        )
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun radiusEdgeIsInclusive() {
        val shop = shop("node/1", northMeters = 500.0)
        val distance = GeoMath.distanceMeters(here, shop.point)
        val items = listOf(item(1, "Milch", SUPERMARKET))

        assertEquals(1, match(listOf(shop), items, radius = distance).size)
        assertTrue(match(listOf(shop), items, radius = distance - 0.01).isEmpty())
    }

    @Test
    fun shopsOutsideRadiusAreIgnored() {
        val near = shop("node/1", northMeters = 400.0)
        val far = shop("node/2", northMeters = 600.0)
        val result = match(listOf(near, far), listOf(item(1, "Milch", SUPERMARKET)))
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun matchesByPurchaseHistory() {
        val chemist = shop("node/7", CHEMIST)
        val other = shop("node/8", CHEMIST, northMeters = 200.0)
        val items = listOf(item(1, "  Spezial  Tee ", SUPERMARKET))
        val purchases = listOf(PurchaseInfo(itemName = "spezial tee", shopId = "node/7", at = 1L))

        val result = match(listOf(chemist, other), items, purchases)

        assertEquals(listOf("node/7"), result.map { it.shop.id })
        assertEquals(setOf(1L), result[0].learnedItemIds)
        assertEquals(1.5, result[0].score, 1e-9)
    }

    @Test
    fun learnedIsRecordedAlsoWhenTagMatches() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET)),
            listOf(item(1, "Milch", SUPERMARKET), item(2, "Butter", SUPERMARKET)),
            listOf(PurchaseInfo("milch", "node/1", 1L), PurchaseInfo("milch", "node/1", 2L)),
        )
        assertEquals(1, result.size)
        assertEquals(setOf(1L), result[0].learnedItemIds)
        assertEquals(2 + 0.5, result[0].score, 1e-9)
    }

    @Test
    fun purchasesOfOtherItemsDoNotMatch() {
        val result = match(
            listOf(shop("node/1", CHEMIST)),
            listOf(item(1, "Milch", SUPERMARKET)),
            listOf(PurchaseInfo("butter", "node/1", 1L)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun pinnedItemMatchesOnlyItsShop() {
        val pinnedShop = shop("node/1", BAKERY, northMeters = 300.0)
        val otherSupermarket = shop("node/2", SUPERMARKET, northMeters = 50.0)
        val items = listOf(item(1, "Milch", SUPERMARKET, pinnedShopId = "node/1"))

        val result = match(listOf(pinnedShop, otherSupermarket), items)

        assertEquals(listOf("node/1"), result.map { it.shop.id })
        assertEquals(listOf(1L), result[0].items.map { it.id })
        assertEquals(1.0 + ShopMatcher.PINNED_BONUS, result[0].score, 1e-9)
    }

    @Test
    fun pinnedItemIgnoresPurchaseHistoryAtOtherShops() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET), shop("node/2", SUPERMARKET)),
            listOf(item(1, "Milch", SUPERMARKET, pinnedShopId = "node/1")),
            listOf(PurchaseInfo("milch", "node/2", 1L)),
        )
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun pinnedShopOutsideRadiusMatchesNothing() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET, northMeters = 900.0), shop("node/2", SUPERMARKET)),
            listOf(item(1, "Milch", SUPERMARKET, pinnedShopId = "node/1")),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun itemsAreSortedByGermanNameIgnoringCase() {
        val items = listOf(
            item(1, "zucker", SUPERMARKET),
            item(2, "Brot", SUPERMARKET),
            item(3, "Äpfel", SUPERMARKET),
            item(4, "banane", SUPERMARKET),
            item(5, "Apfelsaft", SUPERMARKET),
        )
        val result = match(listOf(shop("node/1")), items)
        assertEquals(listOf("Äpfel", "Apfelsaft", "banane", "Brot", "zucker"), result.single().items.map { it.name })
    }

    @Test
    fun rankingByScoreThenDistanceThenId() {
        val shops = listOf(
            shop("node/5", SUPERMARKET, northMeters = 400.0),
            shop("node/4", BAKERY, northMeters = 100.0),
            shop("node/3", BAKERY, northMeters = 100.0),
            shop("node/2", BAKERY, northMeters = 50.0),
        )
        val items = listOf(item(1, "Brot", BAKERY, SUPERMARKET), item(2, "Milch", SUPERMARKET))

        val result = match(shops, items)

        // node/5: two items (score 2); bakeries: one item each, nearest first, equal distance by id.
        assertEquals(listOf("node/5", "node/2", "node/3", "node/4"), result.map { it.shop.id })
    }

    @Test
    fun learnedAndPinnedBonusesAffectRanking() {
        val shops = listOf(
            shop("node/1", SUPERMARKET, northMeters = 10.0),
            shop("node/2", CHEMIST, northMeters = 300.0),
            shop("node/3", "shop=kiosk", northMeters = 400.0),
        )
        val items = listOf(
            item(1, "Milch", SUPERMARKET),
            item(2, "Seife", CHEMIST),
            item(3, "Zeitung", "shop=kiosk", pinnedShopId = "node/3"),
        )
        val purchases = listOf(PurchaseInfo("seife", "node/2", 1L))

        val result = match(shops, items, purchases)

        assertEquals(listOf("node/3", "node/2", "node/1"), result.map { it.shop.id })
        assertEquals(listOf(2.0, 1.5, 1.0), result.map { it.score })
    }

    @Test
    fun nodeAndBuildingOfSameShopAreMergedKeepingBetterScore() {
        val node = shop("node/1", SUPERMARKET, northMeters = 100.0, name = "REWE")
        val way = shop("way/2", SUPERMARKET, northMeters = 140.0, name = "Rewe ")
        val items = listOf(item(1, "Milch", SUPERMARKET), item(2, "Brot", BAKERY))
        val purchases = listOf(PurchaseInfo("brot", "way/2", 1L))

        val result = match(listOf(node, way), items, purchases)

        assertEquals(listOf("way/2"), result.map { it.shop.id })
        assertEquals(2.5, result.single().score, 1e-9)
    }

    @Test
    fun duplicatesWithEqualScoreKeepNearer() {
        val node = shop("node/9", SUPERMARKET, northMeters = 130.0, name = "Edeka")
        val way = shop("way/1", SUPERMARKET, northMeters = 90.0, name = "Edeka")
        val result = match(listOf(node, way), listOf(item(1, "Milch", SUPERMARKET)))
        assertEquals(listOf("way/1"), result.map { it.shop.id })
    }

    @Test
    fun brandCountsAsNameForMerging() {
        val node = shop("node/1", CHEMIST, northMeters = 100.0, name = "dm-drogerie markt", brand = "dm")
        val way = shop("way/2", CHEMIST, northMeters = 110.0, name = null, brand = "DM-Drogerie Markt")
        val result = match(listOf(node, way), listOf(item(1, "Seife", CHEMIST)))
        assertEquals(1, result.size)
    }

    @Test
    fun differentTagOrFarApartAreNotMerged() {
        val items = listOf(item(1, "Milch", SUPERMARKET, "shop=convenience"))
        val sameNameOtherTag = match(
            listOf(
                shop("node/1", SUPERMARKET, northMeters = 100.0, name = "Netto"),
                shop("node/2", "shop=convenience", northMeters = 110.0, name = "Netto"),
            ),
            items,
        )
        assertEquals(2, sameNameOtherTag.size)

        val farApart = match(
            listOf(
                shop("node/1", SUPERMARKET, northMeters = 100.0, name = "Netto"),
                shop("node/2", SUPERMARKET, northMeters = 100.0 + ShopMatcher.DUPLICATE_DISTANCE_METERS + 5, name = "Netto"),
            ),
            items,
        )
        assertEquals(2, farApart.size)
    }

    @Test
    fun differentNamesAreNotMerged() {
        val result = match(
            listOf(
                shop("node/1", SUPERMARKET, northMeters = 100.0, name = "Aldi"),
                shop("node/2", SUPERMARKET, northMeters = 105.0, name = "Lidl"),
            ),
            listOf(item(1, "Milch", SUPERMARKET)),
        )
        assertEquals(2, result.size)
    }

    @Test
    fun shopsWithoutNameAndBrandAreNeverMerged() {
        val result = match(
            listOf(
                shop("node/1", SUPERMARKET, northMeters = 100.0),
                shop("way/2", SUPERMARKET, northMeters = 101.0),
                shop("way/3", SUPERMARKET, northMeters = 102.0, name = " ", brand = ""),
            ),
            listOf(item(1, "Milch", SUPERMARKET)),
        )
        assertEquals(listOf("node/1", "way/2", "way/3"), result.map { it.shop.id })
    }

    @Test
    fun duplicateShopIdsAreCountedOnce() {
        val shop = shop("node/1", SUPERMARKET)
        val result = match(listOf(shop, shop), listOf(item(1, "Milch", SUPERMARKET)))
        assertEquals(1, result.size)
    }

    @Test
    fun tagsAreComparedTrimmed() {
        val result = match(listOf(shop("node/1", SUPERMARKET)), listOf(item(1, "Milch", " $SUPERMARKET ")))
        assertEquals(1, result.size)
    }

    private companion object {
        const val SUPERMARKET = "shop=supermarket"
        const val BAKERY = "shop=bakery"
        const val CHEMIST = "shop=chemist"
    }
}
