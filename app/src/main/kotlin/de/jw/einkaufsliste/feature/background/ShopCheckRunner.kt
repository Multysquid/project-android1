package de.jw.einkaufsliste.feature.background

import android.content.Context
import android.util.Log
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.geofence.GeofenceSelector
import de.jw.einkaufsliste.core.match.ShopMatcher
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopInfo
import de.jw.einkaufsliste.core.model.ShopMatch
import de.jw.einkaufsliste.core.notify.NotificationPolicy
import de.jw.einkaufsliste.core.osm.OpeningHours
import de.jw.einkaufsliste.core.text.ItemNames
import de.jw.einkaufsliste.data.db.NotificationLogEntity
import de.jw.einkaufsliste.data.settings.AppSettings
import de.jw.einkaufsliste.feature.shops.ShopRefreshResult
import de.jw.einkaufsliste.feature.shops.ShopRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** What started a check. [MANUAL] runs even when the check is off and never notifies. */
enum class CheckTrigger { PERIODIC, SERVICE, GEOFENCE, BOOT, MANUAL }

/** [message] is a short German summary, shown after a manual check ("2 Läden mit Artikeln in der Nähe"). */
data class CheckOutcome(val notifiedShops: Int, val matchingShops: Int, val message: String)

/**
 * The shop check shared by WorkManager, the foreground service, geofences and the manual
 * "Jetzt prüfen": location -> shop cache -> classify new items -> matching every shop in the
 * radius -> filters -> notifications -> geofences.
 */
class ShopCheckRunner(private val context: Context, private val container: AppContainer) {
    private val notifier by lazy { ShopNotifier(context) }
    private val geofences by lazy { GeofenceManager(context, container.locationProvider) }

    /** Serializes "read cooldown, notify, write cooldown", so parallel checks do not notify twice. */
    private val notifyLock = Mutex()

    @Volatile
    private var lastHousekeepingAt = 0L

    /** One full check: location, shop cache, matching, notifications, geofences. Never throws. */
    suspend fun run(trigger: CheckTrigger, geofenceShopIds: List<String> = emptyList()): CheckOutcome =
        run(trigger, geofenceShopIds, location = null)

    /**
     * Like the other [run], at a [location] that is already known (from a geofence event or a
     * location update); null looks the location up. Never throws except for cancellation.
     */
    suspend fun run(
        trigger: CheckTrigger,
        geofenceShopIds: List<String> = emptyList(),
        location: GeoPoint?,
    ): CheckOutcome {
        val outcome = runLogging(TAG, "Check ($trigger) failed") { check(trigger, geofenceShopIds, location) }
            ?: CheckOutcome(0, 0, "Die Prüfung ist fehlgeschlagen.")
        Log.d(TAG, "Check ($trigger): matching=${outcome.matchingShops} notified=${outcome.notifiedShops}")
        return outcome
    }

    private suspend fun check(trigger: CheckTrigger, geofenceShopIds: List<String>, knownLocation: GeoPoint?): CheckOutcome {
        val settings = container.settingsRepository.current()
        val manual = trigger == CheckTrigger.MANUAL
        if (!settings.checkEnabled && !manual) {
            geofences.removeAll()
            return CheckOutcome(0, 0, "Prüfung ist ausgeschaltet.")
        }
        if (container.itemRepository.openItemsForMatching().isEmpty()) {
            geofences.removeAll()
            return CheckOutcome(0, 0, "Deine Liste ist leer.")
        }

        val locationProvider = container.locationProvider
        if (knownLocation == null && !locationProvider.hasForegroundPermission()) {
            return CheckOutcome(0, 0, "Standortzugriff ist nicht erlaubt.")
        }
        val here = knownLocation ?: locationProvider.currentLocation()
            ?: return CheckOutcome(0, 0, "Kein Standort verfügbar.")

        // A failed download is fine as long as the cache has shops here.
        val refresh = runLogging(TAG, "Shop refresh failed") { container.shopRepository.ensureShopsAround(here) }
        // Items are classified when they are added; this catches those added offline or before a Gemini key was set.
        container.assortmentService.classifyOpenItems()

        val radius = settings.radiusMeters
        val geofenceIds = if (trigger == CheckTrigger.GEOFENCE) geofenceShopIds.toSet() else emptySet()
        // Every registration fires ENTER for shops the user is near; geofence checks must not loop.
        val updateGeofences = settings.checkEnabled &&
            !(trigger == CheckTrigger.GEOFENCE && GeofenceManager.hasFreshRegistration(context))
        val wide = if (updateGeofences || geofenceIds.isNotEmpty()) {
            container.matchService.matchesAround(here, ShopRepository.fetchRadiusFor(radius).toDouble())
        } else {
            emptyList()
        }

        var matches = container.matchService.matchesAround(here, radius.toDouble())
        if (geofenceIds.isNotEmpty()) {
            // The geofence says the user is at the shop even when this fix is a bit outside the radius
            // (but not far outside: a geofence of an older, larger radius may still be registered).
            // Twins of a match (one shop mapped twice) count as known: the matcher dropped them on purpose.
            val known = HashSet<String>()
            for (match in matches) known += sameShopIds(match.shop)
            val extra = ArrayList<ShopMatch>()
            for (match in wide) {
                if (match.shop.id !in geofenceIds || match.shop.id in known) continue
                if (match.distanceMeters > radius + GEOFENCE_SLACK_METERS) continue
                known += sameShopIds(match.shop)
                extra += match
            }
            matches = matches + extra
        }
        if (settings.respectOpeningHours) {
            val now = LocalDateTime.now()
            matches = matches.filter { OpeningHours.isOpen(it.shop.openingHours, now) != false }
        }

        val notified = if (manual) 0 else notifyAbout(matches, settings)

        if (updateGeofences) {
            // The radius may have changed during the check; CheckScheduler then removed the old circles.
            val geofenceRadius = container.settingsRepository.current().radiusMeters
            geofences.replaceAll(GeofenceSelector.select(here, wide), geofenceRadius)
        }
        runLogging(TAG, "Housekeeping failed") { housekeeping() }

        val message = when {
            // The cache may hold shops from elsewhere (home), but none here: say why there is nothing.
            matches.isEmpty() && refresh is ShopRefreshResult.Failed &&
                container.shopRepository.shopsNear(here, radius.toDouble()).isEmpty() -> refresh.message
            matches.isEmpty() -> "Gerade kein passender Laden in der Nähe."
            matches.size == 1 -> "1 Laden mit Artikeln von deiner Liste in der Nähe."
            else -> "${matches.size} Läden mit Artikeln von deiner Liste in der Nähe."
        }
        return CheckOutcome(notifiedShops = notified, matchingShops = matches.size, message = message)
    }

    /**
     * Notifies about the best [MAX_NOTIFIED_SHOPS] matches outside their cooldown; returns how many.
     * The other matches outside their cooldown start it too: the notification leads to the
     * "In der Nähe" screen, which lists them, and the next check must not notify them in a burst.
     */
    private suspend fun notifyAbout(matches: List<ShopMatch>, settings: AppSettings): Int {
        if (matches.isEmpty()) return 0
        val time = LocalDateTime.now()
        if (NotificationPolicy.isQuietTime(time.hour * 60 + time.minute, settings.quietHours)) return 0
        if (!notifier.canNotify()) return 0
        val log = container.database.notificationLogDao()
        return notifyLock.withLock {
            val now = System.currentTimeMillis()
            // Shop ids per match, all sharing one cooldown; one notification per physical shop.
            val claimed = HashSet<String>()
            val due = matches.mapNotNull { match ->
                val ids = sameShopIds(match.shop)
                if (ids.any { it in claimed }) return@mapNotNull null
                claimed += ids
                val last = ids.mapNotNull { log.get(it)?.lastNotifiedAt }.maxOrNull()
                if (NotificationPolicy.cooldownPassed(now, last, settings.cooldownHours)) match to ids else null
            }
            val shown = due.take(MAX_NOTIFIED_SHOPS)
            val posted = notifier.notifyShops(shown.map { it.first }, moreShops = due.size - shown.size)
            if (posted.isNotEmpty()) {
                for (id in due.flatMap { it.second }) log.upsert(NotificationLogEntity(shopId = id, lastNotifiedAt = now))
            }
            posted.size
        }
    }

    /**
     * Ids of [shop] and of other cached objects for the same shop (mapped twice, e.g. node and
     * building; same rule as [ShopMatcher]). The matcher keeps whichever is better or nearer, so
     * the cooldown must cover all of them.
     */
    private suspend fun sameShopIds(shop: ShopInfo): List<String> {
        val key = duplicateKey(shop) ?: return listOf(shop.id)
        val twins = container.shopRepository.shopsNear(shop.point, ShopMatcher.DUPLICATE_DISTANCE_METERS)
            .filter { duplicateKey(it) == key }
            .map { it.id }
        return (listOf(shop.id) + twins).distinct()
    }

    /** Same as ShopMatcher's: tag and normalized name (or brand); null for shops without either. */
    private fun duplicateKey(shop: ShopInfo): String? {
        val label = shop.name?.takeIf { it.isNotBlank() } ?: shop.brand?.takeIf { it.isNotBlank() } ?: return null
        return shop.tag.trim() + "|" + ItemNames.normalize(label)
    }

    /** Drops old cooldown entries, purchase history and Gemini answers; at most every few hours. */
    private suspend fun housekeeping() {
        val now = System.currentTimeMillis()
        if (now - lastHousekeepingAt < HOUSEKEEPING_INTERVAL_MS) return
        lastHousekeepingAt = now
        container.database.notificationLogDao().deleteBefore(now - NOTIFICATION_LOG_MAX_AGE_MS)
        container.database.purchaseDao().deleteBefore(now - PURCHASE_MAX_AGE_MS)
        container.database.assortmentCacheDao().deleteBefore(now - ASSORTMENT_MAX_AGE_MS)
    }

    private companion object {
        const val TAG = "ShopCheck"
        const val MAX_NOTIFIED_SHOPS = 3

        /** How far outside the radius a shop whose geofence fired still counts (location inaccuracy). */
        const val GEOFENCE_SLACK_METERS = 150
        val HOUSEKEEPING_INTERVAL_MS: Long = TimeUnit.HOURS.toMillis(6)
        val NOTIFICATION_LOG_MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(7)
        val PURCHASE_MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(2 * 365)

        /** Gemini is asked again about names it classified this long ago (and that are still on the list). */
        val ASSORTMENT_MAX_AGE_MS: Long = TimeUnit.DAYS.toMillis(180)
    }
}
