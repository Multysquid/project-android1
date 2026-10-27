package de.jw.einkaufsliste.data.repo

import androidx.room.withTransaction
import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.toEntity
import de.jw.einkaufsliste.data.db.toOsmTagsColumn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * The categories ("Unterlisten"). Every read waits until the German defaults are in the
 * database: on a fresh install the first screen, the add dialog or a photo can otherwise
 * read an empty table and save an item without Unterliste.
 */
class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    private val seedLock = Mutex()

    @Volatile
    private var seeded = false

    fun observeAll(): Flow<List<CategoryEntity>> = flow {
        ensureSeeded()
        emitAll(dao.observeAll())
    }

    suspend fun getAll(): List<CategoryEntity> {
        ensureSeeded()
        return dao.getAll()
    }

    suspend fun getById(id: Long): CategoryEntity? = dao.getById(id)

    /**
     * Fills an empty database with the German default categories, once per process. Called
     * at app start and before every read above; after the first call it only checks a flag.
     */
    suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (seeded) return
            if (dao.count() == 0) dao.insertAll(CategoryCatalog.defaults.map { it.toEntity() })
            seeded = true
        }
    }

    /** Id of the category with [slug]; falls back to "Sonstiges" for unknown or null slugs. */
    suspend fun idForSlug(slug: String?): Long? {
        ensureSeeded()
        slug?.let { dao.getBySlug(it) }?.let { return it.id }
        return dao.getBySlug(CategoryCatalog.FALLBACK_SLUG)?.id
    }

    /**
     * "Standard-Unterlisten wiederherstellen": brings back the default categories the user
     * deleted, so Gemini and the barcode lookup (which map to their slugs) can sort into them
     * again. A category the user made by hand with the same name takes over the default's
     * slug instead of getting a twin; the others are added after the existing ones, so the
     * user's order stays. Returns how many defaults were restored.
     */
    suspend fun restoreMissingDefaults(): Int = db.withTransaction {
        val existing = dao.getAll()
        val slugs = existing.mapTo(HashSet()) { it.slug }
        val missing = CategoryCatalog.defaults.filter { it.slug !in slugs }
        if (missing.isEmpty()) return@withTransaction 0

        val handMade = existing.filter { it.slug.startsWith(CUSTOM_SLUG_PREFIX) }.toMutableList()
        var nextSortOrder = dao.maxSortOrder() + 1
        val toInsert = mutableListOf<CategoryEntity>()
        missing.forEach { def ->
            val twin = handMade.firstOrNull { it.name.trim().equals(def.name, ignoreCase = true) }
            if (twin != null) {
                handMade.remove(twin)
                dao.update(twin.copy(slug = def.slug))
            } else {
                toInsert += def.toEntity().copy(sortOrder = nextSortOrder++)
            }
        }
        dao.insertAll(toInsert)
        missing.size
    }

    suspend fun add(name: String, emoji: String, osmTags: List<String>): Long =
        dao.insert(
            CategoryEntity(
                slug = CUSTOM_SLUG_PREFIX + UUID.randomUUID().toString(),
                name = name.trim(),
                emoji = emoji.trim().ifEmpty { "🛒" },
                osmTags = osmTags.toOsmTagsColumn(),
                sortOrder = dao.maxSortOrder() + 1,
            ),
        )

    suspend fun update(category: CategoryEntity) = dao.update(category.copy(name = category.name.trim()))

    /** Saves the order of [categories] as shown, top to bottom. */
    suspend fun reorder(categories: List<CategoryEntity>) =
        dao.updateAll(categories.mapIndexed { index, category -> category.copy(sortOrder = index) })

    /** Restores name, emoji and shop types of a default category. No-op for custom categories. */
    suspend fun resetToDefault(category: CategoryEntity) {
        val def = CategoryCatalog.bySlug(category.slug) ?: return
        dao.update(category.copy(name = def.name, emoji = def.emoji, osmTags = def.osmTags.toOsmTagsColumn()))
    }

    /**
     * Deletes [category] and moves its items to "Sonstiges". Returns false (and does
     * nothing) for "Sonstiges" itself, which must always exist.
     */
    suspend fun delete(category: CategoryEntity): Boolean {
        if (category.slug == CategoryCatalog.FALLBACK_SLUG) return false
        db.withTransaction {
            val fallbackId = dao.getBySlug(CategoryCatalog.FALLBACK_SLUG)?.id
            if (fallbackId != null) db.itemDao().moveCategory(category.id, fallbackId)
            dao.delete(category)
        }
        return true
    }

    private companion object {
        /** Slugs of categories the user created; the defaults use the slugs of CategoryCatalog. */
        const val CUSTOM_SLUG_PREFIX = "custom_"
    }
}
