package de.jw.einkaufsliste.feature.gemini

import de.jw.einkaufsliste.core.gemini.GeminiApi
import de.jw.einkaufsliste.core.gemini.GeminiException
import de.jw.einkaufsliste.core.model.RecognizedItem
import de.jw.einkaufsliste.data.db.toDef
import de.jw.einkaufsliste.data.net.fetch
import de.jw.einkaufsliste.data.repo.CategoryRepository
import de.jw.einkaufsliste.data.secure.ApiKeyStore
import de.jw.einkaufsliste.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.util.Base64

/** A failure the user fixes in the settings (key missing or rejected, unknown model); [message] is German. */
class GeminiSettingsException(message: String) : Exception(message)

/**
 * Recognizes photographed items with the user's own Gemini API key. Every failure is
 * a [Result.failure] whose message is German and can be shown as is; it is a
 * [GeminiSettingsException] when the fix is in the settings.
 */
class GeminiClient(
    private val http: OkHttpClient,
    private val apiKeyStore: ApiKeyStore,
    private val settings: SettingsRepository,
    private val categoryRepository: CategoryRepository,
) {
    /** Sends [jpeg] to Gemini; failures carry a German user-facing message. */
    suspend fun recognize(jpeg: ByteArray): Result<RecognizedItem> = withContext(Dispatchers.IO) {
        guarded {
            val apiKey = apiKeyStore.get() ?: throw GeminiSettingsException(MESSAGE_NO_KEY)
            val model = modelName(settings.current().geminiModel)
            val categories = categoryRepository.getAll().map { it.toDef() }
            val body = GeminiApi.buildRequest(
                imageBase64 = Base64.getEncoder().encodeToString(jpeg),
                mimeType = "image/jpeg",
                categories = categories,
            )
            val request = requestBuilder(GeminiApi.endpoint(model), apiKey)
                .post(body.toRequestBody(JSON))
                .build()
            val response = http.fetch(request)
            if (!response.isSuccessful) throw httpError(response.code, response.body)
            GeminiApi.parseResponse(response.body, categories.map { it.slug }.toSet())
        }
    }

    /** Checks [apiKey] (or the stored key when null) with a cheap request; failure carries a German message. */
    suspend fun testKey(apiKey: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        guarded {
            val key = apiKey?.trim()?.takeIf { it.isNotEmpty() }
                ?: apiKeyStore.get()
                ?: throw GeminiSettingsException(MESSAGE_NO_KEY)
            val model = modelName(settings.current().geminiModel)
            val response = http.fetch(requestBuilder(MODELS_URL + model, key).get().build())
            if (!response.isSuccessful) throw httpError(response.code, response.body)
            Unit
        }
    }

    /** Runs [block] and turns every failure into a [Result.failure] with a German message. */
    private inline fun <T> guarded(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: GeminiException) {
            Result.failure(e)
        } catch (e: GeminiSettingsException) {
            Result.failure(e)
        } catch (e: InterruptedIOException) {
            Result.failure(GeminiException(MESSAGE_TIMEOUT))
        } catch (e: IOException) {
            Result.failure(GeminiException(MESSAGE_OFFLINE))
        } catch (e: Exception) {
            Result.failure(GeminiException(MESSAGE_UNEXPECTED))
        }

    /**
     * Exception for an HTTP error answer. A rejected key (401/403, or the "key not valid" answer
     * [GeminiApi.errorMessage] looks for) or an unknown model (404) is fixed in the settings.
     */
    private fun httpError(code: Int, body: String): Exception {
        val message = GeminiApi.errorMessage(code, body)
        val keyOrModel = code == 401 || code == 403 || code == 404 || body.contains("API_KEY_INVALID") ||
            (code == 400 && body.contains("API key not valid", ignoreCase = true))
        return if (keyOrModel) GeminiSettingsException(message) else GeminiException(message)
    }

    private fun requestBuilder(url: String, apiKey: String): Request.Builder {
        val httpUrl = url.toHttpUrlOrNull() ?: throw GeminiSettingsException(MESSAGE_BAD_MODEL)
        return try {
            Request.Builder().url(httpUrl).header(GeminiApi.API_KEY_HEADER, apiKey)
        } catch (e: IllegalArgumentException) {
            // OkHttp refuses header values with control or non-ASCII characters.
            throw GeminiSettingsException(MESSAGE_BAD_KEY)
        }
    }

    /** Accepts "gemini-2.5-flash" as well as the API's own "models/gemini-2.5-flash". */
    private fun modelName(setting: String): String =
        setting.trim().removePrefix("models/").ifEmpty { GeminiApi.DEFAULT_MODEL }

    companion object {
        const val MESSAGE_NO_KEY = "Kein Gemini-API-Schlüssel hinterlegt."
        const val MESSAGE_OFFLINE = "Keine Internetverbindung."
        const val MESSAGE_TIMEOUT = "Gemini hat nicht rechtzeitig geantwortet. Bitte nochmal versuchen."
        const val MESSAGE_BAD_KEY = "Der API-Schlüssel enthält ungültige Zeichen. Bitte in den Einstellungen prüfen."
        const val MESSAGE_BAD_MODEL = "Der Modellname ist ungültig. Bitte in den Einstellungen prüfen."
        const val MESSAGE_UNEXPECTED = "Bei der Anfrage an Gemini ist ein Fehler aufgetreten."

        private const val MODELS_URL = "https://generativelanguage.googleapis.com/v1beta/models/"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
