package de.jw.einkaufsliste.feature.shops

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jw.einkaufsliste.AppContainer
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** What the screen is waiting for. */
enum class NearbyProgress { LOCATING, LOADING_SHOPS }

/** Matches for one location and radius. */
data class NearbyResult(
    val radiusMeters: Int,
    val rows: List<NearbyShopRow>,
    /** No cached shop at all within [radiusMeters]. */
    val noShopsInRadius: Boolean,
) {
    val hasLearnedItems: Boolean get() = rows.any { it.hasLearnedItems }
}

data class NearbyUiState(
    /** Non-null while working; [result] (if any) stays visible meanwhile. */
    val progress: NearbyProgress? = null,
    /** The shopping list has no open items; nothing to look for, but the shops around are still loaded. */
    val listEmpty: Boolean = false,
    /** With [listEmpty]: cached shops within the radius once loaded (for "Nur in diesem Laden"); else null. */
    val shopsInRadius: Int? = null,
    /** The last attempt found no location. */
    val noLocation: Boolean = false,
    val baseRadiusMeters: Int = AppSettings.DEFAULT_RADIUS_METERS,
    val widened: Boolean = false,
    /** German warning when downloading shops failed; the result then comes from the cache. */
    val warning: String? = null,
    val result: NearbyResult? = null,
) {
    val searchRadiusMeters: Int get() = if (widened) widenedRadiusFor(baseRadiusMeters) else baseRadiusMeters

    /** False when the radius setting is already as large as "Weiter weg suchen" would be. */
    val canWiden: Boolean get() = widenedRadiusFor(baseRadiusMeters) > baseRadiusMeters
}

/** Location -> shop download (if needed) -> matching, for the "In der Nähe" screen. */
class NearbyViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NearbyUiState())
    val state: StateFlow<NearbyUiState> = _state.asStateFlow()

    private var started = false
    private var here: GeoPoint? = null
    private var job: Job? = null

    /** [here] was located with only approximate location allowed. */
    private var hereApproximate = false

    /** When the last load finished ([SystemClock.elapsedRealtime]). */
    private var loadedAtMs = 0L

    // What the running job still has to do; carried over when it is replaced by a newer one.
    private var pendingRelocate = false
    private var pendingForce = false

    init {
        viewModelScope.launch {
            container.settingsRepository.settings
                .map { it.radiusMeters }
                .distinctUntilChanged()
                .collect { radius ->
                    if (radius == _state.value.baseRadiusMeters) return@collect
                    _state.update { it.copy(baseRadiusMeters = radius) }
                    if (started) load(relocate = false, force = false)
                }
        }
    }

    /** First load, once the location permission is granted. Later calls do nothing. */
    fun start() {
        if (started) return
        started = true
        load(relocate = true, force = false)
    }

    /** Locates again and downloads shops even when the cache is fresh. */
    fun refresh() {
        started = true
        load(relocate = true, force = true)
    }

    /** After "Kein Standort verfügbar". */
    fun retry() {
        started = true
        load(relocate = true, force = false)
    }

    /**
     * Back on screen (from the background, a shop notification, the system settings or a permission
     * dialog): loads again when the location was missing, the result is a few minutes old (the user
     * may have moved) or precise location was allowed meanwhile.
     */
    fun onResume() {
        val s = _state.value
        if (!started || s.progress != null) return
        val stale = SystemClock.elapsedRealtime() - loadedAtMs > STALE_AFTER_MS
        val preciseNow = hereApproximate && container.locationProvider.hasFinePermission()
        if (s.noLocation || stale || preciseNow) load(relocate = true, force = false)
    }

    fun setWidened(widened: Boolean) {
        if (_state.value.widened == widened) return
        _state.update { it.copy(widened = widened) }
        if (started) load(relocate = false, force = false)
    }

    private fun load(relocate: Boolean, force: Boolean) {
        pendingRelocate = pendingRelocate || relocate
        pendingForce = pendingForce || force
        job?.cancel()
        job = viewModelScope.launch {
            try {
                loadSteps()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(progress = null, warning = "Die Läden in der Nähe konnten nicht ermittelt werden.")
                }
            }
        }
    }

    private suspend fun loadSteps() {
        val settingsRadius = container.settingsRepository.current().radiusMeters
        _state.update { it.copy(baseRadiusMeters = settingsRadius) }

        // With an empty list nothing can match, but the shops are loaded anyway: pinning an item to a
        // shop ("Nur in diesem Laden") picks from the cached ones.
        val listEmpty = container.itemRepository.observeOpenCount().first() == 0
        _state.update { it.copy(listEmpty = listEmpty, shopsInRadius = null) }

        var location = here
        if (pendingRelocate || location == null) {
            _state.update { it.copy(progress = NearbyProgress.LOCATING, noLocation = false) }
            val approximate = !container.locationProvider.hasFinePermission()
            location = locate()
            // A replaced job must not touch the state any more (some suspending calls ignore cancellation).
            currentCoroutineContext().ensureActive()
            pendingRelocate = false
            here = location
            hereApproximate = approximate
            if (location == null) {
                loadedAtMs = SystemClock.elapsedRealtime()
                _state.update { it.copy(progress = null, noLocation = true, warning = null, result = null) }
                return
            }
        }

        _state.update { it.copy(progress = NearbyProgress.LOADING_SHOPS, noLocation = false) }
        val radius = _state.value.searchRadiusMeters
        val refresh = container.shopRepository.ensureShopsCovering(location, radius, force = pendingForce)
        currentCoroutineContext().ensureActive()
        pendingForce = false

        val failed = refresh as? ShopRefreshResult.Failed
        if (listEmpty) {
            val shopCount = container.shopRepository.shopsNear(location, radius.toDouble()).size
            currentCoroutineContext().ensureActive()
            loadedAtMs = SystemClock.elapsedRealtime()
            _state.update {
                it.copy(
                    progress = null,
                    // A failed download only matters when the cache has nothing here either.
                    warning = failed?.message?.takeIf { shopCount == 0 },
                    result = null,
                    shopsInRadius = shopCount,
                )
            }
            return
        }

        val matches = container.matchService.matchesAround(location, radius.toDouble())
        // Only shops here count: the cache may hold shops from elsewhere (home) but none around a holiday location.
        val shopsHere = matches.isNotEmpty() ||
            container.shopRepository.shopsNear(location, radius.toDouble()).isNotEmpty()
        val now = LocalDateTime.now()
        currentCoroutineContext().ensureActive()
        loadedAtMs = SystemClock.elapsedRealtime()
        _state.update {
            if (failed != null && !shopsHere) {
                // Nothing known here: the screen shows the error with "Erneut versuchen".
                it.copy(progress = null, warning = failed.message, result = null)
            } else {
                it.copy(
                    progress = null,
                    warning = failed?.let { f -> "${f.message} Angezeigt werden die zuletzt gespeicherten Läden." },
                    result = NearbyResult(radius, matches.map { match -> match.toRow(now) }, !shopsHere),
                )
            }
        }
    }

    /** The current location, or null (no fix, location off, permission withdrawn meanwhile). */
    private suspend fun locate(): GeoPoint? = try {
        container.locationProvider.currentLocation()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** Older results are redone when the screen is shown again. */
        val STALE_AFTER_MS: Long = TimeUnit.MINUTES.toMillis(2)
    }
}
