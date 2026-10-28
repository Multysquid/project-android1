package de.jw.einkaufsliste.core.backup

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.CategoryDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {
    private val defaults = CategoryCatalog.defaults
    private val maxDefaultOrder = defaults.maxOf { it.sortOrder }

    private fun category(slug: String, name: String, sortOrder: Int) =
        CategoryDef(slug = slug, name = name, emoji = "🎁", osmTags = listOf("shop=gift"), sortOrder = sortOrder)

    private fun backup(categories: List<CategoryDef> = emptyList(), vararg items: BackupItem) =
        ListBackup(exportedAt = 1L, categories = categories, items = items.toList())

    // --- Categories -----------------------------------------------------------------------------

    @Test
    fun createsMissingCategoriesWithTheirSlugsAfterTheExistingOnes() {
        val gifts = category("custom_aaa", "Geschenke", 30)
        val crafts = category("custom_bbb", "Basteln", 2)
        val changedDefault = defaults.first { it.slug == "fisch" }.copy(name = "Fisch & Meer", sortOrder = 0)
        val plan = BackupMerge.plan(backup(listOf(gifts, changedDefault, crafts, gifts.copy(name = "Doppelt"))), defaults, emptyList())

        // Existing slugs are left alone (no rename), the rest follow the file's order after the existing ones.
        assertEquals(
            listOf(crafts.copy(sortOrder = maxDefaultOrder + 1), gifts.copy(sortOrder = maxDefaultOrder + 2)),
            plan.newCategories,
        )
    }

    @Test
    fun recreatesDeletedDefaultCategory() {
        val withoutFish = defaults.filter { it.slug != "fisch" }
        val fish = defaults.first { it.slug == "fisch" }
        val plan = BackupMerge.plan(backup(defaults), withoutFish, emptyList())
        assertEquals(listOf(fish.copy(sortOrder = maxDefaultOrder + 1)), plan.newCategories)
    }

    @Test
    fun firstCategoryInEmptyDatabaseGetsSortOrderZero() {
        val plan = BackupMerge.plan(backup(listOf(category("custom_a", "A", 9))), emptyList(), emptyList())
        assertEquals(0, plan.newCategories.single().sortOrder)
    }

    @Test
    fun itemsKeepKnownAndNewCategoriesAndFallBackToSonstiges() {
        val gifts = category("custom_aaa", "Geschenke", 30)
        val plan = BackupMerge.plan(
            backup(
                listOf(gifts),
                BackupItem(name = "Milch", categorySlug = "milch_eier"),
                BackupItem(name = "Kerze", categorySlug = "custom_aaa"),
                BackupItem(name = "Ding", categorySlug = "custom_nicht_in_der_datei"),
                BackupItem(name = "Irgendwas", categorySlug = null),
            ),
            defaults,
            emptyList(),
        )
        assertEquals(
            listOf("milch_eier", "custom_aaa", CategoryCatalog.FALLBACK_SLUG, CategoryCatalog.FALLBACK_SLUG),
            plan.newItems.map { it.categorySlug },
        )
        assertEquals(0, plan.skippedDuplicates)
    }

    // --- Items ----------------------------------------------------------------------------------

    @Test
    fun skipsOpenItemsAlreadyOpenWithSameNameAndCategory() {
        val existing = listOf(
            ExistingItem("Milch", "milch_eier", done = false),
            ExistingItem("Brot", "brot_backwaren", done = true),
        )
        val plan = BackupMerge.plan(
            backup(
                emptyList(),
                BackupItem(name = "  MILCH ", categorySlug = "milch_eier"),
                BackupItem(name = "Milch", categorySlug = "getraenke"),
                BackupItem(name = "Brot", categorySlug = "brot_backwaren"),
                BackupItem(name = "Käse", categorySlug = "milch_eier"),
            ),
            defaults,
            existing,
        )
        // Same name in another category, and an item that is only ticked off, are added.
        assertEquals(
            listOf("Milch" to "getraenke", "Brot" to "brot_backwaren", "Käse" to "milch_eier"),
            plan.newItems.map { it.name to it.categorySlug },
        )
        assertEquals(1, plan.skippedDuplicates)
    }

    @Test
    fun normalizesWhitespaceAndCaseIncludingUmlauts() {
        val existing = listOf(ExistingItem("Äpfel  Boskop", "obst_gemuese", done = false))
        val plan = BackupMerge.plan(
            backup(emptyList(), BackupItem(name = "äpfel boskop", categorySlug = "obst_gemuese")),
            defaults,
            existing,
        )
        assertEquals(emptyList<BackupItem>(), plan.newItems)
        assertEquals(1, plan.skippedDuplicates)
    }

    @Test
    fun existingItemWithoutCategoryCountsAsSonstiges() {
        val existing = listOf(ExistingItem("Kleber", categorySlug = null, done = false))
        val plan = BackupMerge.plan(
            backup(
                emptyList(),
                BackupItem(name = "Kleber", categorySlug = CategoryCatalog.FALLBACK_SLUG),
                BackupItem(name = "kleber", categorySlug = "custom_unbekannt"),
                BackupItem(name = "Kleber", categorySlug = "schreibwaren"),
            ),
            defaults,
            existing,
        )
        assertEquals(listOf("schreibwaren"), plan.newItems.map { it.categorySlug })
        assertEquals(2, plan.skippedDuplicates)
    }

    @Test
    fun doneItemsAreAddedAsDoneUnlessTheListAlreadyHasThem() {
        val existing = listOf(
            ExistingItem("Milch", "milch_eier", done = false),
            ExistingItem("Brot", "brot_backwaren", done = true),
        )
        val plan = BackupMerge.plan(
            backup(
                emptyList(),
                BackupItem(name = "Milch", categorySlug = "milch_eier", done = true, doneAt = 5),
                BackupItem(name = "Brot", categorySlug = "brot_backwaren", done = true, doneAt = 6),
                BackupItem(name = "Butter", categorySlug = "milch_eier", done = true, createdAt = 3, doneAt = 7),
            ),
            defaults,
            existing,
        )
        assertEquals(
            listOf(BackupItem(name = "Butter", categorySlug = "milch_eier", done = true, createdAt = 3, doneAt = 7)),
            plan.newItems,
        )
        assertEquals(2, plan.skippedDuplicates)
    }

    @Test
    fun itemFieldsArePassedThrough() {
        val item = BackupItem(
            name = "Kaffee",
            categorySlug = "kaffee_tee",
            quantity = "500 g",
            note = "ganze Bohnen",
            barcode = "4006067084201",
            done = false,
            createdAt = 1_700_000_000_000,
            pinnedShopId = "node/42",
        )
        val plan = BackupMerge.plan(backup(emptyList(), item), defaults, emptyList())
        assertEquals(listOf(item), plan.newItems)
    }

    @Test
    fun duplicatesWithinTheFileAreAllRestored() {
        val plan = BackupMerge.plan(
            backup(
                emptyList(),
                BackupItem(name = "Milch", categorySlug = "milch_eier"),
                BackupItem(name = "Milch", categorySlug = "milch_eier"),
            ),
            defaults,
            emptyList(),
        )
        assertEquals(2, plan.newItems.size)
    }

    @Test
    fun importingTheSameFileTwiceAddsNothingTheSecondTime() {
        val gifts = category("custom_aaa", "Geschenke", 30)
        val file = backup(
            listOf(gifts),
            BackupItem(name = "Kerze", categorySlug = "custom_aaa"),
            BackupItem(name = "Milch", categorySlug = "milch_eier"),
            BackupItem(name = "Brot", categorySlug = "brot_backwaren", done = true),
            BackupItem(name = "Ding", categorySlug = "weg"),
        )
        val first = BackupMerge.plan(file, defaults, emptyList())
        assertEquals(ImportResult(addedItems = 4, addedCategories = 1, skippedDuplicates = 0), first.result)

        val categoriesAfter = defaults + first.newCategories
        val itemsAfter = first.newItems.map { ExistingItem(it.name, it.categorySlug, it.done) }
        val second = BackupMerge.plan(file, categoriesAfter, itemsAfter)
        assertEquals(ImportResult(addedItems = 0, addedCategories = 0, skippedDuplicates = 4), second.result)
    }

    // --- Result message -------------------------------------------------------------------------

    @Test
    fun resultMessages() {
        assertEquals(
            "12 Artikel und 2 Unterlisten importiert, 3 schon vorhanden.",
            ImportResult(addedItems = 12, addedCategories = 2, skippedDuplicates = 3).message,
        )
        assertEquals("1 Artikel und 1 Unterliste importiert.", ImportResult(1, 1, 0).message)
        assertEquals("5 Artikel importiert.", ImportResult(5, 0, 0).message)
        assertEquals("5 Artikel importiert, 1 schon vorhanden.", ImportResult(5, 0, 1).message)
        assertEquals("2 Unterlisten importiert.", ImportResult(0, 2, 0).message)
        assertEquals("Nichts Neues importiert, 7 schon vorhanden.", ImportResult(0, 0, 7).message)
        assertEquals("Die Sicherung enthält keine Artikel.", ImportResult(0, 0, 0).message)
    }

    @Test
    fun resultCountsMatchThePlan() {
        val plan = BackupMerge.plan(
            backup(listOf(category("custom_x", "X", 0)), BackupItem(name = "A"), BackupItem(name = "B")),
            defaults,
            listOf(ExistingItem("b", CategoryCatalog.FALLBACK_SLUG, done = false)),
        )
        assertEquals(ImportResult(addedItems = 1, addedCategories = 1, skippedDuplicates = 1), plan.result)
        assertTrue(plan.result.message.startsWith("1 Artikel und 1 Unterliste importiert"))
    }
}
