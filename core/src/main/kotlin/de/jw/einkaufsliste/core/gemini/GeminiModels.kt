package de.jw.einkaufsliste.core.gemini

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.Locale

/**
 * A Gemini model the user can pick: [id] as the API expects it ("gemini-3.8-flash"), the
 * [name] to show and an optional German [hint].
 */
data class GeminiModel(val id: String, val name: String, val hint: String? = null)

/** One page of the API's model list; [nextPageToken] is null on the last page. */
data class GeminiModelPage(val models: List<GeminiModel>, val nextPageToken: String?)

/**
 * The models offered in the settings: Google's list for the user's key, filtered to models
 * that answer text for photos and item names, or [KNOWN] when that list is not available.
 */
object GeminiModels {
    /** Model list endpoint without paging parameters. */
    const val LIST_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    /** Models per page; the API's maximum. */
    const val PAGE_SIZE = 1000

    const val HINT_RECOMMENDED = "Empfohlen: schnell und genau"
    const val HINT_LEGACY = "Älteres Modell, das neue Schlüssel nicht mehr nutzen dürfen"
    const val HINT_PREVIEW = "Vorschau, kann jederzeit wegfallen"
    const val HINT_ALIAS = "Zeigt immer auf das aktuelle Modell dieser Art"
    const val HINT_CUSTOM = "Selbst eingetragen"

    /**
     * Offered when Google's list cannot be loaded (no key yet, offline). Checked against
     * Google's model and deprecation pages in October 2026; [GeminiApi.DEFAULT_MODEL] first.
     */
    val KNOWN: List<GeminiModel> = listOf(
        GeminiModel(GeminiApi.DEFAULT_MODEL, "Gemini 3.8 Flash", HINT_RECOMMENDED),
        GeminiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Am schnellsten und günstigsten"),
        GeminiModel("gemini-4-argon", "Gemini 4 Argon", "Das stärkste Modell, aber langsamer und teurer"),
        GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash", HINT_LEGACY),
    )

    /**
     * Name parts of models that cannot answer a photo or item name with text: speech, live
     * audio, image or video generation, embeddings, robotics and computer use.
     */
    private val EXCLUDED_PARTS = setOf(
        "tts", "live", "audio", "image", "embedding", "transcribe", "translate", "robotics", "computer", "banana", "omni",
    )

    private val PREVIEW_PARTS = setOf("preview", "exp", "experimental")

    private val VERSION = Regex("""^gemini-(\d+)(?:\.(\d+))?(?:-|$)""")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** The model id for a setting: accepts the API's own "models/gemini-3.8-flash"; blank gives the default. */
    fun normalizeId(setting: String): String =
        setting.trim().removePrefix("models/").trim().ifEmpty { GeminiApi.DEFAULT_MODEL }

    /** URL of one page of the model list; [pageToken] is the previous page's [GeminiModelPage.nextPageToken]. */
    fun listUrl(pageToken: String? = null): String {
        val token = pageToken?.takeIf { it.isNotBlank() }?.let { "&pageToken=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return "$LIST_URL?pageSize=$PAGE_SIZE$token"
    }

    /**
     * Parses one page of `models.list`, keeping only [isPickable] models. Throws
     * [GeminiException] when the answer is not a model list.
     */
    fun parsePage(responseJson: String): GeminiModelPage {
        val root = runCatching { json.parseToJsonElement(responseJson) }.getOrNull() as? JsonObject
            ?: throw GeminiException(GeminiApi.MESSAGE_GARBLED)
        val entries = root["models"] as? JsonArray ?: JsonArray(emptyList())
        val models = entries.filterIsInstance<JsonObject>().mapNotNull { entry ->
            val id = entry.string("name")?.let { it.trim().removePrefix("models/") } ?: return@mapNotNull null
            val methods = (entry["supportedGenerationMethods"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            if (!isPickable(id, methods)) return@mapNotNull null
            GeminiModel(id = id, name = entry.string("displayName")?.takeIf { it.isNotBlank() } ?: id)
        }
        return GeminiModelPage(models, root.string("nextPageToken")?.takeIf { it.isNotBlank() })
    }

    /** A Gemini model that `generateContent` can ask for text: no speech, live, image or embedding model. */
    fun isPickable(id: String, methods: Collection<String>): Boolean {
        val normalized = id.lowercase(Locale.ROOT)
        if (!normalized.startsWith("gemini-")) return false
        if (methods.none { it.equals("generateContent", ignoreCase = true) }) return false
        return normalized.split('-').none { it in EXCLUDED_PARTS }
    }

    /**
     * Whether a request to [model] may set its own temperature. Google asks not to change the
     * sampling defaults from Gemini 3 on, so only the older 1.x and 2.x models get one.
     */
    fun acceptsTemperature(model: String): Boolean {
        val major = VERSION.find(normalizeId(model).lowercase(Locale.ROOT))?.groupValues?.get(1)?.toIntOrNull()
        return major != null && major <= 2
    }

    /**
     * The entries of the model picker: [fromGoogle] when it has any, else [KNOWN], each id
     * once, with German hints; [selected] is added when it is missing. The default model
     * comes first, then the newest versions, stable before preview; aliases such as
     * "gemini-flash-latest" and custom names come last.
     */
    fun choices(fromGoogle: List<GeminiModel>?, selected: String): List<GeminiModel> {
        val known = KNOWN.associateBy { it.id }
        val base = fromGoogle?.takeIf { it.isNotEmpty() } ?: KNOWN
        val models = base.distinctBy { it.id }.map { model ->
            val hint = model.hint ?: known[model.id]?.hint ?: ruleHint(model.id)
            model.copy(hint = hint)
        }
        val selectedId = normalizeId(selected)
        val all = if (models.any { it.id == selectedId }) {
            models
        } else {
            models + (known[selectedId] ?: GeminiModel(selectedId, selectedId, ruleHint(selectedId) ?: HINT_CUSTOM))
        }
        return all.sortedWith(PICKER_ORDER)
    }

    /** The picker entry for [selected] among [choices], or a custom entry. */
    fun find(choices: List<GeminiModel>, selected: String): GeminiModel {
        val id = normalizeId(selected)
        return choices.firstOrNull { it.id == id } ?: GeminiModel(id, id, HINT_CUSTOM)
    }

    private fun ruleHint(id: String): String? {
        val parts = id.lowercase(Locale.ROOT).split('-')
        return when {
            acceptsTemperature(id) -> HINT_LEGACY
            parts.any { it in PREVIEW_PARTS } -> HINT_PREVIEW
            parts.lastOrNull() == "latest" -> HINT_ALIAS
            else -> null
        }
    }

    /** (major, minor) of a versioned id like "gemini-3.8-flash", else null. */
    private fun version(id: String): Pair<Int, Int>? {
        val match = VERSION.find(id.lowercase(Locale.ROOT)) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        return major to (match.groupValues[2].toIntOrNull() ?: 0)
    }

    private fun isPreview(id: String): Boolean = id.lowercase(Locale.ROOT).split('-').any { it in PREVIEW_PARTS }

    private val PICKER_ORDER: Comparator<GeminiModel> =
        compareBy<GeminiModel> { it.id != GeminiApi.DEFAULT_MODEL }
            .thenBy { version(it.id) == null }
            .thenByDescending { version(it.id)?.first ?: 0 }
            .thenByDescending { version(it.id)?.second ?: 0 }
            .thenBy { isPreview(it.id) }
            .thenBy { it.id }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
}
