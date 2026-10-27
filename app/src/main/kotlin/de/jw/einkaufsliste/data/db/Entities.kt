package de.jw.einkaufsliste.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category ("Unterliste"). [slug] is stable for the seeded defaults (see
 * CategoryCatalog) and `custom_<uuid>` for user-created ones. [osmTags] is a
 * comma-separated list such as `shop=supermarket,shop=convenience`.
 */
@Entity(tableName = "categories", indices = [Index(value = ["slug"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val name: String,
    val emoji: String,
    val osmTags: String,
    val sortOrder: Int,
)

enum class ItemSource { MANUAL, PHOTO, BARCODE }

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long?,
    val quantity: String? = null,
    val note: String? = null,
    val barcode: String? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Shop id (`node/123`) the user pinned this item to; it then only matches that shop. */
    val pinnedShopId: String? = null,
    val source: ItemSource = ItemSource.MANUAL,
)

/** Cached barcode lookups, so each barcode is looked up online only once. */
@Entity(tableName = "barcode_cache")
data class BarcodeCacheEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val quantity: String?,
    val categorySlug: String,
    /** "openfoodfacts", "openproductsfacts", ..., or "user" when typed in by hand. */
    val source: String,
    val fetchedAt: Long,
)

/** Shops downloaded from OpenStreetMap. [id] is `node/123`, `way/456` or `relation/789`. */
@Entity(tableName = "shops", indices = [Index("lat"), Index("lon")])
data class ShopEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val brand: String?,
    val lat: Double,
    val lon: Double,
    val tag: String,
    val openingHours: String?,
    val fetchedAt: Long,
)

/** "Item bought at shop", recorded when an item is ticked off near a shop; used to rank shops. */
@Entity(tableName = "purchases", indices = [Index("shopId"), Index("itemName")])
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Normalized with ItemNames.normalize. */
    val itemName: String,
    val categoryId: Long?,
    val shopId: String,
    val at: Long,
)

/** When the app last notified about a shop, for the per-shop cooldown. */
@Entity(tableName = "notification_log")
data class NotificationLogEntity(
    @PrimaryKey val shopId: String,
    @ColumnInfo(name = "lastNotifiedAt") val lastNotifiedAt: Long,
)
