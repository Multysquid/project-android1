package de.jw.einkaufsliste.feature.shops

import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.osm.OverpassApi
import de.jw.einkaufsliste.data.db.AppDatabase
import de.jw.einkaufsliste.data.db.ShopEntity
import de.jw.einkaufsliste.data.db.toEntity
import de.jw.einkaufsliste.data.db.toShopInfo
import de.jw.einkaufsliste.data.net.fetch
import de.jw.einkaufsliste.data.settings.ShopFetchInfo
import de.jw.einkaufsliste.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

sealed interface ShopRefreshResult {
    /** Cache covers [here] and is fresh; nothing downloaded. */
    data class UpToDate(val cachedCount: Int) : ShopRefreshResult

    /** Downloaded [count] shops. */
    data class Refreshed(val count: Int) : ShopRefreshResult

    /**
     * Download failed; [message] is German and user-facing. [cachedCount] counts the whole cache,
     * which may have no shop near the user (ask [ShopRepository.shopsNear] for that).
     */
    data class Failed(val message: String, val cachedCount: Int) : ShopRefreshResult
}

/**
 * Shops from OpenStreetMap, downloaded via Overpass around the user's location and
 * cached in Room. Everything except [ensureShopsAround] works offline on the cache.
 */
class ShopRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val shopDao = db.shopDao()

    /** One download at a time; a second caller waits and then usually finds the cache up to date. */
    private val downloadLock = Mutex()

    fun observeShops(): Flow<List<ShopEntity>> = shopDao.observeAll()

    /** Downloads shops around [here] from Overpass when the cache does not cover it or is older than 7 days. */
    suspend fun ensureShopsAround(here: GeoPoint, force: Boolean = false): ShopRefreshResult =
        ensureShopsCovering(here, settings.current().radiusMeters, force)

    /**
     * Like [ensureShopsAround], but makes sure the cache covers [coverRadiusMeters] around [here]
     * (for a search wider than the radius setting).
     *
     * With [force] it downloads even when the cache is fresh, except when the last download
     * covering [here] is less than a minute old (protects the public Overpass servers).
     * A download that has started is finished and stored even if the caller is cancelled
     * meanwhile, so leaving a screen does not waste it.
     */
    suspend fun ensureShopsCovering(
        here: GeoPoint,
        coverRadiusMeters: Int,
        force: Boolean = false,
    ): ShopRefreshResult = downloadLock.withLock {
        // Larger circles could never count as covered and would be downloaded on every call.
        val cover = coverRadiusMeters.coerceIn(0, (MAX_FETCH_RADIUS_METERS * COVERAGE_FACTOR).toInt())
        val now = System.currentTimeMillis()
        val last = settings.lastShopFetch()
        val maxAge = if (force) FORCE_MIN_INTERVAL_MS else MAX_CACHE_AGE_MS
        if (last != null && (now - last.fetchedAt) in 0 until maxAge && covers(last, here, cover)) {
            return@withLock ShopRefreshResult.UpToDate(shopDao.count())
        }

        // Overpass gets a rounded center, not the exact fix; the circle grows by the shift so it still
        // covers the same area around [here] (and the largest circle still counts as covered).
        val center = queryCenter(here)
        val shift = ceil(GeoMath.distanceMeters(here, center) / COVERAGE_FACTOR).toInt()
        val fetchRadius = max(fetchRadiusFor(settings.current().radiusMeters), ceil(cover / COVERAGE_FACTOR).toInt())
            .coerceAtMost(MAX_FETCH_RADIUS_METERS) + shift
        // Default: parsing a city's answer (several MB of JSON) must not block the main thread.
        withContext(NonCancellable + Dispatchers.Default) {
            val shops = download(center, fetchRadius)
                ?: return@withContext ShopRefreshResult.Failed(FAILED_MESSAGE, shopDao.count())
            shopDao.upsertAll(shops.map { it.toEntity(fetchedAt = now) })
            shopDao.deleteFetchedBefore(now - STALE_SHOP_AGE_MS)
            settings.setLastShopFetch(ShopFetchInfo(center = center, radiusMeters = fetchRadius, fetchedAt = now))
            ShopRefreshResult.Refreshed(shops.size)
        }
    }

    /** Cached shops within [radiusMeters] of [here] (exact distance, not just the bounding box). No network. */
    suspend fun shopsNear(here: GeoPoint, radiusMeters: Double): List<ShopInfo> {
        if (!(radiusMeters > 0.0)) return emptyList()
        val box = GeoMath.boundingBox(here, radiusMeters)
        return shopDao.getInBox(box.minLat, box.maxLat, box.minLon, box.maxLon)
            .filter { GeoMath.distanceMeters(here, GeoPoint(it.lat, it.lon)) <= radiusMeters }
            .map { it.toShopInfo() }
    }

    suspend fun getShop(id: String): ShopInfo? = shopDao.getById(id)?.toShopInfo()

    /** Tries the Overpass endpoints in order; null when none gave a usable answer. */
    private suspend fun download(center: GeoPoint, radiusMeters: Int): List<ShopInfo>? {
        val body = FormBody.Builder()
            .add("data", OverpassApi.buildShopsQuery(center, radiusMeters))
            .build()
        for (endpoint in OverpassApi.ENDPOINTS) {
            val request = Request.Builder().url(endpoint).post(body).build()
            val result = try {
                http.fetch(request)
            } catch (e: IOException) {
                continue
            }
            // 429 (rate limit), 504 (busy) and other errors: the next server may do better.
            if (!result.isSuccessful) continue
            // Null: not an Overpass answer, or a runtime error reported with status 200 (timeout, memory).
            return OverpassApi.parseOrNull(result.body) ?: continue
        }
        return null
    }

    companion object {
        const val MIN_FETCH_RADIUS_METERS = 3_000
        const val MAX_FETCH_RADIUS_METERS = 10_000

        /** Only this share of the downloaded circle counts as covered, so shops at its edge are not missed. */
        const val COVERAGE_FACTOR = 0.9

        val MAX_CACHE_AGE_MS: Long = TimeUnit.DAYS.toMillis(7)
        val FORCE_MIN_INTERVAL_MS: Long = TimeUnit.SECONDS.toMillis(60)

        /** Shops not seen in any download for this long are deleted (closed or no longer in OSM nearby). */
        val STALE_SHOP_AGE_MS: Long = TimeUnit.DAYS.toMillis(30)

        const val FAILED_MESSAGE =
            "Läden konnten nicht geladen werden. Keine Internetverbindung oder der Kartenserver ist überlastet."

        /** Download radius for a search radius setting: 4x the setting, at least 3 km, at most 10 km. */
        fun fetchRadiusFor(radiusMeters: Int): Int =
            max(MIN_FETCH_RADIUS_METERS, radiusMeters * 4).coerceIn(MIN_FETCH_RADIUS_METERS, MAX_FETCH_RADIUS_METERS)

        /** Decimal places of the download center sent to Overpass: 3 is a grid of about 110 x 70 m in Germany. */
        private const val QUERY_CENTER_DECIMALS = 3

        /** [here] rounded to [QUERY_CENTER_DECIMALS], so Overpass gets an approximate area, not the exact fix. */
        fun queryCenter(here: GeoPoint): GeoPoint {
            val scale = 10.0.pow(QUERY_CENTER_DECIMALS)
            return GeoPoint(Math.round(here.lat * scale) / scale, Math.round(here.lon * scale) / scale)
        }

        /** True when the download [last] contains the whole circle of [radiusMeters] around [here]. */
        fun covers(last: ShopFetchInfo, here: GeoPoint, radiusMeters: Int): Boolean =
            GeoMath.distanceMeters(last.center, here) + radiusMeters <= last.radiusMeters * COVERAGE_FACTOR
    }
}
