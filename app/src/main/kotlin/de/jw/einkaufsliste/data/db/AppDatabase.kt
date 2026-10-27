package de.jw.einkaufsliste.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ItemEntity::class,
        BarcodeCacheEntity::class,
        ShopEntity::class,
        PurchaseEntity::class,
        NotificationLogEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun barcodeCacheDao(): BarcodeCacheDao
    abstract fun shopDao(): ShopDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun notificationLogDao(): NotificationLogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "einkaufsliste.db")
                .build()
    }
}
