package de.jw.einkaufsliste.core.backup

import de.jw.einkaufsliste.core.model.CategoryDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class ListBackupTest {
    private val sweets = CategoryDef(
        slug = "suesses_snacks",
        name = "Süßes & Snacks",
        emoji = "🍫",
        osmTags = listOf("shop=supermarket", "shop=confectionery"),
        sortOrder = 7,
    )
    private val custom = CategoryDef(
        slug = "custom_0b7f5c1e-2f0a-4c55-9d1f-6b1d2b9e8a10",
        name = "Geschenke für Oma",
        emoji = "🎁",
        osmTags = emptyList(),
        sortOrder = 23,
    )
    private val milk = BackupItem(
        name = "Milch 1,5 %",
        categorySlug = "milch_eier",
        quantity = "2 l",
        note = "laktosefrei, \"die blaue\"\nnicht die rote \\ grüne",
        barcode = "4008400401621",
        done = false,
        createdAt = 1_760_000_000_000,
        doneAt = null,
        pinnedShopId = "node/123456",
    )
    private val chocolate = BackupItem(
        name = "Schokolade 🍫 Zartbitter",
        categorySlug = custom.slug,
        done = true,
        createdAt = 1_759_000_000_000,
        doneAt = 1_759_500_000_000,
    )
    private val backup = ListBackup(
        exportedAt = 1_760_870_400_000,
        categories = listOf(sweets, custom),
        items = listOf(milk, chocolate, BackupItem(name = "Brötchen")),
    )

    // --- Writing --------------------------------------------------------------------------------

    @Test
    fun roundTripKeepsEverything() {
        assertEquals(backup, ListBackup.fromJson(backup.toJson()))
    }

    @Test
    fun emptyBackupRoundTrips() {
        val empty = ListBackup(exportedAt = 0, categories = emptyList(), items = emptyList())
        assertEquals(empty, ListBackup.fromJson(empty.toJson()))
    }

    @Test
    fun writesVersionOneFormat() {
        val root = Json.parseToJsonElement(backup.toJson()).jsonObject
        assertEquals(setOf("format", "version", "exportedAt", "categories", "items"), root.keys)
        assertEquals("einkaufsliste", root.getValue("format").jsonPrimitive.content)
        assertEquals(1, root.getValue("version").jsonPrimitive.int)
        assertEquals(1_760_870_400_000, root.getValue("exportedAt").jsonPrimitive.long)

        val category = root.getValue("categories").jsonArray[0].jsonObject
        assertEquals(setOf("slug", "name", "emoji", "osmTags", "sortOrder"), category.keys)
        assertEquals("suesses_snacks", category.getValue("slug").jsonPrimitive.content)
        assertEquals(
            listOf("shop=supermarket", "shop=confectionery"),
            category.getValue("osmTags").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(7, category.getValue("sortOrder").jsonPrimitive.int)

        val items = root.getValue("items").jsonArray
        assertEquals(3, items.size)
        val first = items[0].jsonObject
        assertEquals(
            setOf("name", "categorySlug", "quantity", "note", "barcode", "done", "createdAt", "doneAt", "pinnedShopId"),
            first.keys,
        )
        assertFalse(first.getValue("done").jsonPrimitive.boolean)
        assertEquals("node/123456", first.getValue("pinnedShopId").jsonPrimitive.content)
        val minimal = items[2].jsonObject
        assertSame(JsonNull, minimal["categorySlug"])
        assertSame(JsonNull, minimal["createdAt"])
    }

    @Test
    fun umlautsAndEmojiAreWrittenAsTheyAre() {
        val json = backup.toJson()
        assertTrue(json.contains("Süßes & Snacks"))
        assertTrue(json.contains("🍫"))
        assertTrue(json.contains("Geschenke für Oma"))
        assertTrue(json.contains("Brötchen"))
        val read = ListBackup.fromJson(json)
        assertEquals("Schokolade 🍫 Zartbitter", read.items[1].name)
        assertEquals("🎁", read.categories[1].emoji)
    }

    @Test
    fun quotesBackslashesAndLineBreaksSurvive() {
        val note = ListBackup.fromJson(backup.toJson()).items[0].note
        assertEquals("laktosefrei, \"die blaue\"\nnicht die rote \\ grüne", note)
    }

    @Test
    fun fileNameContainsTheDate() {
        assertEquals("einkaufsliste-2026-10-19.json", ListBackup.fileName(LocalDate.of(2026, 10, 19)))
        assertEquals("einkaufsliste-2027-01-05.json", ListBackup.fileName(LocalDate.of(2027, 1, 5)))
    }

    // --- Reading: tolerance ---------------------------------------------------------------------

    @Test
    fun missingOptionalFieldsBecomeNull() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,"items":[{"name":"Butter"}],"categories":[{"slug":"custom_x","name":"Bastelzeug"}]}""",
        )
        assertNull(read.exportedAt)
        assertEquals(listOf(BackupItem(name = "Butter")), read.items)
        val item = read.items.single()
        assertNull(item.categorySlug)
        assertNull(item.quantity)
        assertNull(item.note)
        assertNull(item.barcode)
        assertFalse(item.done)
        assertNull(item.createdAt)
        assertNull(item.doneAt)
        assertNull(item.pinnedShopId)
        assertEquals(CategoryDef("custom_x", "Bastelzeug", "🛒", emptyList(), 0), read.categories.single())
    }

    @Test
    fun missingListsAreEmpty() {
        val read = ListBackup.fromJson("""{"format":"einkaufsliste","version":1}""")
        assertEquals(ListBackup(exportedAt = null, categories = emptyList(), items = emptyList()), read)
    }

    @Test
    fun explicitNullsAreAccepted() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,"exportedAt":null,"categories":null,
               "items":[{"name":"Eier","categorySlug":null,"quantity":null,"note":null,"barcode":null,
                         "done":null,"createdAt":null,"doneAt":null,"pinnedShopId":null}]}""",
        )
        assertNull(read.exportedAt)
        assertEquals(emptyList<CategoryDef>(), read.categories)
        assertEquals(listOf(BackupItem(name = "Eier")), read.items)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,"app":"x","shops":[{"id":"node/1"}],
               "categories":[{"slug":"getraenke","name":"Getränke","emoji":"🥤","osmTags":[],"sortOrder":3,"color":"#ff0000"}],
               "items":[{"name":"Wasser","categorySlug":"getraenke","source":"BARCODE","id":42}]}""",
        )
        assertEquals("Getränke", read.categories.single().name)
        assertEquals(BackupItem(name = "Wasser", categorySlug = "getraenke"), read.items.single())
    }

    @Test
    fun defaultCategoryFallsBackToCatalogNameAndEmoji() {
        val read = ListBackup.fromJson("""{"format":"einkaufsliste","version":1,"categories":[{"slug":"fisch"}]}""")
        val fish = read.categories.single()
        assertEquals("Fisch", fish.name)
        assertEquals("🐟", fish.emoji)
    }

    @Test
    fun entriesWithoutNameOrSlugAndNonObjectsAreSkipped() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,
               "categories":[{"name":"Ohne Slug"},{"slug":"custom_y"},{"slug":"  ","name":"Leer"},"text",1,
                             {"slug":"custom_z","name":"Gut"}],
               "items":[{"name":"   "},{"quantity":"2"},null,["Milch"],"Milch",{"name":" Käse  "}]}""",
        )
        assertEquals(listOf("custom_z"), read.categories.map { it.slug })
        // Without a sortOrder, the position among the category objects is used.
        assertEquals(3, read.categories.single().sortOrder)
        assertEquals(listOf(BackupItem(name = "Käse")), read.items)
    }

    @Test
    fun blankTextBecomesNullAndTextIsTrimmed() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,
               "items":[{"name":"Tee","quantity":"  ","note":"","barcode":" 4001234567890 ","pinnedShopId":" way/7 "}]}""",
        )
        assertEquals(BackupItem(name = "Tee", barcode = "4001234567890", pinnedShopId = "way/7"), read.items.single())
    }

    @Test
    fun looseTypesAreAccepted() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":"1","exportedAt":"1760870400000",
               "categories":[{"slug":"custom_q","name":"Q","osmTags":"shop=bakery, shop=farm,,shop=bakery","sortOrder":"4"}],
               "items":[{"name":"Mehl","quantity":2,"done":"true","createdAt":"1700000000000"}]}""",
        )
        assertEquals(1_760_870_400_000, read.exportedAt)
        val category = read.categories.single()
        assertEquals(listOf("shop=bakery", "shop=farm"), category.osmTags)
        assertEquals(4, category.sortOrder)
        assertEquals(BackupItem(name = "Mehl", quantity = "2", done = true, createdAt = 1_700_000_000_000), read.items.single())
    }

    @Test
    fun wrongFieldTypesFallBackToDefaults() {
        val read = ListBackup.fromJson(
            """{"format":"einkaufsliste","version":1,"exportedAt":"gestern","categories":{"slug":"x"},
               "items":[{"name":"Salz","done":"vielleicht","createdAt":"bald","note":{"a":1},"categorySlug":["x"]}]}""",
        )
        assertNull(read.exportedAt)
        assertEquals(emptyList<CategoryDef>(), read.categories)
        assertEquals(BackupItem(name = "Salz"), read.items.single())
    }

    @Test
    fun byteOrderMarkIsIgnored() {
        val read = ListBackup.fromJson("﻿" + backup.toJson())
        assertEquals(backup, read)
    }

    // --- Reading: rejected files ----------------------------------------------------------------

    @Test
    fun rejectsFilesThatAreNotABackup() {
        val notBackups = listOf(
            "",
            "   ",
            "Milch, Eier, Brot",
            "{\"format\":\"einkaufsliste\",\"version\":1",
            "[]",
            "[{\"format\":\"einkaufsliste\",\"version\":1}]",
            "\"einkaufsliste\"",
            "null",
            "{}",
            """{"items":[{"name":"Milch"}]}""",
            """{"format":"andere-app","version":1,"items":[]}""",
            """{"format":"Einkaufsliste","version":1}""",
            """{"format":"einkaufsliste"}""",
            """{"format":"einkaufsliste","version":null}""",
            """{"format":"einkaufsliste","version":"eins"}""",
            """{"format":"einkaufsliste","version":0}""",
            """{"format":"einkaufsliste","version":-3}""",
            """{"format":{"name":"einkaufsliste"},"version":1}""",
        )
        notBackups.forEach { json -> assertRejected(json, ListBackup.MESSAGE_NOT_A_BACKUP) }
    }

    @Test
    fun rejectsNewerVersions() {
        assertRejected("""{"format":"einkaufsliste","version":2,"items":[{"name":"Milch"}]}""", ListBackup.MESSAGE_NEWER_VERSION)
        assertRejected("""{"format":"einkaufsliste","version":17}""", ListBackup.MESSAGE_NEWER_VERSION)
    }

    @Test
    fun messagesAreGerman() {
        assertEquals("Die Datei ist keine Sicherung der Einkaufsliste.", ListBackup.MESSAGE_NOT_A_BACKUP)
        assertTrue(ListBackup.MESSAGE_NEWER_VERSION.startsWith("Die Sicherung stammt aus einer neueren Version der App."))
    }

    private fun assertRejected(json: String, message: String) {
        try {
            ListBackup.fromJson(json)
            fail("Expected BackupFormatException for: $json")
        } catch (e: BackupFormatException) {
            assertEquals("for: $json", message, e.message)
        }
    }
}
