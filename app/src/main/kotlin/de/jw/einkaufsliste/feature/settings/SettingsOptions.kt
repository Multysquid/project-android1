package de.jw.einkaufsliste.feature.settings

import java.util.Locale
import kotlin.math.abs

/** Choices and labels of the settings screen. Pure Kotlin, so they can be tested without a device. */
internal object SettingsOptions {
    /** Radius choices for the slider, in meters. */
    val RADIUS_METERS: List<Int> = listOf(100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000)

    /** "Höchstens alle … Stunden pro Laden" choices. */
    val COOLDOWN_HOURS: List<Int> = listOf(1, 2, 3, 6, 12, 24)

    /** Index of the option closest to [value]; ties go to the smaller option. */
    fun nearestIndex(options: List<Int>, value: Int): Int {
        require(options.isNotEmpty()) { "options must not be empty" }
        return options.indices.minBy { abs(options[it] - value) }
    }

    /** Option at a slider [position] (a float index), clamped to the valid range. */
    fun optionAt(options: List<Int>, position: Float): Int =
        options[Math.round(position).coerceIn(0, options.lastIndex)]

    /** "500 m", "1 km", "1,5 km": whole kilometers without a trailing ",0". */
    fun radiusLabel(meters: Int): String = when {
        meters < 1000 -> "$meters m"
        meters % 1000 == 0 -> "${meters / 1000} km"
        else -> String.format(Locale.GERMAN, "%.1f km", meters / 1000.0)
    }

    /** "Jede Minute", "Alle 5 Minuten", "Jede Stunde". */
    fun intervalLabel(minutes: Int): String = when (minutes) {
        1 -> "Jede Minute"
        60 -> "Jede Stunde"
        else -> "Alle $minutes Minuten"
    }

    /** "Höchstens alle 3 Stunden pro Laden". */
    fun cooldownLabel(hours: Int): String =
        if (hours == 1) "Höchstens einmal pro Stunde pro Laden" else "Höchstens alle $hours Stunden pro Laden"

    /** Minute of the day as "07:00". */
    fun formatTime(minuteOfDay: Int): String {
        val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)
    }
}
