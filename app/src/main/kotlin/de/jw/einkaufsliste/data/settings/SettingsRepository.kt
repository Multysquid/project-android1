package de.jw.einkaufsliste.data.settings

import android.content.Context
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

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
    val batterySetupDone: Boolean = false,
    /** The user hid the list's card that says the shop check is off. */
    val hideCheckBanner: Boolean = false,
) {
    val quietHours: QuietHours? get() = if (quietHoursEnabled) QuietHours(quietStartMinute, quietEndMinute) else null

    companion object {
        const val DEFAULT_RADIUS_METERS = 500
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
            batterySetupDone = this[Keys.batterySetupDone] ?: defaults.batterySetupDone,
            hideCheckBanner = this[Keys.hideCheckBanner] ?: defaults.hideCheckBanner,
        )
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
        val batterySetupDone = booleanPreferencesKey("battery_setup_done")
        val hideCheckBanner = booleanPreferencesKey("hide_check_banner")
        val fetchLat = doublePreferencesKey("shop_fetch_lat")
        val fetchLon = doublePreferencesKey("shop_fetch_lon")
        val fetchRadius = intPreferencesKey("shop_fetch_radius")
        val fetchAt = longPreferencesKey("shop_fetch_at")
    }
}
