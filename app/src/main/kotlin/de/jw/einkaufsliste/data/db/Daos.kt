package de.jw.einkaufsliste.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE slug = :slug")
    suspend fun getBySlug(slug: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)
}

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY done, createdAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE done = 0 ORDER BY createdAt DESC")
    suspend fun getOpen(): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE done = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getById(id: Long): ItemEntity?

    @Insert
    suspend fun insert(item: ItemEntity): Long

    @Update
    suspend fun update(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM items WHERE done = 1")
    suspend fun deleteDone(): Int

    @Query("UPDATE items SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun moveCategory(fromId: Long, toId: Long)
}

@Dao
interface BarcodeCacheDao {
    @Query("SELECT * FROM barcode_cache WHERE barcode = :barcode")
    suspend fun get(barcode: String): BarcodeCacheEntity?

    @Upsert
    suspend fun upsert(entry: BarcodeCacheEntity)
}

@Dao
interface AssortmentCacheDao {
    @Query("SELECT * FROM assortment_cache")
    fun observeAll(): Flow<List<AssortmentCacheEntity>>

    @Query("SELECT * FROM assortment_cache")
    suspend fun getAll(): List<AssortmentCacheEntity>

    @Upsert
    suspend fun upsert(entry: AssortmentCacheEntity)

    @Query("DELETE FROM assortment_cache WHERE fetchedAt < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops")
    fun observeAll(): Flow<List<ShopEntity>>

    @Query("SELECT * FROM shops WHERE lat BETWEEN :minLat AND :maxLat AND lon BETWEEN :minLon AND :maxLon")
    suspend fun getInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<ShopEntity>

    @Query("SELECT * FROM shops WHERE id = :id")
    suspend fun getById(id: String): ShopEntity?

    @Query("SELECT * FROM shops WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ShopEntity>

    @Query("SELECT COUNT(*) FROM shops")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(shops: List<ShopEntity>)

    @Query("DELETE FROM shops WHERE fetchedAt < :before")
    suspend fun deleteFetchedBefore(before: Long): Int
}

@Dao
interface PurchaseDao {
    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Query("SELECT * FROM purchases WHERE at >= :since")
    suspend fun getSince(since: Long): List<PurchaseEntity>

    @Query("DELETE FROM purchases WHERE at < :before")
    suspend fun deleteBefore(before: Long): Int

    @Query("SELECT COUNT(*) FROM purchases")
    suspend fun count(): Int

    /** Forgets the whole purchase history; returns how many entries were deleted. */
    @Query("DELETE FROM purchases")
    suspend fun deleteAll(): Int
}

@Dao
interface NotificationLogDao {
    @Query("SELECT * FROM notification_log WHERE shopId = :shopId")
    suspend fun get(shopId: String): NotificationLogEntity?

    @Upsert
    suspend fun upsert(entry: NotificationLogEntity)

    @Query("DELETE FROM notification_log WHERE lastNotifiedAt < :before")
    suspend fun deleteBefore(before: Long): Int
}
