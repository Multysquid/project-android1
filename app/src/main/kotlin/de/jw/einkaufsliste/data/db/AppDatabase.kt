package de.jw.einkaufsliste.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ItemEntity::class,
        BarcodeCacheEntity::class,
        ShopEntity::class,
        PurchaseEntity::class,
        NotificationLogEntity::class,
        AssortmentCacheEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun barcodeCacheDao(): BarcodeCacheDao
    abstract fun shopDao(): ShopDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun notificationLogDao(): NotificationLogDao
    abstract fun assortmentCacheDao(): AssortmentCacheDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "einkaufsliste.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Version 2 adds the assortment cache. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `assortment_cache` (`nameKey` TEXT NOT NULL, " +
                        "`assortments` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`nameKey`))",
                )
            }
        }
    }
}
