package de.jw.einkaufsliste.data.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.jw.einkaufsliste.core.gemini.GeminiApi
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.QuietHours
import de.jw.einkaufsliste.core.schedule.CheckSchedule
import de.jw.einkaufsliste.data.secure.ApiKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context -> listOf(SettingsUpgradeMigration { ApiKeyStore.hasStoredKey(context) }) },
)

/** User settings. Defaults are the ones agreed in the plan. */
data class AppSettings(
    /** Background shop check on/off. Off until the user grants location permission. */
    val checkEnabled: Boolean = false,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    val intervalMinutes: Int = CheckSchedule.DEFAULT_INTERVAL_MINUTES,
    val quietHoursEnabled: Boolean = true,
    val quietStartMinute: Int = 22 * 60,
    val quietEndMinute: Int = 7 * 60,
    val respectOpeningHours: Boolean = true,
    val cooldownHours: Int = 3,
    val geminiModel: String = GeminiApi.DEFAULT_MODEL,
    /**
     * Ask Gemini what kind of product each new item is (only with an API key); its answers count
     * only while this is on. See AssortmentService.
     */
    val classifyWithGemini: Boolean = true,
    val batterySetupDone: Boolean = false,
    /** The user hid the list's card that says the shop check is off. */
    val hideCheckBanner: Boolean = false,
) {
    val quietHours: QuietHours? get() = if (quietHoursEnabled) QuietHours(quietStartMinute, quietEndMinute) else null

    companion object {
        const val DEFAULT_RADIUS_METERS = 1000
        const val MIN_RADIUS_METERS = 100
        const val MAX_RADIUS_METERS = 5000
    }
}

/** Where and when shops were last downloaded, to decide when to download again. */
data class ShopFetchInfo(val center: GeoPoint, val radiusMeters: Int, val fetchedAt: Long)

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.checkEnabled] = next.checkEnabled
            prefs[Keys.radiusMeters] = next.radiusMeters.coerceIn(AppSettings.MIN_RADIUS_METERS, AppSettings.MAX_RADIUS_METERS)
            prefs[Keys.intervalMinutes] = CheckSchedule.coerce(next.intervalMinutes)
            prefs[Keys.quietHoursEnabled] = next.quietHoursEnabled
            prefs[Keys.quietStartMinute] = next.quietStartMinute.coerceIn(0, 24 * 60 - 1)
            prefs[Keys.quietEndMinute] = next.quietEndMinute.coerceIn(0, 24 * 60 - 1)
            prefs[Keys.respectOpeningHours] = next.respectOpeningHours
            prefs[Keys.cooldownHours] = next.cooldownHours.coerceIn(1, 48)
            prefs[Keys.geminiModel] = next.geminiModel.trim().ifEmpty { GeminiApi.DEFAULT_MODEL }
            prefs[Keys.classifyWithGemini] = next.classifyWithGemini
            prefs[Keys.batterySetupDone] = next.batterySetupDone
            prefs[Keys.hideCheckBanner] = next.hideCheckBanner
        }
    }

    suspend fun lastShopFetch(): ShopFetchInfo? {
        val prefs = store.data.first()
        val lat = prefs[Keys.fetchLat] ?: return null
        val lon = prefs[Keys.fetchLon] ?: return null
        val radius = prefs[Keys.fetchRadius] ?: return null
        val at = prefs[Keys.fetchAt] ?: return null
        return ShopFetchInfo(GeoPoint(lat, lon), radius, at)
    }

    suspend fun setLastShopFetch(info: ShopFetchInfo) {
        store.edit { prefs ->
            prefs[Keys.fetchLat] = info.center.lat
            prefs[Keys.fetchLon] = info.center.lon
            prefs[Keys.fetchRadius] = info.radiusMeters
            prefs[Keys.fetchAt] = info.fetchedAt
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            checkEnabled = this[Keys.checkEnabled] ?: defaults.checkEnabled,
            radiusMeters = this[Keys.radiusMeters] ?: defaults.radiusMeters,
            intervalMinutes = CheckSchedule.coerce(this[Keys.intervalMinutes] ?: defaults.intervalMinutes),
            quietHoursEnabled = this[Keys.quietHoursEnabled] ?: defaults.quietHoursEnabled,
            quietStartMinute = this[Keys.quietStartMinute] ?: defaults.quietStartMinute,
            quietEndMinute = this[Keys.quietEndMinute] ?: defaults.quietEndMinute,
            respectOpeningHours = this[Keys.respectOpeningHours] ?: defaults.respectOpeningHours,
            cooldownHours = this[Keys.cooldownHours] ?: defaults.cooldownHours,
            geminiModel = this[Keys.geminiModel] ?: defaults.geminiModel,
            classifyWithGemini = this[Keys.classifyWithGemini] ?: defaults.classifyWithGemini,
            batterySetupDone = this[Keys.batterySetupDone] ?: defaults.batterySetupDone,
            hideCheckBanner = this[Keys.hideCheckBanner] ?: defaults.hideCheckBanner,
        )
    }
}

/**
 * One-time upgrade of the settings an older version saved, run by DataStore before the first
 * read and marked done in the store. That version saved every setting on each change, so most
 * users have its default radius of 500 m stored: it becomes the new default. Users who already
 * had a Gemini key were told that only photos go to Gemini, so "Artikel mit Gemini einordnen"
 * starts off for them. [hasStoredGeminiKey] says whether a key is stored.
 */
internal class SettingsUpgradeMigration(private val hasStoredGeminiKey: () -> Boolean) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[Keys.upgradeV2Done] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        upgradedRadius(prefs[Keys.radiusMeters])?.let { prefs[Keys.radiusMeters] = it }
        upgradedClassifyWithGemini(prefs[Keys.classifyWithGemini], hasStoredGeminiKey())?.let {
            prefs[Keys.classifyWithGemini] = it
        }
        prefs[Keys.upgradeV2Done] = true
        return prefs
    }

    override suspend fun cleanUp() = Unit

    companion object {
        /** The default radius before the upgrade. */
        const val OLD_DEFAULT_RADIUS_METERS = 500

        /** The radius to store instead of the [stored] one, or null to keep it. */
        fun upgradedRadius(stored: Int?): Int? =
            if (stored == OLD_DEFAULT_RADIUS_METERS) AppSettings.DEFAULT_RADIUS_METERS else null

        /** The value to store for "Artikel mit Gemini einordnen" instead of the [stored] one, or null to keep it. */
        fun upgradedClassifyWithGemini(stored: Boolean?, hasStoredKey: Boolean): Boolean? =
            if (stored == null && hasStoredKey) false else null
    }
}

private object Keys {
    val checkEnabled = booleanPreferencesKey("check_enabled")
    val radiusMeters = intPreferencesKey("radius_meters")
    val intervalMinutes = intPreferencesKey("interval_minutes")
    val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
    val quietStartMinute = intPreferencesKey("quiet_start_minute")
    val quietEndMinute = intPreferencesKey("quiet_end_minute")
    val respectOpeningHours = booleanPreferencesKey("respect_opening_hours")
    val cooldownHours = intPreferencesKey("cooldown_hours")
    val geminiModel = stringPreferencesKey("gemini_model")
    val classifyWithGemini = booleanPreferencesKey("classify_with_gemini")
    val batterySetupDone = booleanPreferencesKey("battery_setup_done")
    val hideCheckBanner = booleanPreferencesKey("hide_check_banner")
    val fetchLat = doublePreferencesKey("shop_fetch_lat")
    val fetchLon = doublePreferencesKey("shop_fetch_lon")
    val fetchRadius = intPreferencesKey("shop_fetch_radius")
    val fetchAt = longPreferencesKey("shop_fetch_at")

    /** [SettingsUpgradeMigration] ran. */
    val upgradeV2Done = booleanPreferencesKey("upgrade_v2_done")
}
