package de.jw.einkaufsliste.data.repo

import androidx.room.withTransaction
import de.jw.einkaufsliste.core.backup.BackupItem
import de.jw.einkaufsliste.core.backup.BackupMerge
import de.jw.einkaufsliste.core.backup.ExistingItem
import de.jw.einkaufsliste.core.backup.ImportResult
import de.jw.einkaufsliste.core.backup.ListBackup
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.db.ItemSource
import de.jw.einkaufsliste.data.db.toDef
import de.jw.einkaufsliste.data.db.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * "Liste sichern": the list (categories and items) to and from a backup file, see
 * [ListBackup] for the format and [BackupMerge] for how an import is merged. Shops,
 * purchases, settings and the API key are not part of a backup.
 */
class BackupService(private val db: AppDatabase) {
    private val categoryDao = db.categoryDao()
    private val itemDao = db.itemDao()

    /** The whole list as backup JSON. */
    suspend fun export(): String {
        // Items first: a category deleted in between leaves its items without a slug, and
        // the import puts those into "Sonstiges", where the delete moved them anyway.
        val items = itemDao.observeAll().first()
        val categories = categoryDao.getAll()
        val slugById = categories.associate { it.id to it.slug }
        val backup = ListBackup(
            exportedAt = System.currentTimeMillis(),
            categories = categories.map { it.toDef() },
            items = items.map { item ->
                BackupItem(
                    name = item.name,
                    categorySlug = item.categoryId?.let(slugById::get),
                    quantity = item.quantity,
                    note = item.note,
                    barcode = item.barcode,
                    done = item.done,
                    createdAt = item.createdAt,
                    doneAt = item.doneAt,
                    pinnedShopId = item.pinnedShopId,
                )
            },
        )
        return withContext(Dispatchers.Default) { backup.toJson() }
    }

    /**
     * Merges the backup [json] into the list in one transaction: missing categories are
     * created, items already on the list are skipped, nothing is changed or deleted.
     * Throws [de.jw.einkaufsliste.core.backup.BackupFormatException] (German message) when
     * [json] is not a backup this app can read.
     */
    suspend fun import(json: String): ImportResult {
        val backup = withContext(Dispatchers.Default) { ListBackup.fromJson(json) }
        // Ticked-off items only decide whether a ticked-off item from the file is new. The DAO
        // offers them only as a Flow, which is read before the transaction, not inside it.
        val doneItems = itemDao.observeAll().first().filter { it.done }
        return db.withTransaction {
            val categories = categoryDao.getAll()
            val slugById = categories.associate { it.id to it.slug }
            val existing = (itemDao.getOpen() + doneItems).map { item ->
                ExistingItem(name = item.name, categorySlug = item.categoryId?.let(slugById::get), done = item.done)
            }
            val plan = BackupMerge.plan(backup, categories.map { it.toDef() }, existing)

            val idBySlug = categories.associateTo(HashMap()) { it.slug to it.id }
            plan.newCategories.forEach { category -> idBySlug[category.slug] = categoryDao.insert(category.toEntity()) }
            val fallbackId = idBySlug[CategoryCatalog.FALLBACK_SLUG]
            val now = System.currentTimeMillis()
            plan.newItems.forEach { item ->
                val createdAt = item.createdAt ?: now
                itemDao.insert(
                    ItemEntity(
                        name = item.name,
                        categoryId = item.categorySlug?.let(idBySlug::get) ?: fallbackId,
                        quantity = item.quantity,
                        note = item.note,
                        barcode = item.barcode,
                        done = item.done,
                        doneAt = if (item.done) item.doneAt ?: createdAt else null,
                        createdAt = createdAt,
                        pinnedShopId = item.pinnedShopId,
                        source = ItemSource.MANUAL,
                    ),
                )
            }
            plan.result
        }
    }
}
