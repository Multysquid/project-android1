package de.jw.einkaufsliste.feature.shops

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import de.jw.einkaufsliste.core.osm.OpeningHours
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale
import kotlin.math.max

/** Smallest radius of "Weiter weg suchen". */
internal const val WIDENED_MIN_RADIUS_METERS = 2_000

/** Largest radius of "Weiter weg suchen" (the largest radius setting). */
internal const val WIDENED_MAX_RADIUS_METERS = 5_000

/** Radius of "Weiter weg suchen": 2 km, or twice the setting if that is larger, at most 5 km. */
internal fun widenedRadiusFor(radiusMeters: Int): Int =
    max(WIDENED_MIN_RADIUS_METERS, radiusMeters * 2).coerceAtMost(WIDENED_MAX_RADIUS_METERS)

/** German shop type, e.g. "Supermarkt". */
internal fun ShopInfo.typeLabel(): String = CategoryCatalog.shopTagLabel(tag)

/** Name, else brand, else the shop type ("Bäckerei"). */
internal fun ShopInfo.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: brand?.takeIf { it.isNotBlank() } ?: typeLabel()

/** `geo:` URI that opens a map app at the shop, labelled with [label]. */
internal fun routeUri(shop: ShopInfo, label: String): String {
    val coordinates = String.format(Locale.ROOT, "%.6f,%.6f", shop.lat, shop.lon)
    // URLEncoder also encodes "(" and ")", which would end the label early; "+" is a space there.
    val encodedLabel = URLEncoder.encode(label, "UTF-8").replace("+", "%20")
    return "geo:0,0?q=$coordinates($encodedLabel)"
}

/** One shop card of the "In der Nähe" screen. */
data class NearbyShopRow(
    val match: ShopMatch,
    val title: String,
    /** Null when it would only repeat [title] (shop without name or brand). */
    val typeLabel: String?,
    /** Null when the opening hours are missing or not understood. */
    val openNow: Boolean?,
) {
    val hasLearnedItems: Boolean get() = match.items.any { it.id in match.learnedItemIds }
}

internal fun ShopMatch.toRow(now: LocalDateTime): NearbyShopRow {
    val title = shop.displayName()
    val type = shop.typeLabel()
    return NearbyShopRow(
        match = this,
        title = title,
        typeLabel = type.takeIf { it != title },
        openNow = OpeningHours.isOpen(shop.openingHours, now),
    )
}
