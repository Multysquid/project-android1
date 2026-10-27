package de.jw.einkaufsliste.data.db

import de.jw.einkaufsliste.core.catalog.Assortment
import de.jw.einkaufsliste.core.model.CategoryDef
import de.jw.einkaufsliste.core.model.ItemInfo
import de.jw.einkaufsliste.core.model.PurchaseInfo
import de.jw.einkaufsliste.core.model.ShopInfo

fun CategoryEntity.osmTagList(): List<String> =
    osmTags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun List<String>.toOsmTagsColumn(): String =
    map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(",")

fun CategoryEntity.toDef(): CategoryDef =
    CategoryDef(slug = slug, name = name, emoji = emoji, osmTags = osmTagList(), sortOrder = sortOrder)

fun CategoryDef.toEntity(): CategoryEntity =
    CategoryEntity(slug = slug, name = name, emoji = emoji, osmTags = osmTags.toOsmTagsColumn(), sortOrder = sortOrder)

fun ItemEntity.toItemInfo(category: CategoryEntity?, assortments: Set<Assortment>): ItemInfo =
    ItemInfo(id = id, name = name, categorySlug = category?.slug, assortments = assortments)

fun ShopEntity.toShopInfo(): ShopInfo =
    ShopInfo(id = id, name = name, brand = brand, lat = lat, lon = lon, tag = tag, openingHours = openingHours)

fun ShopInfo.toEntity(fetchedAt: Long): ShopEntity =
    ShopEntity(
        id = id,
        name = name,
        brand = brand,
        lat = lat,
        lon = lon,
        tag = tag,
        openingHours = openingHours,
        fetchedAt = fetchedAt,
    )

fun PurchaseEntity.toPurchaseInfo(): PurchaseInfo = PurchaseInfo(itemName = itemName, shopId = shopId, at = at)
