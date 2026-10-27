package de.jw.einkaufsliste.feature.list

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.catalog.CategoryGuesser
import de.jw.einkaufsliste.core.text.ItemNames
import de.jw.einkaufsliste.data.db.CategoryEntity
import de.jw.einkaufsliste.data.db.ItemEntity

/** Picks the category the add dialog preselects while the user types a name. */
internal object CategorySuggestion {
    /**
     * The category the same (normalized) name was last added with among [items], so the
     * user's own choice wins over keywords; else the keyword guess for [name] if that
     * category exists; else "Sonstiges". Earlier items in "Sonstiges" are ignored: they
     * carry no choice (e.g. scanned without a known category, or their Unterliste was deleted).
     */
    fun suggest(name: String, categories: List<CategoryEntity>, items: List<ItemEntity>): Long? {
        val sonstigesId = categories.firstOrNull { it.slug == CategoryCatalog.FALLBACK_SLUG }?.id
        val fallback = sonstigesId ?: categories.lastOrNull()?.id
        val normalized = ItemNames.normalize(name)
        if (normalized.isEmpty()) return fallback

        val known = categories.mapTo(HashSet()) { it.id }
        val lastUsed = items
            .filter { it.categoryId in known && it.categoryId != sonstigesId && ItemNames.normalize(it.name) == normalized }
            .maxByOrNull { it.createdAt }
        if (lastUsed != null) return lastUsed.categoryId

        val guessedSlug = CategoryGuesser.guess(name)
        if (guessedSlug != null) {
            categories.firstOrNull { it.slug == guessedSlug }?.let { return it.id }
        }
        return fallback
    }
}
