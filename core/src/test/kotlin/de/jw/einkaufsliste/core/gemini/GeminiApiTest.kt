package de.jw.einkaufsliste.core.gemini

import de.jw.einkaufsliste.core.catalog.CategoryCatalog
import de.jw.einkaufsliste.core.model.CategoryDef
import de.jw.einkaufsliste.core.model.RecognizedItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiApiTest {
    private val slugs = CategoryCatalog.slugs

    // --- Request ----------------------------------------------------------------------------------

    @Test
    fun endpointUsesTrimmedModel() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent",
            GeminiApi.endpoint(" gemini-2.5-flash "),
        )
    }

    @Test
    fun requestHasPromptImageAndSchema() {
        val body = Json.parseToJsonElement(GeminiApi.buildRequest("aGVsbG8+/w==", "image/jpeg", CategoryCatalog.defaults))
            .jsonObject
        assertEquals(setOf("contents", "generationConfig"), body.keys)

        val content = body.getValue("contents").jsonArray.single().jsonObject
        assertEquals("user", content.getValue("role").jsonPrimitive.content)
        val parts = content.getValue("parts").jsonArray
        assertEquals(2, parts.size)
        val prompt = parts[0].jsonObject.getValue("text").jsonPrimitive.content
        assertEquals(GeminiApi.prompt(CategoryCatalog.defaults), prompt)
        val inline = parts[1].jsonObject.getValue("inlineData").jsonObject
        assertEquals("image/jpeg", inline.getValue("mimeType").jsonPrimitive.content)
        assertEquals("aGVsbG8+/w==", inline.getValue("data").jsonPrimitive.content)

        val config = body.getValue("generationConfig").jsonObject
        assertEquals(0.2, config.getValue("temperature").jsonPrimitive.double, 0.0)
        assertEquals("application/json", config.getValue("responseMimeType").jsonPrimitive.content)
        val schema = config.getValue("responseSchema").jsonObject
        assertEquals("OBJECT", schema.getValue("type").jsonPrimitive.content)
        val properties = schema.getValue("properties").jsonObject
        assertEquals(setOf("name", "categorySlug", "quantity", "confidence"), properties.keys)
        assertEquals("STRING", properties.type("name"))
        assertEquals("STRING", properties.type("categorySlug"))
        assertEquals("STRING", properties.type("quantity"))
        assertEquals("NUMBER", properties.type("confidence"))
        assertTrue(properties.getValue("quantity").jsonObject.getValue("nullable").jsonPrimitive.boolean)
        assertEquals(
            CategoryCatalog.defaults.map { it.slug },
            properties.getValue("categorySlug").jsonObject.getValue("enum").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(listOf("name", "categorySlug"), schema.getValue("required").jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun requestTextIsCompactJson() {
        val body = GeminiApi.buildRequest("AAAA", "image/png", CategoryCatalog.defaults)
        assertTrue(body.startsWith("{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":"))
        assertTrue(body.contains("{\"inlineData\":{\"mimeType\":\"image/png\",\"data\":\"AAAA\"}}"))
        assertTrue(body.contains("\"generationConfig\":{\"temperature\":0.2,\"responseMimeType\":\"application/json\""))
        assertTrue(body.contains("\"quantity\":{\"type\":\"STRING\",\"nullable\":true}"))
        assertTrue(body.endsWith("\"required\":[\"name\",\"categorySlug\"]}}}"))
    }

    @Test
    fun promptIsGermanAndListsAllCategories() {
        val prompt = GeminiApi.prompt(CategoryCatalog.defaults)
        for (category in CategoryCatalog.defaults) {
            assertTrue(category.slug, prompt.lines().contains("${category.slug}: ${category.name}"))
        }
        for (expected in listOf("Einkaufsliste", "Vollmilch", "Spülmittel", "AA-Batterien", "\"1 l\"", "\"500 g\"", "Unbekannt", "confidence", "Marke")) {
            assertTrue(expected, prompt.contains(expected))
        }
    }

    @Test
    fun customCategoriesAndSpecialCharactersAreEscaped() {
        val categories = listOf(
            CategoryDef("custom_1", "Omas \"Spezial\"\nKram \\ & Co", "🧺", emptyList(), 0),
            CategoryDef("custom_1", "Duplikat", "🧺", emptyList(), 1),
            CategoryDef("sonstiges", "Sonstiges", "🛒", emptyList(), 2),
        )
        val body = Json.parseToJsonElement(GeminiApi.buildRequest("x", "image/jpeg", categories)).jsonObject
        val enum = body.getValue("generationConfig").jsonObject.getValue("responseSchema").jsonObject
            .getValue("properties").jsonObject.getValue("categorySlug").jsonObject.getValue("enum").jsonArray
        assertEquals(listOf("custom_1", "sonstiges"), enum.map { it.jsonPrimitive.content })
        val prompt = body.getValue("contents").jsonArray[0].jsonObject.getValue("parts").jsonArray[0]
            .jsonObject.getValue("text").jsonPrimitive.content
        assertTrue(prompt.contains("custom_1: Omas \"Spezial\"\nKram \\ & Co"))
        assertFalse(prompt.contains("Duplikat"))
    }

    @Test
    fun noCategoriesGivesSchemaWithoutEnum() {
        val body = Json.parseToJsonElement(GeminiApi.buildRequest("x", "image/jpeg", emptyList())).jsonObject
        val slugSchema = body.getValue("generationConfig").jsonObject.getValue("responseSchema").jsonObject
            .getValue("properties").jsonObject.getValue("categorySlug").jsonObject
        assertEquals(setOf("type"), slugSchema.keys)
    }

    // --- Response ---------------------------------------------------------------------------------

    @Test
    fun parsesRealisticAnswer() {
        val item = GeminiApi.parseResponse(response(answer("Vollmilch", "milch_eier", "\"1 l\"", "0.93")), slugs)
        assertEquals(RecognizedItem("Vollmilch", "milch_eier", "1 l", 0.93), item)
    }

    @Test
    fun parsesFullResponseWithThoughtsAndMetadata() {
        val json = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {"text": "**Identifying the product**\nThe photo shows a bottle of dish soap.", "thought": true},
                      {"text": "{\n  \"name\": \"Spülmittel\",\n  \"categorySlug\": \"haushalt\",\n  \"quantity\": \"500 ml\",\n  \"confidence\": 0.88\n}"}
                    ],
                    "role": "model"
                  },
                  "finishReason": "STOP",
                  "index": 0,
                  "safetyRatings": [
                    {"category": "HARM_CATEGORY_HARASSMENT", "probability": "NEGLIGIBLE"}
                  ]
                }
              ],
              "usageMetadata": {
                "promptTokenCount": 1342,
                "candidatesTokenCount": 41,
                "totalTokenCount": 1501,
                "thoughtsTokenCount": 118
              },
              "modelVersion": "gemini-2.5-flash",
              "responseId": "kF8TaMmAJ5yS1MkP3bqf0Ac"
            }
        """.trimIndent()
        assertEquals(RecognizedItem("Spülmittel", "haushalt", "500 ml", 0.88), GeminiApi.parseResponse(json, slugs))
    }

    @Test
    fun concatenatesTextParts() {
        val json = """
            {"candidates":[{"content":{"role":"model","parts":[
              {"text":"{\"name\": \"AA-Bat"},
              {"text":"terien\", \"categorySlug\": \"batterien_lampen\", \"quantity\": null, \"confidence\": 0.7}"}
            ]},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals(RecognizedItem("AA-Batterien", "batterien_lampen", null, 0.7), GeminiApi.parseResponse(json, slugs))
    }

    @Test
    fun stripsMarkdownFencesAndProse() {
        val inner = """{"name":"Bananen","categorySlug":"obst_gemuese","quantity":null,"confidence":0.9}"""
        val expected = RecognizedItem("Bananen", "obst_gemuese", null, 0.9)
        assertEquals(expected, GeminiApi.parseResponse(response("```json\n$inner\n```"), slugs))
        assertEquals(expected, GeminiApi.parseResponse(response("```\n$inner\n```\n"), slugs))
        assertEquals(expected, GeminiApi.parseResponse(response("```json $inner```"), slugs))
        assertEquals(expected, GeminiApi.parseResponse(response("Hier ist das Ergebnis:\n$inner\nViel Spaß!"), slugs))
        assertEquals(expected, GeminiApi.parseResponse(response("[$inner]"), slugs))
    }

    @Test
    fun unknownCategoryBecomesSonstiges() {
        assertEquals("sonstiges", parse(answer("Zahnstocher", "zahnpflege", "null", "0.8")).categorySlug)
        assertEquals("sonstiges", parse(answer("Zahnstocher", null, "null", "0.8")).categorySlug)
        assertEquals("sonstiges", parse("""{"name":"Zahnstocher","confidence":0.8}""").categorySlug)
        assertEquals(
            "sonstiges",
            GeminiApi.parseResponse(response(answer("Vollmilch", "milch_eier", "null", "0.9")), setOf("custom_x")).categorySlug,
        )
        assertEquals("custom_x", GeminiApi.parseResponse(response(answer("Wolle", "custom_x", "null", "0.9")), setOf("custom_x")).categorySlug)
    }

    @Test
    fun optionalFields() {
        val item = parse("""{"name":"  Spülmittel  ","categorySlug":" haushalt "}""")
        assertEquals(RecognizedItem("Spülmittel", "haushalt", null, null), item)
        assertNull(parse(answer("Brot", "brot_backwaren", "\"  \"", "0.9")).quantity)
        assertNull(parse(answer("Brot", "brot_backwaren", "\"null\"", "0.9")).quantity)
        assertEquals(0.8, parse(answer("Brot", "brot_backwaren", "null", "\"0.8\"")).confidence!!, 1e-9)
        assertEquals(0.85, parse(answer("Brot", "brot_backwaren", "null", "85")).confidence!!, 1e-9)
        assertEquals(1.0, parse(answer("Brot", "brot_backwaren", "null", "250")).confidence!!, 1e-9)
        assertNull(parse(answer("Brot", "brot_backwaren", "null", "\"hoch\"")).confidence)
    }

    @Test
    fun confidenceThreshold() {
        assertEquals(0.2, parse(answer("Brot", "brot_backwaren", "null", "0.2")).confidence!!, 1e-9)
        assertThrows(GeminiApi.MESSAGE_NOT_RECOGNIZED) { parse(answer("Brot", "brot_backwaren", "null", "0.19")) }
        assertThrows(GeminiApi.MESSAGE_NOT_RECOGNIZED) { parse(answer("Brot", "brot_backwaren", "null", "0")) }
        assertThrows(GeminiApi.MESSAGE_NOT_RECOGNIZED) { parse(answer("Brot", "brot_backwaren", "null", "-1")) }
    }

    @Test
    fun unknownItem() {
        val expected = "Auf dem Foto wurde kein Artikel erkannt. Versuch es mit mehr Licht oder näher dran."
        assertThrows(expected) { parse(answer("Unbekannt", "sonstiges", "null", "0")) }
        assertThrows(expected) { parse(answer("unbekannt", "sonstiges", "null", "0.9")) }
        assertThrows(expected) { parse(answer(" Unbekannt ", "sonstiges", "null", null)) }
    }

    @Test
    fun missingName() {
        val expected = "Gemini hat keinen Artikel erkannt."
        assertThrows(expected) { parse("""{"name":"","categorySlug":"sonstiges","confidence":0.9}""") }
        assertThrows(expected) { parse("""{"name":"   ","categorySlug":"sonstiges"}""") }
        assertThrows(expected) { parse("""{"name":null,"categorySlug":"sonstiges"}""") }
        assertThrows(expected) { parse("""{"categorySlug":"sonstiges"}""") }
        assertThrows(expected) { parse("""{"name":{"de":"Milch"}}""") }
        assertThrows(expected) { parse("{}") }
    }

    @Test
    fun blockedPrompt() {
        val json = """
            {
              "promptFeedback": {
                "blockReason": "SAFETY",
                "safetyRatings": [{"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "probability": "HIGH", "blocked": true}]
              },
              "usageMetadata": {"promptTokenCount": 1290, "totalTokenCount": 1290},
              "modelVersion": "gemini-2.5-flash"
            }
        """.trimIndent()
        assertThrows("Gemini hat das Foto abgelehnt.") { GeminiApi.parseResponse(json, slugs) }
        assertThrows("Gemini hat das Foto abgelehnt.") {
            GeminiApi.parseResponse("""{"promptFeedback":{"blockReason":"OTHER"},"candidates":[]}""", slugs)
        }
    }

    @Test
    fun blockedCandidate() {
        for (reason in listOf("SAFETY", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")) {
            val json = """{"candidates":[{"finishReason":"$reason","index":0,"content":{"role":"model"}}]}"""
            assertThrows("Gemini hat das Foto abgelehnt.") { GeminiApi.parseResponse(json, slugs) }
        }
        val withText = """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[{"text":"{\"name\":\"Messer\"}"}]}}]}"""
        assertThrows("Gemini hat das Foto abgelehnt.") { GeminiApi.parseResponse(withText, slugs) }
    }

    @Test
    fun promptFeedbackWithoutBlockReasonIsFine() {
        val json = """
            {"promptFeedback":{"safetyRatings":[]},"candidates":[{"content":{"parts":[{"text":"{\"name\":\"Eier\",\"categorySlug\":\"milch_eier\",\"confidence\":0.9}"}]},"finishReason":"STOP"}]}
        """.trimIndent()
        assertEquals("Eier", GeminiApi.parseResponse(json, slugs).name)
    }

    @Test
    fun noAnswer() {
        val expected = "Gemini hat nicht geantwortet."
        for (json in listOf(
            "{}",
            """{"candidates":[]}""",
            """{"candidates":null}""",
            """{"candidates":[null]}""",
            """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"role":"model"}}]}""",
            """{"candidates":[{"content":{"parts":[]}}]}""",
            """{"candidates":[{"content":{"parts":[{"text":"Ich denke nach...","thought":true}]}}]}""",
            """{"candidates":[{"content":{"parts":[{"text":"   "}]}}]}""",
            """{"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"image/png","data":"AAAA"}}]}}]}""",
        )) {
            assertThrows(expected) { GeminiApi.parseResponse(json, slugs) }
        }
    }

    @Test
    fun malformedAnswers() {
        val expected = "Die Antwort von Gemini war unverständlich."
        for (json in listOf("", "   ", "not json", "<html>oops</html>", "[]", "null", "{\"candidates\":[")) {
            assertThrows(expected) { GeminiApi.parseResponse(json, slugs) }
        }
        assertThrows(expected) { GeminiApi.parseResponse(response("Vollmilch"), slugs) }
        assertThrows(expected) { GeminiApi.parseResponse(response("{\"name\": \"Vollmilch\""), slugs) }
        assertThrows(expected) { GeminiApi.parseResponse(response("[1, 2]"), slugs) }
        assertThrows(expected) { GeminiApi.parseResponse(response("\"Vollmilch\""), slugs) }
    }

    // --- Errors -----------------------------------------------------------------------------------

    @Test
    fun invalidKey() {
        val body = """
            {
              "error": {
                "code": 400,
                "message": "API key not valid. Please pass a valid API key.",
                "status": "INVALID_ARGUMENT",
                "details": [
                  {
                    "@type": "type.googleapis.com/google.rpc.ErrorInfo",
                    "reason": "API_KEY_INVALID",
                    "domain": "googleapis.com",
                    "metadata": {"service": "generativelanguage.googleapis.com"}
                  },
                  {
                    "@type": "type.googleapis.com/google.rpc.LocalizedMessage",
                    "locale": "en-US",
                    "message": "API key not valid. Please pass a valid API key."
                  }
                ]
              }
            }
        """.trimIndent()
        val expected = "Der API-Schlüssel ist ungültig. Bitte in den Einstellungen prüfen."
        assertEquals(expected, GeminiApi.errorMessage(400, body))
        assertEquals(
            expected,
            GeminiApi.errorMessage(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""),
        )
        assertEquals(
            expected,
            GeminiApi.errorMessage(403, """{"error":{"code":403,"status":"PERMISSION_DENIED","details":[{"reason":"API_KEY_INVALID"}]}}"""),
        )
    }

    @Test
    fun noAccess() {
        val expected = "Der API-Schlüssel hat keinen Zugriff auf Gemini."
        val serviceDisabled = """
            {
              "error": {
                "code": 403,
                "message": "Generative Language API has not been used in project 123456789 before or it is disabled.",
                "status": "PERMISSION_DENIED",
                "details": [{"@type": "type.googleapis.com/google.rpc.ErrorInfo", "reason": "SERVICE_DISABLED", "domain": "googleapis.com"}]
              }
            }
        """.trimIndent()
        assertEquals(expected, GeminiApi.errorMessage(403, serviceDisabled))
        assertEquals(
            expected,
            GeminiApi.errorMessage(403, """{"error":{"code":403,"message":"Requests from this Android client application <empty> are blocked.","status":"PERMISSION_DENIED","details":[{"reason":"API_KEY_ANDROID_APP_BLOCKED"}]}}"""),
        )
        assertEquals(expected, GeminiApi.errorMessage(401, """{"error":{"code":401,"status":"UNAUTHENTICATED"}}"""))
        assertEquals(expected, GeminiApi.errorMessage(403, ""))
    }

    @Test
    fun unknownModel() {
        val body = """
            {
              "error": {
                "code": 404,
                "message": "models/gemini-9-ultra is not found for API version v1beta, or is not supported for generateContent. Call ListModels to see the list of available models and their supported methods.",
                "status": "NOT_FOUND"
              }
            }
        """.trimIndent()
        assertEquals(
            "Dieses Gemini-Modell gibt es nicht. Bitte den Modellnamen in den Einstellungen prüfen.",
            GeminiApi.errorMessage(404, body),
        )
    }

    @Test
    fun quota() {
        val body = """
            {
              "error": {
                "code": 429,
                "message": "You exceeded your current quota, please check your plan and billing details.",
                "status": "RESOURCE_EXHAUSTED",
                "details": [
                  {"@type": "type.googleapis.com/google.rpc.QuotaFailure", "violations": [{"quotaMetric": "generativelanguage.googleapis.com/generate_content_free_tier_requests", "quotaId": "GenerateRequestsPerMinutePerProjectPerModel-FreeTier"}]},
                  {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "41s"}
                ]
              }
            }
        """.trimIndent()
        assertEquals(
            "Zu viele Anfragen oder Kontingent aufgebraucht. Bitte später nochmal versuchen.",
            GeminiApi.errorMessage(429, body),
        )
    }

    @Test
    fun regionNotSupported() {
        val body = """{"error":{"code":400,"message":"User location is not supported for the API use.","status":"FAILED_PRECONDITION"}}"""
        assertEquals("Gemini ist für diesen Schlüssel oder dieses Land nicht verfügbar.", GeminiApi.errorMessage(400, body))
    }

    @Test
    fun serverErrors() {
        val expected = "Gemini ist gerade nicht erreichbar. Bitte später nochmal versuchen."
        assertEquals(
            expected,
            GeminiApi.errorMessage(503, """{"error":{"code":503,"message":"The model is overloaded. Please try again later.","status":"UNAVAILABLE"}}"""),
        )
        assertEquals(expected, GeminiApi.errorMessage(500, """{"error":{"code":500,"message":"An internal error has occurred.","status":"INTERNAL"}}"""))
        assertEquals(expected, GeminiApi.errorMessage(502, "<html><body>502 Bad Gateway</body></html>"))
        assertEquals(expected, GeminiApi.errorMessage(504, ""))
    }

    @Test
    fun otherErrors() {
        assertEquals(
            "Fehler bei Gemini (HTTP 400).",
            GeminiApi.errorMessage(400, """{"error":{"code":400,"message":"Unable to process input image.","status":"INVALID_ARGUMENT"}}"""),
        )
        assertEquals("Fehler bei Gemini (HTTP 400).", GeminiApi.errorMessage(400, "not json"))
        assertEquals("Fehler bei Gemini (HTTP 418).", GeminiApi.errorMessage(418, ""))
        assertEquals("Fehler bei Gemini (HTTP 413).", GeminiApi.errorMessage(413, """{"error":"too large"}"""))
        assertEquals("Fehler bei Gemini (HTTP 400).", GeminiApi.errorMessage(400, """{"error":{"status":"FAILED_PRECONDITION_X","details":"x"}}"""))
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun parse(answerJson: String): RecognizedItem = GeminiApi.parseResponse(response(answerJson), slugs)

    /** The model's JSON answer as text; [quantity] and [confidence] are raw JSON, null leaves the field out. */
    private fun answer(name: String, slug: String?, quantity: String, confidence: String?): String {
        val fields = mutableListOf("\"name\": ${JsonPrimitive(name)}")
        fields += "\"categorySlug\": ${if (slug == null) "null" else JsonPrimitive(slug).toString()}"
        fields += "\"quantity\": $quantity"
        if (confidence != null) fields += "\"confidence\": $confidence"
        return fields.joinToString(", ", "{", "}")
    }

    /** A generateContent answer whose single text part is [text]. */
    private fun response(text: String): String {
        val body = JsonObject(
            mapOf(
                "candidates" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "content" to JsonObject(
                                    mapOf(
                                        "parts" to JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive(text))))),
                                        "role" to JsonPrimitive("model"),
                                    ),
                                ),
                                "finishReason" to JsonPrimitive("STOP"),
                                "index" to JsonPrimitive(0),
                            ),
                        ),
                    ),
                ),
                "modelVersion" to JsonPrimitive("gemini-2.5-flash"),
            ),
        )
        return body.toString()
    }

    private fun JsonObject.type(key: String): String =
        getValue(key).jsonObject.getValue("type").jsonPrimitive.content

    private fun assertThrows(expectedMessage: String, block: () -> Unit) {
        try {
            block()
            fail("expected GeminiException: $expectedMessage")
        } catch (e: GeminiException) {
            assertEquals(expectedMessage, e.message)
        }
    }
}
