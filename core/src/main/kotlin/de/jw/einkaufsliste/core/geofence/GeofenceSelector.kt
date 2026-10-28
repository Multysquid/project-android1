package de.jw.einkaufsliste.core.geofence

import de.jw.einkaufsliste.core.geo.GeoMath
import de.jw.einkaufsliste.core.model.GeoPoint
import de.jw.einkaufsliste.core.model.ShopMatch

/** Picks which matching shops get a geofence (Android allows at most 100 per app). */
object GeofenceSelector {
    const val MAX_GEOFENCES = 90

    /**
     * The nearest [max] matches to [here], de-duplicated by shop id, nearest first.
     *
     * Distances are measured from [here] again, since [ShopMatch.distanceMeters] may
     * stem from an older location. Of matches with the same shop id the first is kept.
     */
    fun select(here: GeoPoint, matches: List<ShopMatch>, max: Int = MAX_GEOFENCES): List<ShopMatch> {
        if (max <= 0 || matches.isEmpty()) return emptyList()
        return matches
            .distinctBy { it.shop.id }
            .map { it to GeoMath.distanceMeters(here, it.shop.point) }
            .sortedWith(compareBy<Pair<ShopMatch, Double>> { it.second }.thenBy { it.first.shop.id })
            .take(max)
            .map { it.first }
    }
}
