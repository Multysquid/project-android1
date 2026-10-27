package de.jw.einkaufsliste.core.match

import de.jw.einkaufsliste.core.catalog.Assortment
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

    private fun item(id: Long, name: String, vararg assortments: Assortment) =
        ItemInfo(id = id, name = name, categorySlug = null, assortments = assortments.toSet())

    private fun match(
        shops: List<ShopInfo>,
        items: List<ItemInfo>,
        purchases: List<PurchaseInfo> = emptyList(),
        radius: Double = 500.0,
    ) = ShopMatcher.match(here, radius, shops, items, purchases)

    @Test
    fun emptyForNonPositiveRadiusOrNoItems() {
        val shops = listOf(shop("node/1"))
        val items = listOf(item(1, "Milch", MILCH_EIER))
        assertTrue(match(shops, items, radius = 0.0).isEmpty())
        assertTrue(match(shops, items, radius = -10.0).isEmpty())
        assertTrue(match(shops, items, radius = Double.NaN).isEmpty())
        assertTrue(match(shops, emptyList()).isEmpty())
        assertTrue(match(emptyList(), items).isEmpty())
    }

    @Test
    fun matchesEveryShopWhoseAssortmentHasTheItem() {
        val supermarket = shop("node/1", SUPERMARKET, northMeters = 100.0)
        val bakery = shop("node/2", BAKERY, northMeters = 50.0)
        val items = listOf(item(1, "Milch", MILCH_EIER), item(2, "Brot", BROT_BACKWAREN))

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
    fun itemMatchesWhenAnyOfItsAssortmentsIsSold() {
        val result = match(
            listOf(shop("node/1", CHEMIST), shop("node/2", SHOES)),
            listOf(item(1, "Sonnencreme", Assortment.SPORT, Assortment.KOERPERPFLEGE)),
        )
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun shopsWithoutMatchingItemsAreDropped() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET), shop("node/2", SHOES), shop("node/3", "shop=hairdresser")),
            listOf(item(1, "Milch", MILCH_EIER)),
        )
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun medicinesOnlyAtPharmacies() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET), shop("node/2", CHEMIST), shop("node/3", PHARMACY, northMeters = 300.0)),
            listOf(item(1, "Ibuprofen", Assortment.MEDIKAMENTE)),
        )
        assertEquals(listOf("node/3"), result.map { it.shop.id })
    }

    @Test
    fun unclassifiedItemsMatchOnlyBroadShops() {
        val result = match(
            listOf(
                shop("node/1", SUPERMARKET),
                shop("node/2", DEPARTMENT_STORE, northMeters = 300.0),
                shop("node/3", SHOES),
                shop("node/4", VARIETY_STORE, northMeters = 50.0),
                shop("node/5", SUPERMARKET, northMeters = 20.0, name = "ALDI", brand = "Aldi Süd"),
                shop("node/6", VARIETY_STORE, northMeters = 30.0, name = "TEDi", brand = "TEDi"),
            ),
            listOf(item(1, "Irgendwas")),
        )
        assertEquals(listOf("node/1", "node/2"), result.map { it.shop.id })
    }

    @Test
    fun unclassifiedItemsDoNotSendTheUserToVarietyStores() {
        val dm = shop("node/1", CHEMIST, northMeters = 100.0, name = "dm-drogerie markt", brand = "dm")
        val tedi = shop("node/2", VARIETY_STORE, northMeters = 200.0, name = "TEDi", brand = "TEDi")
        val rewe = shop("node/3", SUPERMARKET, northMeters = 300.0, name = "REWE", brand = "REWE")
        val items = listOf(
            item(1, "Shampoo", Assortment.KOERPERPFLEGE),
            item(2, "Deo", Assortment.KOERPERPFLEGE),
            item(3, "Tempos"),
            item(4, "Klorollen"),
        )

        val result = match(listOf(dm, tedi, rewe), items)

        assertEquals(listOf("node/3", "node/1", "node/2"), result.map { it.shop.id })
        assertEquals(listOf("Deo", "Klorollen", "Shampoo", "Tempos"), result[0].items.map { it.name })
        assertEquals(listOf("Deo", "Shampoo"), result[2].items.map { it.name })
        assertEquals(result[1].score, result[2].score, 1e-9)
    }

    @Test
    fun theChainDecidesAssortmentBroadnessAndBonus() {
        // An E-Center mapped with brand "Edeka" is the hypermarket; an Aldi is a discounter
        // although shop=supermarket alone would be broad.
        val eCenter = shop("node/1", SUPERMARKET, northMeters = 300.0, name = "E-Center", brand = "Edeka")
        val aldi = shop("node/2", SUPERMARKET, northMeters = 100.0, name = "ALDI", brand = "Aldi Süd")
        val items = listOf(item(1, "Bohrmaschine", Assortment.WERKZEUG), item(2, "Irgendwas"))

        val result = match(listOf(eCenter, aldi), items)

        assertEquals(listOf("node/1"), result.map { it.shop.id })
        assertEquals(2.0 + ShopMatcher.CHAIN_BONUS, result.single().score, 1e-9)
    }

    @Test
    fun baumarktMatchesBaumarktCleanersButNotEverydayCleaning() {
        val obi = shop("node/1", DIY, name = "OBI", brand = "OBI")
        val items = listOf(
            item(1, "Klopapier", Assortment.HAUSHALT_REINIGUNG),
            item(2, "Spiritus", Assortment.HAUSHALT_REINIGUNG, Assortment.WERKZEUG),
        )
        assertEquals(listOf("Spiritus"), match(listOf(obi), items).single().items.map { it.name })
    }

    @Test
    fun phoneCreditIsFoundAtKiosksDiscountersAndPhoneShops() {
        val result = match(
            listOf(
                shop("node/1", "shop=kiosk", northMeters = 100.0),
                shop("node/2", SUPERMARKET, northMeters = 200.0, name = "Lidl", brand = "Lidl"),
                shop("node/3", "shop=mobile_phone", northMeters = 300.0),
                shop("node/4", SHOES, northMeters = 50.0),
                shop("node/5", CHEMIST, northMeters = 60.0, name = "dm", brand = "dm"),
            ),
            listOf(item(1, "Handyguthaben", Assortment.ZEITSCHRIFTEN_TABAK, Assortment.ELEKTRONIK)),
        )
        assertEquals(setOf("node/1", "node/2", "node/3"), result.map { it.shop.id }.toSet())
    }

    @Test
    fun knownChainsGetABonus() {
        val independent = shop("node/1", SUPERMARKET, northMeters = 50.0, name = "Frischemarkt Yilmaz")
        val lidl = shop("node/2", SUPERMARKET, northMeters = 300.0, name = "Lidl", brand = "Lidl")

        val result = match(listOf(independent, lidl), listOf(item(1, "Milch", MILCH_EIER)))

        assertEquals(listOf("node/2", "node/1"), result.map { it.shop.id })
        assertEquals(1.0 + ShopMatcher.CHAIN_BONUS, result[0].score, 1e-9)
        assertEquals(1.0, result[1].score, 1e-9)
    }

    @Test
    fun chainAssortmentWinsOverShopType() {
        // A chain's own assortment counts, not just the generic one of its OSM tag.
        val dm = shop("node/1", CHEMIST, name = "dm-drogerie markt", brand = "dm")
        val result = match(listOf(dm), listOf(item(1, "Shampoo", Assortment.KOERPERPFLEGE), item(2, "Bohrmaschine", Assortment.WERKZEUG)))
        assertEquals(listOf("Shampoo"), result.single().items.map { it.name })
    }

    @Test
    fun radiusEdgeIsInclusive() {
        val shop = shop("node/1", northMeters = 500.0)
        val distance = GeoMath.distanceMeters(here, shop.point)
        val items = listOf(item(1, "Milch", MILCH_EIER))

        assertEquals(1, match(listOf(shop), items, radius = distance).size)
        assertTrue(match(listOf(shop), items, radius = distance - 0.01).isEmpty())
    }

    @Test
    fun shopsOutsideRadiusAreIgnored() {
        val near = shop("node/1", northMeters = 400.0)
        val far = shop("node/2", northMeters = 600.0)
        val result = match(listOf(near, far), listOf(item(1, "Milch", MILCH_EIER)))
        assertEquals(listOf("node/1"), result.map { it.shop.id })
    }

    @Test
    fun matchesByPurchaseHistory() {
        val shoes = shop("node/7", SHOES)
        val other = shop("node/8", SHOES, northMeters = 200.0)
        val items = listOf(item(1, "  Spezial  Tee ", Assortment.KAFFEE_TEE))
        val purchases = listOf(PurchaseInfo(itemName = "spezial tee", shopId = "node/7", at = 1L))

        val result = match(listOf(shoes, other), items, purchases)

        assertEquals(listOf("node/7"), result.map { it.shop.id })
        assertEquals(setOf(1L), result[0].learnedItemIds)
        assertEquals(1.5, result[0].score, 1e-9)
    }

    @Test
    fun learnedIsRecordedAlsoWhenAssortmentMatches() {
        val result = match(
            listOf(shop("node/1", SUPERMARKET)),
            listOf(item(1, "Milch", MILCH_EIER), item(2, "Butter", MILCH_EIER)),
            listOf(PurchaseInfo("milch", "node/1", 1L), PurchaseInfo("milch", "node/1", 2L)),
        )
        assertEquals(1, result.size)
        assertEquals(setOf(1L), result[0].learnedItemIds)
        assertEquals(2 + 0.5, result[0].score, 1e-9)
    }

    @Test
    fun purchasesOfOtherItemsDoNotMatch() {
        val result = match(
            listOf(shop("node/1", SHOES)),
            listOf(item(1, "Milch", MILCH_EIER)),
            listOf(PurchaseInfo("butter", "node/1", 1L)),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun itemsAreSortedByGermanNameIgnoringCase() {
        val items = listOf(
            item(1, "zucker", Assortment.VORRAT),
            item(2, "Brot", BROT_BACKWAREN),
            item(3, "Äpfel", Assortment.OBST_GEMUESE),
            item(4, "banane", Assortment.OBST_GEMUESE),
            item(5, "Apfelsaft", Assortment.GETRAENKE),
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
        val items = listOf(item(1, "Brot", BROT_BACKWAREN), item(2, "Milch", MILCH_EIER))

        val result = match(shops, items)

        // node/5: two items (score 2); bakeries: one item each, nearest first, equal distance by id.
        assertEquals(listOf("node/5", "node/2", "node/3", "node/4"), result.map { it.shop.id })
    }

    @Test
    fun learnedBonusAffectsRanking() {
        val shops = listOf(
            shop("node/1", SUPERMARKET, northMeters = 10.0),
            shop("node/2", CHEMIST, northMeters = 300.0),
        )
        val items = listOf(item(1, "Milch", MILCH_EIER), item(2, "Seife", Assortment.KOERPERPFLEGE))
        val purchases = listOf(PurchaseInfo("seife", "node/2", 1L))

        val result = match(shops, items, purchases)

        // The supermarket has both (2.0); the drugstore only the soap, but it was bought there (1.5).
        assertEquals(listOf("node/1", "node/2"), result.map { it.shop.id })
        assertEquals(listOf(2.0, 1.5), result.map { it.score })
    }

    @Test
    fun nodeAndBuildingOfSameShopAreMergedKeepingBetterScore() {
        val node = shop("node/1", SUPERMARKET, northMeters = 100.0, name = "REWE")
        val way = shop("way/2", SUPERMARKET, northMeters = 140.0, name = "Rewe ")
        val items = listOf(item(1, "Milch", MILCH_EIER), item(2, "Brot", BROT_BACKWAREN))
        val purchases = listOf(PurchaseInfo("brot", "way/2", 1L))

        val result = match(listOf(node, way), items, purchases)

        assertEquals(listOf("way/2"), result.map { it.shop.id })
        assertEquals(2.5 + ShopMatcher.CHAIN_BONUS, result.single().score, 1e-9)
    }

    @Test
    fun duplicatesWithEqualScoreKeepNearer() {
        val node = shop("node/9", SUPERMARKET, northMeters = 130.0, name = "Edeka")
        val way = shop("way/1", SUPERMARKET, northMeters = 90.0, name = "Edeka")
        val result = match(listOf(node, way), listOf(item(1, "Milch", MILCH_EIER)))
        assertEquals(listOf("way/1"), result.map { it.shop.id })
    }

    @Test
    fun brandCountsAsNameForMerging() {
        val node = shop("node/1", CHEMIST, northMeters = 100.0, name = "dm-drogerie markt", brand = "dm")
        val way = shop("way/2", CHEMIST, northMeters = 110.0, name = null, brand = "DM-Drogerie Markt")
        val result = match(listOf(node, way), listOf(item(1, "Seife", Assortment.KOERPERPFLEGE)))
        assertEquals(1, result.size)
    }

    @Test
    fun differentTagOrFarApartAreNotMerged() {
        val items = listOf(item(1, "Milch", MILCH_EIER))
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
            listOf(item(1, "Milch", MILCH_EIER)),
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
            listOf(item(1, "Milch", MILCH_EIER)),
        )
        assertEquals(listOf("node/1", "way/2", "way/3"), result.map { it.shop.id })
    }

    @Test
    fun duplicateShopIdsAreCountedOnce() {
        val shop = shop("node/1", SUPERMARKET)
        val result = match(listOf(shop, shop), listOf(item(1, "Milch", MILCH_EIER)))
        assertEquals(1, result.size)
    }

    private companion object {
        const val SUPERMARKET = "shop=supermarket"
        const val BAKERY = "shop=bakery"
        const val CHEMIST = "shop=chemist"
        const val SHOES = "shop=shoes"
        const val PHARMACY = "amenity=pharmacy"
        const val DEPARTMENT_STORE = "shop=department_store"
        const val VARIETY_STORE = "shop=variety_store"
        const val DIY = "shop=doityourself"
        val MILCH_EIER = Assortment.MILCH_EIER
        val BROT_BACKWAREN = Assortment.BROT_BACKWAREN
    }
}
