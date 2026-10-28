package de.jw.einkaufsliste.core.osm

import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Locale

/** Builds Overpass API queries for shops and parses the JSON answer. */
object OverpassApi {
    /** Public Overpass endpoints, tried in order. */
    val ENDPOINTS: List<String> = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** `shop=*` values that do not describe a shop that is open for business. */
    private val IGNORED_SHOP_VALUES = setOf("vacant", "disused", "no", "closed")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Overpass QL for all `shop=*` and `amenity=pharmacy` objects within [radiusMeters] of [center], with `out center tags`. */
    fun buildShopsQuery(center: GeoPoint, radiusMeters: Int, timeoutSeconds: Int = 25): String {
        val around = String.format(Locale.ROOT, "(around:%d,%.6f,%.6f)", radiusMeters, center.lat, center.lon)
        return "[out:json][timeout:$timeoutSeconds];" +
            "(nwr[\"shop\"]$around;nwr[\"amenity\"=\"pharmacy\"]$around;);" +
            "out center tags;"
    }

    /**
     * Parses an Overpass JSON answer (nodes, and ways/relations with `center`). Skips elements without coordinates.
     * Returns an empty list for anything that is not an Overpass answer; use [parseOrNull] to tell the two apart.
     */
    fun parse(json: String): List<ShopInfo> = parseOrNull(json).orEmpty()

    /**
     * Like [parse], but null when [json] is not a usable Overpass answer: not JSON, no `elements` array, or a
     * `remark` reporting a runtime error (Overpass answers timeouts and memory errors with HTTP 200 and such a
     * remark, possibly with incomplete elements). Callers should not cache a null result as "no shops here".
     */
    fun parseOrNull(json: String): List<ShopInfo>? {
        val root = runCatching { this.json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
        val remark = root.string("remark")
        if (remark != null && remark.contains("error", ignoreCase = true)) return null
        val elements = root["elements"] as? JsonArray ?: return null
        val seen = HashSet<String>()
        return elements.mapNotNull { element -> (element as? JsonObject)?.let(::toShop) }
            .filter { seen.add(it.id) }
    }

    private fun toShop(element: JsonObject): ShopInfo? {
        val type = element.string("type") ?: return null
        if (type != "node" && type != "way" && type != "relation") return null
        val osmId = (element["id"] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.trim().toLongOrNull() }
            ?: return null
        val tags = element["tags"] as? JsonObject ?: return null
        val tag = shopTag(tags) ?: return null
        val center = element["center"] as? JsonObject
        val point = if (type == "node") {
            coordinates(element) ?: center?.let(::coordinates)
        } else {
            center?.let(::coordinates) ?: coordinates(element)
        } ?: return null
        return ShopInfo(
            id = "$type/$osmId",
            name = tags.string("name") ?: tags.string("name:de"),
            brand = tags.string("brand"),
            lat = point.lat,
            lon = point.lon,
            tag = tag,
            openingHours = tags.string("opening_hours"),
        )
    }

    /**
     * `amenity=pharmacy`, else `shop=<value>` (first value of a `;` list), else null. A pharmacy
     * comes first: in Germany it is always an Apotheke, even when it also carries a shop tag such
     * as `shop=chemist`, and only `amenity=pharmacy` sells medicines.
     */
    private fun shopTag(tags: JsonObject): String? {
        if (tags.string("amenity") == "pharmacy") return "amenity=pharmacy"
        val shop = tags.string("shop")?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() }
        if (shop != null && shop.lowercase(Locale.ROOT) !in IGNORED_SHOP_VALUES) return "shop=$shop"
        return null
    }

    private fun coordinates(obj: JsonObject): GeoPoint? {
        val lat = obj.double("lat") ?: return null
        val lon = obj.double("lon") ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return GeoPoint(lat, lon)
    }

    private fun JsonObject.double(key: String): Double? =
        (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.trim().toDoubleOrNull() }
            ?.takeIf { it.isFinite() }

    /** Trimmed string value; null when missing, JSON null, not a primitive or blank. */
    private fun JsonObject.string(key: String): String? = this[key].stringOrNull()

    private fun JsonElement?.stringOrNull(): String? {
        val primitive = this as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }
}
