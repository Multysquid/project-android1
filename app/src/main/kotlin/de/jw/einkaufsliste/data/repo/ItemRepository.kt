package de.jw.einkaufsliste.data.repo

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.ItemInfo
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemEntity
import de.jw.einkaufsliste.data.db.ItemSource
import de.jw.einkaufsliste.data.db.toItemInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** One "Unterliste": a category and its items, open items first. */
data class ListSection(
    val category: CategoryEntity?,
    val items: List<ItemEntity>,
) {
    val openCount: Int get() = items.count { !it.done }
}

/**
 * Shopping list items.
 *
 * [onItemBought] is called after an item was ticked off; the app uses it to record
 * where it was bought (see PurchaseRecorder). [onItemNamed] is called after an item was
 * added or edited; the app uses it to classify the name (see AssortmentService). Both
 * must return quickly.
 */
class ItemRepository(
    db: AppDatabase,
    private val assortments: AssortmentService,
    private val onItemBought: (ItemEntity) -> Unit,
    private val onItemNamed: (String) -> Unit,
) {
    private val itemDao = db.itemDao()
    private val categoryDao = db.categoryDao()

    /** The list grouped into sections in category order; empty categories are left out. */
    fun observeSections(): Flow<List<ListSection>> =
        combine(itemDao.observeAll(), categoryDao.observeAll()) { items, categories ->
            val fallback = categories.firstOrNull { it.slug == CategoryCatalog.FALLBACK_SLUG }
            val byCategory = items.groupBy { it.categoryId ?: fallback?.id }
            val sections = categories.mapNotNull { category ->
                byCategory[category.id]?.let { ListSection(category, sortItems(it)) }
            }
            val orphans = byCategory[null]
            if (orphans != null) sections + ListSection(null, sortItems(orphans)) else sections
        }

    fun observeOpenCount(): Flow<Int> = itemDao.observeOpenCount()

    suspend fun getById(id: Long): ItemEntity? = itemDao.getById(id)

    suspend fun add(
        name: String,
        categoryId: Long?,
        quantity: String? = null,
        note: String? = null,
        barcode: String? = null,
        source: ItemSource = ItemSource.MANUAL,
    ): Long {
        val id = itemDao.insert(
            ItemEntity(
                name = name.trim(),
                categoryId = categoryId,
                quantity = quantity?.trim()?.ifEmpty { null },
                note = note?.trim()?.ifEmpty { null },
                barcode = barcode,
                source = source,
            ),
        )
        onItemNamed(name.trim())
        return id
    }

    suspend fun update(item: ItemEntity) {
        itemDao.update(item.copy(name = item.name.trim()))
        onItemNamed(item.name.trim())
    }

    suspend fun setDone(id: Long, done: Boolean) {
        val item = itemDao.getById(id) ?: return
        if (item.done == done) return
        val updated = item.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)
        itemDao.update(updated)
        if (done) onItemBought(updated)
    }

    suspend fun delete(id: Long) = itemDao.deleteById(id)

    /**
     * Puts a deleted [item] back exactly as it was (same id, done state and times) in
     * one write. No purchase is recorded; that happened when it was ticked off.
     */
    suspend fun restore(item: ItemEntity): Long = itemDao.insert(item)

    /** Removes all ticked-off items; returns how many. */
    suspend fun clearDone(): Int = itemDao.deleteDone()

    /** Open items with what kind of product each is (see [AssortmentService]), for shop matching. */
    suspend fun openItemsForMatching(): List<ItemInfo> {
        val categories = categoryDao.getAll().associateBy { it.id }
        val fallback = categories.values.firstOrNull { it.slug == CategoryCatalog.FALLBACK_SLUG }
        val answers = assortments.cache()
        return itemDao.getOpen().map { item ->
            val category = item.categoryId?.let { categories[it] } ?: fallback
            item.toItemInfo(category, AssortmentService.resolve(item.name, category?.slug, answers))
        }
    }

    private fun sortItems(items: List<ItemEntity>): List<ItemEntity> =
        items.sortedWith(compareBy<ItemEntity> { it.done }.thenByDescending { if (it.done) it.doneAt ?: 0L else it.createdAt })
}
