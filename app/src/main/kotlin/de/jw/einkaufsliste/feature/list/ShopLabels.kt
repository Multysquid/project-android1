package de.jw.einkaufsliste.feature.list

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.ShopEntity

/** Shown for a pinned shop that is no longer in the shop cache. */
internal const val MISSING_SHOP_LABEL = "Laden nicht mehr gespeichert"

/** German shop type, e.g. "Supermarkt". */
internal fun ShopEntity.typeLabel(): String = CategoryCatalog.shopTagLabel(tag)

/** Name, else brand, else the shop type ("Bäckerei"). */
internal fun ShopEntity.displayName(): String =
    name?.takeIf { it.isNotBlank() } ?: brand?.takeIf { it.isNotBlank() } ?: typeLabel()

/** "EDEKA Müller · Supermarkt", or just "Supermarkt" when the shop has no name. */
internal fun ShopEntity.displayNameWithType(): String {
    val title = displayName()
    val type = typeLabel()
    return if (title == type) title else "$title · $type"
}
