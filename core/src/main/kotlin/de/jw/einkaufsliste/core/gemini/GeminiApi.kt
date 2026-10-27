package de.jw.einkaufsliste.core.gemini

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.CategoryDef
import de.jw.einkaufsliste.core.model.RecognizedItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Locale

/** Request/response handling for recognizing a photographed item with the Gemini API. */
object GeminiApi {
    const val DEFAULT_MODEL = "gemini-2.5-flash"
    const val API_KEY_HEADER = "x-goog-api-key"

    /** Name Gemini is told to use when the photo shows no recognizable product. */
    const val UNKNOWN_NAME = "Unbekannt"

    /** Answers below this confidence count as "nothing recognized". */
    const val MIN_CONFIDENCE = 0.2

    const val MESSAGE_NO_ITEM = "Gemini hat keinen Artikel erkannt."
    const val MESSAGE_NOT_RECOGNIZED =
        "Auf dem Foto wurde kein Artikel erkannt. Versuch es mit mehr Licht oder näher dran."
    const val MESSAGE_BLOCKED = "Gemini hat das Foto abgelehnt."
    const val MESSAGE_NO_ANSWER = "Gemini hat nicht geantwortet."
    const val MESSAGE_GARBLED = "Die Antwort von Gemini war unverständlich."

    private val BLOCKING_FINISH_REASONS =
        setOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun endpoint(model: String): String =
        "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"

    /**
     * JSON body for `generateContent`: a German prompt, the image as inline data and a
     * response schema that forces `{name, categorySlug, quantity, confidence}` with
     * `categorySlug` restricted to the slugs of [categories].
     */
    fun buildRequest(imageBase64: String, mimeType: String, categories: List<CategoryDef>): String {
        val slugs = categories.map { it.slug }.distinct()
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt(categories)) }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", mimeType)
                                put("data", imageBase64)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
                putJsonObject("responseSchema") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("name") { put("type", "STRING") }
                        putJsonObject("categorySlug") {
                            put("type", "STRING")
                            if (slugs.isNotEmpty()) putJsonArray("enum") { slugs.forEach { add(it) } }
                        }
                        putJsonObject("quantity") {
                            put("type", "STRING")
                            put("nullable", true)
                        }
                        putJsonObject("confidence") { put("type", "NUMBER") }
                    }
                    putJsonArray("required") {
                        add("name")
                        add("categorySlug")
                    }
                }
            }
        }
        return body.toString()
    }

    /** The German instruction sent with the photo; lists every category as `slug: Name`. */
    fun prompt(categories: List<CategoryDef>): String {
        val lines = mutableListOf(
            "Du hilfst bei einer deutschen Einkaufsliste. Erkenne auf dem Foto den einen Hauptartikel " +
                "(ein Produkt oder Lebensmittel), den man einkaufen würde.",
            "Antworte nur mit einem JSON-Objekt mit diesen Feldern:",
            "- \"name\": kurzer deutscher Name, so wie ihn jemand auf einen Einkaufszettel schreiben würde, " +
                "zum Beispiel \"Vollmilch\", \"Spülmittel\" oder \"AA-Batterien\". Die Marke nur, wenn sie wichtig ist.",
            "- \"categorySlug\": der Slug der am besten passenden Kategorie aus dieser Liste (Format \"slug: Name\"):",
        )
        categories.distinctBy { it.slug }.forEach { lines += "${it.slug}: ${it.name}" }
        lines += "- \"quantity\": auf dem Foto sichtbare Menge oder Größe wie \"1 l\" oder \"500 g\", sonst null."
        lines += "- \"confidence\": Zahl von 0 bis 1, wie sicher du dir bist."
        lines += "Wenn kein Produkt zu erkennen ist, setze \"name\" auf \"$UNKNOWN_NAME\" und \"confidence\" auf 0."
        return lines.joinToString("\n")
    }

    /** Parses a successful answer. Throws [GeminiException] with a German message when nothing usable came back. */
    fun parseResponse(json: String, validSlugs: Set<String>): RecognizedItem {
        val root = parseObject(json) ?: throw GeminiException(MESSAGE_GARBLED)
        val feedback = root["promptFeedback"] as? JsonObject
        if (feedback?.string("blockReason") != null) throw GeminiException(MESSAGE_BLOCKED)
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull() as? JsonObject
            ?: throw GeminiException(MESSAGE_NO_ANSWER)
        val finishReason = candidate.string("finishReason")?.uppercase(Locale.ROOT)
        if (finishReason in BLOCKING_FINISH_REASONS) throw GeminiException(MESSAGE_BLOCKED)
        val parts = ((candidate["content"] as? JsonObject)?.get("parts") as? JsonArray).orEmpty()
        val text = parts.filterIsInstance<JsonObject>()
            .filter { (it["thought"] as? JsonPrimitive)?.booleanOrNull != true }
            .mapNotNull { part -> (part["text"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content }
            .joinToString("")
        if (text.isBlank()) throw GeminiException(MESSAGE_NO_ANSWER)

        val answer = parseAnswerObject(text) ?: throw GeminiException(MESSAGE_GARBLED)
        val name = answer.string("name") ?: throw GeminiException(MESSAGE_NO_ITEM)
        val confidence = answer.number("confidence")?.let(::normalizeConfidence)
        if (name.equals(UNKNOWN_NAME, ignoreCase = true) || (confidence != null && confidence < MIN_CONFIDENCE)) {
            throw GeminiException(MESSAGE_NOT_RECOGNIZED)
        }
        val slug = answer.string("categorySlug")
        return RecognizedItem(
            name = name,
            categorySlug = slug?.takeIf { it in validSlugs } ?: CategoryCatalog.FALLBACK_SLUG,
            quantity = answer.string("quantity")?.takeUnless { it.equals("null", ignoreCase = true) },
            confidence = confidence,
        )
    }

    /** German user-facing message for an HTTP error answer (bad key, quota, unknown model, ...). */
    fun errorMessage(httpCode: Int, body: String): String {
        val error = parseObject(body)?.get("error") as? JsonObject
        val status = error?.string("status")?.uppercase(Locale.ROOT)
        val message = error?.string("message").orEmpty()
        val reasons = (error?.get("details") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.string("reason")?.uppercase(Locale.ROOT) }
        val keyInvalid = "API_KEY_INVALID" in reasons ||
            (httpCode == 400 && message.contains("API key not valid", ignoreCase = true))
        return when {
            keyInvalid -> "Der API-Schlüssel ist ungültig. Bitte in den Einstellungen prüfen."
            httpCode == 401 || httpCode == 403 -> "Der API-Schlüssel hat keinen Zugriff auf Gemini."
            httpCode == 404 -> "Dieses Gemini-Modell gibt es nicht. Bitte den Modellnamen in den Einstellungen prüfen."
            httpCode == 429 -> "Zu viele Anfragen oder Kontingent aufgebraucht. Bitte später nochmal versuchen."
            httpCode == 400 && status == "FAILED_PRECONDITION" ->
                "Gemini ist für diesen Schlüssel oder dieses Land nicht verfügbar."
            httpCode in 500..599 -> "Gemini ist gerade nicht erreichbar. Bitte später nochmal versuchen."
            else -> "Fehler bei Gemini (HTTP $httpCode)."
        }
    }

    private fun parseObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject

    /** The model's JSON answer: markdown fences and text around the object are tolerated, as is a one-element array. */
    private fun parseAnswerObject(text: String): JsonObject? {
        val unfenced = stripCodeFences(text)
        val element = runCatching { json.parseToJsonElement(unfenced) }.getOrNull()
            ?: run {
                val start = unfenced.indexOf('{')
                val end = unfenced.lastIndexOf('}')
                if (start < 0 || end <= start) return null
                runCatching { json.parseToJsonElement(unfenced.substring(start, end + 1)) }.getOrNull()
            }
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstOrNull() as? JsonObject
            else -> null
        }
    }

    private fun stripCodeFences(text: String): String {
        var result = text.trim()
        if (result.startsWith("```")) {
            val firstLineEnd = result.indexOf('\n')
            result = if (firstLineEnd >= 0) result.substring(firstLineEnd + 1) else result.removePrefix("```")
            result = result.trimEnd()
            if (result.endsWith("```")) result = result.removeSuffix("```")
        }
        return result.trim()
    }

    /** Accepts 0..1; values up to 100 are read as percent. */
    private fun normalizeConfidence(value: Double): Double = when {
        value.isNaN() -> 0.0
        value > 1.0 && value <= 100.0 -> value / 100.0
        else -> value.coerceIn(0.0, 1.0)
    }

    /** Trimmed text; null when missing, JSON null, not a primitive or blank. */
    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.trim().takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.number(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.doubleOrNull ?: primitive.content.trim().replace(',', '.').toDoubleOrNull()
    }
}

class GeminiException(message: String) : Exception(message)
