package de.jw.einkaufsliste.core.match

import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.catalog.StoreCatalog
import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ItemInfo
import de.jw.einkaufsliste.core.model.PurchaseInfo
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import de.jw.einkaufsliste.core.text.ItemNames
import java.text.Collator
import java.util.Locale

/** Decides which nearby shops probably carry which open items (see docs/ARCHITECTURE.md, "Matching"). */
object ShopMatcher {
    /** Shops with the same name (or brand) and tag closer than this are one shop mapped twice (node + building). */
    const val DUPLICATE_DISTANCE_METERS = 60.0

    /** Extra score per item that was bought at the shop before. */
    const val LEARNED_BONUS = 0.5

    /** Extra score for a shop of a known chain ([StoreCatalog.chains]), so popular stores rank first. */
    const val CHAIN_BONUS = 0.25

    /**
     * Returns shops within [radiusMeters] of [here] that probably carry at least one item,
     * best first. Nobody assigns items to shops: an item matches every shop whose
     * assortment ([StoreCatalog.assortmentsOf]) contains one of the item's
     * [ItemInfo.assortments]. An item the app could not classify matches broad shops
     * ([StoreCatalog.isBroad]). An item also matches every shop it was bought at before.
     *
     * Score: matched items + [LEARNED_BONUS] per learned item + [CHAIN_BONUS] for a known
     * chain; ties go to the nearer shop, then the lower shop id. Of duplicate OSM objects
     * for one shop only the best match is kept.
     */
    fun match(
        here: GeoPoint,
        radiusMeters: Double,
        shops: List<ShopInfo>,
        items: List<ItemInfo>,
        purchases: List<PurchaseInfo> = emptyList(),
    ): List<ShopMatch> {
        if (!(radiusMeters > 0.0) || items.isEmpty() || shops.isEmpty()) return emptyList()

        val shopsByItemName: Map<String, Set<String>> = purchases
            .groupBy({ ItemNames.normalize(it.itemName) }, { it.shopId })
            .mapValues { (_, shopIds) -> shopIds.toSet() }
        val candidates = items.distinctBy { it.id }.map { item ->
            Candidate(
                item = item,
                boughtAt = shopsByItemName[ItemNames.normalize(item.name)].orEmpty(),
            )
        }
        val itemOrder = itemComparator()

        val matches = shops.distinctBy { it.id }.mapNotNull { shop ->
            val distance = GeoMath.distanceMeters(here, shop.point)
            if (!(distance <= radiusMeters)) null else matchShop(shop, distance, candidates, itemOrder)
        }
        return removeDuplicates(matches.sortedWith(ranking))
    }

    private class Candidate(val item: ItemInfo, val boughtAt: Set<String>)

    private val ranking: Comparator<ShopMatch> =
        compareByDescending<ShopMatch> { it.score }
            .thenBy { it.distanceMeters }
            .thenBy { it.shop.id }

    private fun itemComparator(): Comparator<ItemInfo> {
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }
        return Comparator<ItemInfo> { a, b -> collator.compare(a.name, b.name) }
            .thenBy { it.name }
            .thenBy { it.id }
    }

    private fun matchShop(
        shop: ShopInfo,
        distance: Double,
        candidates: List<Candidate>,
        itemOrder: Comparator<ItemInfo>,
    ): ShopMatch? {
        val chain = StoreCatalog.chainFor(shop)
        val sells: Set<Assortment> = StoreCatalog.assortmentsOf(shop, chain)
        val broad = StoreCatalog.isBroad(shop, chain)
        val matched = ArrayList<ItemInfo>()
        val learned = HashSet<Long>()
        for (candidate in candidates) {
            val item = candidate.item
            val byAssortment = if (item.assortments.isEmpty()) broad else item.assortments.any { it in sells }
            val byHistory = shop.id in candidate.boughtAt
            if (byAssortment || byHistory) {
                matched += item
                if (byHistory) learned += item.id
            }
        }
        if (matched.isEmpty()) return null
        val chainBonus = if (chain != null) CHAIN_BONUS else 0.0
        return ShopMatch(
            shop = shop,
            distanceMeters = distance,
            items = matched.sortedWith(itemOrder),
            learnedItemIds = learned,
            score = matched.size + LEARNED_BONUS * learned.size + chainBonus,
        )
    }

    /** Expects [sorted] best first, so the first of each duplicate group is the one to keep. */
    private fun removeDuplicates(sorted: List<ShopMatch>): List<ShopMatch> {
        val keptByKey = HashMap<String, MutableList<ShopMatch>>()
        val result = ArrayList<ShopMatch>(sorted.size)
        for (match in sorted) {
            val key = duplicateKey(match.shop)
            if (key == null) {
                result += match
                continue
            }
            val sameShop = keptByKey.getOrPut(key) { ArrayList() }
            val isDuplicate = sameShop.any {
                GeoMath.distanceMeters(it.shop.point, match.shop.point) <= DUPLICATE_DISTANCE_METERS
            }
            if (!isDuplicate) {
                sameShop += match
                result += match
            }
        }
        return result
    }

    /** "shop=supermarket|rewe"; null when the shop has neither name nor brand (never merged). */
    private fun duplicateKey(shop: ShopInfo): String? {
        val label = shop.name?.takeIf { it.isNotBlank() } ?: shop.brand?.takeIf { it.isNotBlank() } ?: return null
        return shop.tag.trim() + "|" + ItemNames.normalize(label)
    }
}
