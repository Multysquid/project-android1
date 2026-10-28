package de.jw.einkaufsliste.core.model

import de.jw.einkaufsliste.core.catalog.Assortment

/** A WGS84 coordinate. */
data class GeoPoint(val lat: Double, val lon: Double)

/**
 * A category (Unterliste) as the domain logic sees it.
 *
 * [osmTags] are OpenStreetMap tags in `key=value` form (`shop=supermarket`) that earlier
 * versions used for matching. Matching now goes by [ItemInfo.assortments]; the tags are
 * only kept so backups stay compatible.
 */
data class CategoryDef(
    val slug: String,
    val name: String,
    val emoji: String,
    val osmTags: List<String>,
    val sortOrder: Int,
)

/**
 * A shop from OpenStreetMap.
 *
 * [id] is `type/osmId`, for example `node/123456`. [tag] is the tag that made it a
 * shop, in `key=value` form (`shop=supermarket`).
 */
data class ShopInfo(
    val id: String,
    val name: String?,
    val brand: String?,
    val lat: Double,
    val lon: Double,
    val tag: String,
    val openingHours: String?,
) {
    val point: GeoPoint get() = GeoPoint(lat, lon)
}

/**
 * An open shopping-list item and what kind of product it is. [assortments] is empty when
 * the app could not tell; such an item is looked for in broad stores (see [ShopMatcher]).
 */
data class ItemInfo(
    val id: Long,
    val name: String,
    val categorySlug: String?,
    val assortments: Set<Assortment>,
)

/** "Item with this (normalized) name was bought at this shop." */
data class PurchaseInfo(
    val itemName: String,
    val shopId: String,
    val at: Long,
)

/**
 * A shop that probably carries some of the open items.
 *
 * [learnedItemIds] are the ids of [items] that matched because of purchase history
 * rather than (or in addition to) the shop's assortment.
 */
data class ShopMatch(
    val shop: ShopInfo,
    val distanceMeters: Double,
    val items: List<ItemInfo>,
    val learnedItemIds: Set<Long>,
    val score: Double,
)

/** Result of a barcode lookup in an Open*Facts database. */
data class ProductInfo(
    val barcode: String,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val categorySlug: String,
    val source: String,
)

/** Result of recognizing a photographed item with Gemini. */
data class RecognizedItem(
    val name: String,
    val categorySlug: String,
    val quantity: String?,
    val confidence: Double?,
)

/** Quiet hours as minutes of the day; [startMinute] > [endMinute] means "over midnight". */
data class QuietHours(val startMinute: Int, val endMinute: Int)
