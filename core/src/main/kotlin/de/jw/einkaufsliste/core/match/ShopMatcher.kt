package de.jw.einkaufsliste.core.match

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

    /** Extra score per item pinned to the shop. */
    const val PINNED_BONUS = 1.0

    /**
     * Returns shops within [radiusMeters] of [here] that match at least one item,
     * best first. An item with a pinned shop matches only that shop.
     *
     * Score: matched items + [LEARNED_BONUS] per learned item + [PINNED_BONUS] per
     * pinned item; ties go to the nearer shop, then the lower shop id. Of duplicate
     * OSM objects for one shop only the best match is kept.
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
                tags = item.osmTags.mapTo(HashSet()) { it.trim() },
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

    private class Candidate(val item: ItemInfo, val tags: Set<String>, val boughtAt: Set<String>)

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
        val shopTag = shop.tag.trim()
        val matched = ArrayList<ItemInfo>()
        val learned = HashSet<Long>()
        var pinned = 0
        for (candidate in candidates) {
            val item = candidate.item
            val pinnedShopId = item.pinnedShopId
            if (pinnedShopId != null) {
                if (pinnedShopId == shop.id) {
                    matched += item
                    pinned++
                }
                continue
            }
            val byTag = shopTag in candidate.tags
            val byHistory = shop.id in candidate.boughtAt
            if (byTag || byHistory) {
                matched += item
                if (byHistory) learned += item.id
            }
        }
        if (matched.isEmpty()) return null
        return ShopMatch(
            shop = shop,
            distanceMeters = distance,
            items = matched.sortedWith(itemOrder),
            learnedItemIds = learned,
            score = matched.size + LEARNED_BONUS * learned.size + PINNED_BONUS * pinned,
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
