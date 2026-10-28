package de.jw.einkaufsliste.core.schedule

/** How the periodic shop check is run on the device. */
enum class CheckMode {
    /** WorkManager periodic work; Android does not allow less than 15 minutes. */
    WORK_MANAGER,

    /** Location foreground service with a permanent notification, for intervals below 15 minutes. */
    FOREGROUND_SERVICE,
}

object CheckSchedule {
    /** Intervals offered in the settings, in minutes. */
    val INTERVAL_OPTIONS: List<Int> = listOf(1, 2, 5, 10, 15, 30, 60)
    const val DEFAULT_INTERVAL_MINUTES = 15
    const val WORK_MANAGER_MIN_MINUTES = 15

    fun modeFor(intervalMinutes: Int): CheckMode =
        if (coerce(intervalMinutes) < WORK_MANAGER_MIN_MINUTES) CheckMode.FOREGROUND_SERVICE
        else CheckMode.WORK_MANAGER

    /** Snaps any stored value to the nearest offered option. */
    fun coerce(intervalMinutes: Int): Int =
        INTERVAL_OPTIONS.minByOrNull { kotlin.math.abs(it - intervalMinutes) } ?: DEFAULT_INTERVAL_MINUTES
}
