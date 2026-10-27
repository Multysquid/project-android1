package de.jw.einkaufsliste.core.notify

import de.jw.einkaufsliste.core.model.QuietHours

/** Rules for when a "shop nearby" notification may be shown. */
object NotificationPolicy {
    private const val MILLIS_PER_HOUR = 3_600_000L

    /**
     * True when [minuteOfDay] (0..1439) falls into [quiet]; handles ranges over midnight. Null means no quiet hours.
     *
     * The start minute is quiet, the end minute is not. Start == end means "no quiet hours".
     */
    fun isQuietTime(minuteOfDay: Int, quiet: QuietHours?): Boolean {
        if (quiet == null) return false
        val start = quiet.startMinute
        val end = quiet.endMinute
        return when {
            start == end -> false
            start < end -> minuteOfDay in start until end
            else -> minuteOfDay >= start || minuteOfDay < end
        }
    }

    /** True when no notification for this shop was shown within the last [cooldownHours]. */
    fun cooldownPassed(nowMillis: Long, lastNotifiedMillis: Long?, cooldownHours: Int): Boolean {
        if (lastNotifiedMillis == null || cooldownHours <= 0) return true
        return nowMillis - lastNotifiedMillis >= cooldownHours * MILLIS_PER_HOUR
    }
}
