package de.jw.einkaufsliste.feature.products

import de.jw.einkaufsliste.core.model.ProductInfo
import de.jw.einkaufsliste.core.products.OpenFactsApi
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.BarcodeCacheEntity
import de.jw.einkaufsliste.data.net.fetch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

sealed interface LookupResult {
    data class Found(val product: ProductInfo, val fromCache: Boolean) : LookupResult
    data object NotFound : LookupResult

    /** [message] is German and user-facing (offline, server error). */
    data class Error(val message: String) : LookupResult
}

class ProductLookup(private val db: AppDatabase, private val http: OkHttpClient) {
    private val cache = db.barcodeCacheDao()

    // The user waits with a spinner: give up much earlier than the shared client (tuned for photo uploads).
    private val client = http.newBuilder().callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

    /** Cache first, then the Open*Facts hosts in order; successful lookups are cached. */
    suspend fun lookup(barcode: String): LookupResult {
        val code = barcode.trim()
        cache.get(code)?.let { return LookupResult.Found(it.toProductInfo(), fromCache = true) }

        var answered = false
        var failed = false
        var timedOut = false
        for (host in OpenFactsApi.HOSTS) {
            val result = try {
                client.fetch(Request.Builder().url(OpenFactsApi.productUrl(host, code)).get().build())
            } catch (e: InterruptedIOException) {
                // Timed out: the next host would most likely time out as well.
                timedOut = true
                break
            } catch (e: IOException) {
                failed = true
                continue
            }
            answered = true
            when {
                result.isSuccessful -> {
                    // The category is already final: OpenFactsApi falls back to the name only when no tag fits.
                    val product = OpenFactsApi.parse(result.body, code, OpenFactsApi.sourceForHost(host)) ?: continue
                    cache.upsert(product.toCacheEntity(System.currentTimeMillis()))
                    return LookupResult.Found(product, fromCache = false)
                }
                // 404 is how the product API says "unknown here". Anything else (403 blocked, 429 rate limit,
                // 5xx) is no answer: "Produkt unbekannt" would make the app remember the typed name for good.
                result.code == 404 -> continue
                else -> failed = true
            }
        }
        return when {
            timedOut -> LookupResult.Error(MESSAGE_TIMEOUT)
            !answered -> LookupResult.Error(MESSAGE_OFFLINE)
            failed -> LookupResult.Error(MESSAGE_UNAVAILABLE)
            else -> LookupResult.NotFound
        }
    }

    /**
     * Remembers the name and Unterliste the user chose for a barcode (source "user"): typed in for
     * an unknown product, or corrected from what Open Food Facts found. [lookup] reads the cache
     * first, so this wins from then on. [brand] and [quantity] keep the found product's details.
     */
    suspend fun remember(
        barcode: String,
        name: String,
        categorySlug: String,
        brand: String? = null,
        quantity: String? = null,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        cache.upsert(
            BarcodeCacheEntity(
                barcode = barcode.trim(),
                name = trimmed,
                brand = brand?.trim()?.takeIf { it.isNotEmpty() },
                quantity = quantity?.trim()?.takeIf { it.isNotEmpty() },
                categorySlug = categorySlug,
                source = SOURCE_USER,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        /** [BarcodeCacheEntity.source] of names the user typed in. */
        const val SOURCE_USER = "user"

        const val MESSAGE_OFFLINE = "Keine Internetverbindung. Du kannst den Namen selbst eintippen."
        const val MESSAGE_UNAVAILABLE = "Die Produktdatenbank ist gerade nicht erreichbar."
        const val MESSAGE_TIMEOUT = "Die Produktdatenbank antwortet gerade nicht. Du kannst den Namen selbst eintippen."

        private const val CALL_TIMEOUT_SECONDS = 12L
    }
}

/** German "where does this name come from" label for a [ProductInfo.source], e.g. "aus Open Food Facts". */
fun productSourceLabel(source: String): String = when (source) {
    ProductLookup.SOURCE_USER -> "von dir gespeichert"
    "openfoodfacts" -> "aus Open Food Facts"
    "openproductsfacts" -> "aus Open Products Facts"
    "openbeautyfacts" -> "aus Open Beauty Facts"
    "openpetfoodfacts" -> "aus Open Pet Food Facts"
    else -> "aus $source"
}

private fun BarcodeCacheEntity.toProductInfo(): ProductInfo =
    ProductInfo(
        barcode = barcode,
        name = name,
        brand = brand,
        quantity = quantity,
        categorySlug = categorySlug,
        source = source,
    )

private fun ProductInfo.toCacheEntity(fetchedAt: Long): BarcodeCacheEntity =
    BarcodeCacheEntity(
        barcode = barcode,
        name = name,
        brand = brand,
        quantity = quantity,
        categorySlug = categorySlug,
        source = source,
        fetchedAt = fetchedAt,
    )
