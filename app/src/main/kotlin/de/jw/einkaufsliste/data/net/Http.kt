package de.jw.einkaufsliste.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** User agent sent to OpenStreetMap/Overpass and Open Food Facts, as their usage policies ask. */
const val USER_AGENT = "Demento-Android/0.1 (private, non-commercial)"

fun createHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

/** Status code and body of an HTTP answer. */
data class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Runs [request] on the IO dispatcher. Throws [IOException] when the server could not be reached. */
suspend fun OkHttpClient.fetch(request: Request): HttpResult = withContext(Dispatchers.IO) {
    newCall(request).execute().use { response ->
        HttpResult(response.code, response.body?.string().orEmpty())
    }
}
