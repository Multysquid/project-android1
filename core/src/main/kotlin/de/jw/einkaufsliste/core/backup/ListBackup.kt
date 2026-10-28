package de.jw.einkaufsliste.core.backup

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.CategoryDef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate

/** One shopping-list item as saved in a backup file. */
data class BackupItem(
    val name: String,
    /** Category slug; null (or unknown on import) means "Sonstiges". */
    val categorySlug: String? = null,
    val quantity: String? = null,
    val note: String? = null,
    val barcode: String? = null,
    val done: Boolean = false,
    /** Null when the file does not say; the import then uses the import time. */
    val createdAt: Long? = null,
    /** When it was ticked off; only meaningful for [done] items. */
    val doneAt: Long? = null,
    /** OSM id (`node/123`) of the shop the item is pinned to. */
    val pinnedShopId: String? = null,
)

/**
 * The shopping list as saved to a file ("Liste sichern"): the categories ("Unterlisten")
 * and the items. The shop cache, purchase history, settings and the Gemini key are not
 * part of it.
 *
 * Format version 1 is a JSON object
 * `{"format":"einkaufsliste","version":1,"exportedAt":<millis>,"categories":[...],"items":[...]}`
 * with categories `{slug,name,emoji,osmTags:[...],sortOrder}` and items
 * `{name,categorySlug,quantity,note,barcode,done,createdAt,doneAt,pinnedShopId}`.
 * Reading is tolerant: unknown fields are ignored, missing optional fields become null
 * and entries without a name are skipped.
 */
data class ListBackup(
    /** Null when the file does not say. */
    val exportedAt: Long?,
    val categories: List<CategoryDef>,
    val items: List<BackupItem>,
) {
    /** This backup as pretty-printed JSON (format version [VERSION]). */
    fun toJson(): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", exportedAt)
            putJsonArray("categories") {
                categories.forEach { category ->
                    addJsonObject {
                        put("slug", category.slug)
                        put("name", category.name)
                        put("emoji", category.emoji)
                        putJsonArray("osmTags") { category.osmTags.forEach { add(JsonPrimitive(it)) } }
                        put("sortOrder", category.sortOrder)
                    }
                }
            }
            putJsonArray("items") {
                items.forEach { item ->
                    addJsonObject {
                        put("name", item.name)
                        put("categorySlug", item.categorySlug)
                        put("quantity", item.quantity)
                        put("note", item.note)
                        put("barcode", item.barcode)
                        put("done", item.done)
                        put("createdAt", item.createdAt)
                        put("doneAt", item.doneAt)
                        put("pinnedShopId", item.pinnedShopId)
                    }
                }
            }
        }
        return prettyJson.encodeToString(JsonElement.serializer(), root)
    }

    companion object {
        /** Value of the `format` field; tells a backup apart from any other JSON file. */
        const val FORMAT = "einkaufsliste"

        /** The format version this app writes and the newest it can read. */
        const val VERSION = 1

        const val MESSAGE_NOT_A_BACKUP = "Die Datei ist keine Sicherung der Einkaufsliste."
        const val MESSAGE_NEWER_VERSION =
            "Die Sicherung stammt aus einer neueren Version der App. Bitte aktualisiere die App und versuch es dann nochmal."

        /** Emoji for a category whose emoji is missing in the file. */
        private const val DEFAULT_EMOJI = "🛒"

        private val prettyJson = Json { prettyPrint = true }

        /** Suggested file name for an export on [date]: `einkaufsliste-2026-10-19.json`. */
        fun fileName(date: LocalDate): String = "einkaufsliste-$date.json"

        /**
         * Reads a backup. Throws [BackupFormatException] with a German message when [json]
         * is not a backup of this app or was written by a newer version.
         */
        fun fromJson(json: String): ListBackup {
            val root = try {
                Json.parseToJsonElement(json.removePrefix("﻿"))
            } catch (e: IllegalArgumentException) {
                // SerializationException (malformed JSON) is an IllegalArgumentException.
                throw BackupFormatException(MESSAGE_NOT_A_BACKUP, e)
            }
            if (root !is JsonObject || root.string("format") != FORMAT) {
                throw BackupFormatException(MESSAGE_NOT_A_BACKUP)
            }
            val version = root.primitive("version")?.intOrNull ?: throw BackupFormatException(MESSAGE_NOT_A_BACKUP)
            if (version > VERSION) throw BackupFormatException(MESSAGE_NEWER_VERSION)
            if (version < 1) throw BackupFormatException(MESSAGE_NOT_A_BACKUP)

            val categories = root.objects("categories").mapIndexedNotNull { index, obj -> parseCategory(obj, index) }
            val items = root.objects("items").mapNotNull(::parseItem)
            return ListBackup(exportedAt = root.primitive("exportedAt")?.longOrNull, categories = categories, items = items)
        }

        private fun parseCategory(obj: JsonObject, index: Int): CategoryDef? {
            val slug = obj.string("slug") ?: return null
            val default = CategoryCatalog.bySlug(slug)
            val name = obj.string("name") ?: default?.name ?: return null
            return CategoryDef(
                slug = slug,
                name = name,
                emoji = obj.string("emoji") ?: default?.emoji ?: DEFAULT_EMOJI,
                osmTags = parseTags(obj["osmTags"]),
                sortOrder = obj.primitive("sortOrder")?.intOrNull ?: index,
            )
        }

        private fun parseItem(obj: JsonObject): BackupItem? {
            val name = obj.string("name") ?: return null
            return BackupItem(
                name = name,
                categorySlug = obj.string("categorySlug"),
                quantity = obj.string("quantity"),
                note = obj.string("note"),
                barcode = obj.string("barcode"),
                done = obj.primitive("done")?.booleanOrNull ?: false,
                createdAt = obj.primitive("createdAt")?.longOrNull,
                doneAt = obj.primitive("doneAt")?.longOrNull,
                pinnedShopId = obj.string("pinnedShopId"),
            )
        }

        /** A JSON array of tags; a comma-separated string is accepted too. */
        private fun parseTags(element: JsonElement?): List<String> {
            val raw = when (element) {
                is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }
                is JsonPrimitive -> if (element is JsonNull) emptyList() else element.content.split(',')
                else -> emptyList()
            }
            return raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

        /** Trimmed text of a string (or number) field; null when missing, null or blank. */
        private fun JsonObject.string(key: String): String? =
            primitive(key)?.content?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject.objects(key: String): List<JsonObject> =
            (this[key] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
    }
}

/** The file is not a backup this app can read; [message] is German and meant for the user. */
class BackupFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)
