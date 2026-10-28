package de.jw.einkaufsliste.data.repo

import de.jw.einkaufsliste.core.match.ShopMatcher
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopMatch
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.toPurchaseInfo
import de.jw.einkaufsliste.feature.shops.ShopRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Combines open items, cached shops and purchase history into "which shop nearby has what". */
class MatchService(
    private val db: AppDatabase,
    private val itemRepository: ItemRepository,
    private val shopRepository: ShopRepository,
) {
    /**
     * Matches within [radiusMeters] of [here], best first. Uses only cached shops (no network).
     * Matching runs on [Dispatchers.Default]: thousands of shops around a city centre take a while.
     */
    suspend fun matchesAround(here: GeoPoint, radiusMeters: Double): List<ShopMatch> {
        val items = itemRepository.openItemsForMatching()
        if (items.isEmpty()) return emptyList()
        val shops = shopRepository.shopsNear(here, radiusMeters)
        if (shops.isEmpty()) return emptyList()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PURCHASE_HISTORY_DAYS)
        val purchases = db.purchaseDao().getSince(since).map { it.toPurchaseInfo() }
        return withContext(Dispatchers.Default) { ShopMatcher.match(here, radiusMeters, shops, items, purchases) }
    }

    private companion object {
        const val PURCHASE_HISTORY_DAYS = 365L
    }
}
