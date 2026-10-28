package de.jw.einkaufsliste.feature.background

import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.text.ItemNames
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.db.PurchaseEntity
import de.jw.einkaufsliste.feature.shops.ShopRepository

/** Learns where items are bought: ticking off an item next to a cached shop records a purchase there. */
class PurchaseRecorder(
    private val db: AppDatabase,
    private val locationProvider: LocationProvider,
    private val shopRepository: ShopRepository,
) {
    /** Records "bought at shop X" when the user is within ~75 m of a cached shop. Never throws. */
    suspend fun onItemBought(item: ItemEntity) {
        runLogging(TAG, "Could not record purchase") { record(item) }
    }

    private suspend fun record(item: ItemEntity) {
        val itemName = ItemNames.normalize(item.name)
        if (itemName.isEmpty() || !locationProvider.hasForegroundPermission()) return
        val fix = locationProvider.currentFix(precise = true) ?: return
        // Approximate location (or a poor fix) cannot tell neighbouring shops apart.
        if (!fix.hasAccuracy() || fix.accuracy > MAX_ACCURACY_METERS) return
        val here = GeoPoint(fix.latitude, fix.longitude)
        val shop = shopRepository.shopsNear(here, NEAR_SHOP_METERS)
            .minByOrNull { GeoMath.distanceMeters(here, it.point) }
            ?: return
        // Unticked again while the location was being determined.
        if (db.itemDao().getById(item.id)?.done == false) return
        db.purchaseDao().insert(
            PurchaseEntity(
                itemName = itemName,
                categoryId = item.categoryId,
                shopId = shop.id,
                at = item.doneAt ?: System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        const val TAG = "PurchaseRecorder"
        const val NEAR_SHOP_METERS = 75.0
        const val MAX_ACCURACY_METERS = 100f
    }
}
