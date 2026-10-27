package de.jw.einkaufsliste.core.backup

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.CategoryDef
import de.jw.einkaufsliste.core.text.ItemNames

/** An item already on the list, as the import sees it. [categorySlug] null means "Sonstiges". */
data class ExistingItem(val name: String, val categorySlug: String?, val done: Boolean)

/**
 * What an import adds to the list.
 *
 * [newCategories] get sort orders after the existing ones (in the file's order).
 * [newItems] have [BackupItem.categorySlug] resolved to a category that exists once
 * [newCategories] are created ("Sonstiges" for unknown or missing slugs).
 */
data class ImportPlan(
    val newCategories: List<CategoryDef>,
    val newItems: List<BackupItem>,
    val skippedDuplicates: Int,
) {
    val result: ImportResult
        get() = ImportResult(addedItems = newItems.size, addedCategories = newCategories.size, skippedDuplicates = skippedDuplicates)
}

/** Outcome of an import, for the snackbar. */
data class ImportResult(val addedItems: Int, val addedCategories: Int, val skippedDuplicates: Int) {
    /** German summary, e.g. "12 Artikel und 2 Unterlisten importiert, 3 schon vorhanden." */
    val message: String
        get() {
            val items = "$addedItems Artikel"
            val categories = if (addedCategories == 1) "1 Unterliste" else "$addedCategories Unterlisten"
            val added = when {
                addedItems > 0 && addedCategories > 0 -> "$items und $categories importiert"
                addedItems > 0 -> "$items importiert"
                addedCategories > 0 -> "$categories importiert"
                skippedDuplicates > 0 -> "Nichts Neues importiert"
                else -> return "Die Sicherung enthält keine Artikel."
            }
            return if (skippedDuplicates > 0) "$added, $skippedDuplicates schon vorhanden." else "$added."
        }
}

/**
 * Merges a backup into the current list without changing or deleting anything:
 *
 * - Categories are matched by slug. Missing ones are created with the slug from the file
 *   (custom `custom_<uuid>` slugs included); existing ones are left as they are.
 * - An open item from the file is skipped when an open item with the same normalized name
 *   ([ItemNames.normalize]) and category is already on the list.
 * - A ticked-off item from the file is skipped when the list already has that item, open
 *   or ticked off; otherwise it is added as ticked off. Importing the same file twice
 *   therefore adds nothing the second time.
 *
 * Only items that were on the list before the import count as duplicates, so a file that
 * lists the same item twice restores both.
 */
object BackupMerge {
    fun plan(backup: ListBackup, existingCategories: List<CategoryDef>, existingItems: List<ExistingItem>): ImportPlan {
        val existingSlugs = existingCategories.mapTo(HashSet()) { it.slug }
        var nextSortOrder = (existingCategories.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val newCategories = backup.categories
            .distinctBy { it.slug }
            .filter { it.slug !in existingSlugs }
            .sortedBy { it.sortOrder }
            .map { it.copy(sortOrder = nextSortOrder++) }
        val knownSlugs = existingSlugs + newCategories.map { it.slug }

        val openKeys = HashSet<String>()
        val allKeys = HashSet<String>()
        existingItems.forEach { item ->
            val key = key(item.name, item.categorySlug ?: CategoryCatalog.FALLBACK_SLUG)
            allKeys += key
            if (!item.done) openKeys += key
        }

        var skipped = 0
        val newItems = backup.items.mapNotNull { item ->
            val slug = item.categorySlug?.takeIf { it in knownSlugs } ?: CategoryCatalog.FALLBACK_SLUG
            val key = key(item.name, slug)
            if (key in (if (item.done) allKeys else openKeys)) {
                skipped++
                null
            } else {
                item.copy(categorySlug = slug)
            }
        }
        return ImportPlan(newCategories = newCategories, newItems = newItems, skippedDuplicates = skipped)
    }

    private fun key(name: String, slug: String): String = slug + "\u0000" + ItemNames.normalize(name)
}
