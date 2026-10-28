package de.jw.einkaufsliste.core.gemini

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GeminiModelsTest {
    // --- Ids ---------------------------------------------------------------------------------------

    @Test
    fun normalizeIdAcceptsTheApisOwnNamesAndFallsBackToTheDefault() {
        assertEquals("gemini-3.8-flash", GeminiModels.normalizeId(" models/gemini-3.8-flash "))
        assertEquals("gemini-4-argon", GeminiModels.normalizeId("gemini-4-argon"))
        assertEquals(GeminiApi.DEFAULT_MODEL, GeminiModels.normalizeId(""))
        assertEquals(GeminiApi.DEFAULT_MODEL, GeminiModels.normalizeId("models/"))
    }

    @Test
    fun theDefaultIsTheFirstKnownModel() {
        assertEquals(GeminiApi.DEFAULT_MODEL, GeminiModels.KNOWN.first().id)
        assertEquals(GeminiModels.KNOWN.size, GeminiModels.KNOWN.map { it.id }.distinct().size)
        assertTrue(GeminiModels.KNOWN.all { GeminiModels.isPickable(it.id, listOf("generateContent")) })
    }

    @Test
    fun onlyGemini1And2GetATemperature() {
        for (model in listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "models/gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro")) {
            assertTrue(model, GeminiModels.acceptsTemperature(model))
        }
        for (model in listOf("gemini-3.8-flash", "gemini-3-flash-preview", "gemini-4-argon", "gemini-10.1-flash", "gemini-flash-latest", "gemma-3-27b-it", "")) {
            assertFalse(model, GeminiModels.acceptsTemperature(model))
        }
    }

    @Test
    fun listUrlAsksForTheLargestPageAndEncodesTheToken() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", GeminiModels.listUrl())
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000", GeminiModels.listUrl(" "))
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000&pageToken=a%2Bb%2F%3D%26c",
            GeminiModels.listUrl("a+b/=&c"),
        )
    }

    // --- Filter ------------------------------------------------------------------------------------

    @Test
    fun textModelsArePickable() {
        for (id in listOf(
            "gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-4-argon", "gemini-2.5-flash", "gemini-2.5-pro",
            "gemini-3.1-pro-preview", "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-exp-1206",
        )) {
            assertTrue(id, GeminiModels.isPickable(id, listOf("generateContent", "countTokens")))
        }
    }

    @Test
    fun speechLiveImageAndOtherModelsAreNotPickable() {
        for (id in listOf(
            "gemini-3.8-flash-tts", "gemini-2.5-flash-preview-tts", "gemini-3.8-live", "gemini-3.8-live-extended-thinking",
            "gemini-2.5-flash-native-audio-preview-12-2025", "gemini-live-2.5-flash-preview", "gemini-3.1-flash-image",
            "gemini-3.1-flash-lite-image", "gemini-2.5-flash-image", "gemini-nano-banana-2.1", "gemini-embedding-001",
            "gemini-3.5-transcribe", "gemini-3.5-live-translate-preview", "gemini-robotics-er-2-preview",
            "gemini-2.5-computer-use-preview-10-2025", "gemini-omni-1.1-flash", "gemma-3-27b-it", "imagen-4.0-generate-001",
            "veo-3.0-generate-001", "learnlm-2.0-flash-experimental", "aqa",
        )) {
            assertFalse(id, GeminiModels.isPickable(id, listOf("generateContent")))
        }
    }

    @Test
    fun modelsWithoutGenerateContentAreNotPickable() {
        assertFalse(GeminiModels.isPickable("gemini-3.8-flash", listOf("countTokens", "createCachedContent")))
        assertFalse(GeminiModels.isPickable("gemini-3.8-flash", emptyList()))
        assertTrue(GeminiModels.isPickable("Gemini-3.8-Flash", listOf("GenerateContent")))
    }

    // --- Parsing -----------------------------------------------------------------------------------

    @Test
    fun parsePageKeepsPickableModelsWithTheirNames() {
        val page = GeminiModels.parsePage(
            """
            {
              "models": [
                {"name": "models/gemini-3.8-flash", "displayName": "Gemini 3.8 Flash",
                 "supportedGenerationMethods": ["generateContent", "countTokens"]},
                {"name": "models/gemini-3.8-flash-tts", "displayName": "Gemini 3.8 Flash TTS",
                 "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-embedding-2", "displayName": "Gemini Embedding 2",
                 "supportedGenerationMethods": ["embedContent"]},
                {"name": "models/gemini-4-argon", "supportedGenerationMethods": ["generateContent"]},
                {"name": "models/gemini-3.5-flash-lite", "displayName": " ",
                 "supportedGenerationMethods": ["generateContent"]},
                {"displayName": "No name", "supportedGenerationMethods": ["generateContent"]},
                "not an object"
              ],
              "nextPageToken": "abc"
            }
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                GeminiModel("gemini-3.8-flash", "Gemini 3.8 Flash"),
                GeminiModel("gemini-4-argon", "gemini-4-argon"),
                GeminiModel("gemini-3.5-flash-lite", "gemini-3.5-flash-lite"),
            ),
            page.models,
        )
        assertEquals("abc", page.nextPageToken)
    }

    @Test
    fun lastPageHasNoToken() {
        assertNull(GeminiModels.parsePage("""{"models": []}""").nextPageToken)
        assertNull(GeminiModels.parsePage("""{"models": [], "nextPageToken": ""}""").nextPageToken)
        assertEquals(emptyList<GeminiModel>(), GeminiModels.parsePage("{}").models)
    }

    @Test
    fun parsePageRejectsGarbage() {
        for (body in listOf("", "not json", "[]", "\"models\"")) {
            try {
                GeminiModels.parsePage(body)
                fail("Expected GeminiException for <$body>")
            } catch (e: GeminiException) {
                assertEquals(GeminiApi.MESSAGE_GARBLED, e.message)
            }
        }
    }

    // --- Picker ------------------------------------------------------------------------------------

    @Test
    fun withoutGooglesListThePickerOffersTheKnownModels() {
        val choices = GeminiModels.choices(null, GeminiApi.DEFAULT_MODEL)
        assertEquals(GeminiModels.KNOWN.map { it.id }.toSet(), choices.map { it.id }.toSet())
        assertEquals(GeminiApi.DEFAULT_MODEL, choices.first().id)
        assertEquals(GeminiModels.HINT_RECOMMENDED, choices.first().hint)
        assertEquals(choices, GeminiModels.choices(emptyList(), GeminiApi.DEFAULT_MODEL))
    }

    @Test
    fun googlesListIsSortedDefaultFirstThenNewestStableFirst() {
        val fromGoogle = listOf(
            GeminiModel("gemini-flash-latest", "Gemini Flash Latest"),
            GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
            GeminiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            GeminiModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"),
            GeminiModel("gemini-3.8-flash", "Gemini 3.8 Flash"),
            GeminiModel("gemini-3.10-flash", "Gemini 3.10 Flash"),
            GeminiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
            GeminiModel("gemini-4-argon", "Gemini 4 Argon"),
            GeminiModel("gemini-4-argon", "Duplicate"),
        )
        val choices = GeminiModels.choices(fromGoogle, "gemini-3.5-flash-lite")
        assertEquals(
            listOf(
                "gemini-3.8-flash", "gemini-4-argon", "gemini-3.10-flash", "gemini-3.5-flash-lite",
                "gemini-3.1-flash-lite", "gemini-3.1-pro-preview", "gemini-2.5-flash", "gemini-flash-latest",
            ),
            choices.map { it.id },
        )
        assertEquals("Gemini 4 Argon", choices.single { it.id == "gemini-4-argon" }.name)
    }

    @Test
    fun hintsComeFromTheKnownModelsOrTheName() {
        val choices = GeminiModels.choices(
            listOf(
                GeminiModel("gemini-3.8-flash", "Gemini 3.8 Flash"),
                GeminiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
                GeminiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
                GeminiModel("gemini-pro-latest", "Gemini Pro Latest"),
                GeminiModel("gemini-3.6-flash", "Gemini 3.6 Flash"),
            ),
            GeminiApi.DEFAULT_MODEL,
        ).associate { it.id to it.hint }
        assertEquals(GeminiModels.HINT_RECOMMENDED, choices["gemini-3.8-flash"])
        assertEquals(GeminiModels.HINT_LEGACY, choices["gemini-2.5-pro"])
        assertEquals(GeminiModels.HINT_PREVIEW, choices["gemini-3.1-pro-preview"])
        assertEquals(GeminiModels.HINT_ALIAS, choices["gemini-pro-latest"])
        assertNull(choices["gemini-3.6-flash"])
    }

    @Test
    fun aSelectedModelGooglesListLacksIsStillOffered() {
        val fromGoogle = listOf(GeminiModel("gemini-3.8-flash", "Gemini 3.8 Flash"))

        val custom = GeminiModels.choices(fromGoogle, "models/my-tuned-model")
        assertEquals(listOf("gemini-3.8-flash", "my-tuned-model"), custom.map { it.id })
        assertEquals(GeminiModel("my-tuned-model", "my-tuned-model", GeminiModels.HINT_CUSTOM), custom.last())

        val known = GeminiModels.choices(fromGoogle, "gemini-4-argon")
        assertEquals(GeminiModels.KNOWN.single { it.id == "gemini-4-argon" }, known.single { it.id == "gemini-4-argon" })

        val legacy = GeminiModels.choices(fromGoogle, "gemini-2.0-flash")
        assertEquals(GeminiModels.HINT_LEGACY, legacy.single { it.id == "gemini-2.0-flash" }.hint)
    }

    @Test
    fun findReturnsTheSelectedEntryOrACustomOne() {
        val choices = GeminiModels.choices(null, GeminiApi.DEFAULT_MODEL)
        assertEquals(GeminiModels.KNOWN.first(), GeminiModels.find(choices, " models/${GeminiApi.DEFAULT_MODEL}"))
        assertEquals(GeminiModel("other", "other", GeminiModels.HINT_CUSTOM), GeminiModels.find(choices, "other"))
    }
}
