package de.jw.einkaufsliste.core.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckScheduleTest {
    @Test
    fun offeredOptionsMapToThemselves() {
        for (option in CheckSchedule.INTERVAL_OPTIONS) assertEquals(option, CheckSchedule.coerce(option))
    }

    @Test
    fun defaultIsAnOfferedOption() {
        assertTrue(CheckSchedule.DEFAULT_INTERVAL_MINUTES in CheckSchedule.INTERVAL_OPTIONS)
    }

    @Test
    fun coerceSnapsToNearestOption() {
        assertEquals(1, CheckSchedule.coerce(0))
        assertEquals(1, CheckSchedule.coerce(-20))
        assertEquals(2, CheckSchedule.coerce(3))
        assertEquals(5, CheckSchedule.coerce(4))
        assertEquals(10, CheckSchedule.coerce(12))
        assertEquals(15, CheckSchedule.coerce(13))
        assertEquals(15, CheckSchedule.coerce(14))
        assertEquals(30, CheckSchedule.coerce(40))
        assertEquals(60, CheckSchedule.coerce(50))
        assertEquals(60, CheckSchedule.coerce(10_000))
    }

    @Test
    fun coerceTieGoesToSmallerOption() {
        assertEquals(30, CheckSchedule.coerce(45))
    }

    @Test
    fun modeForShortIntervalsIsForegroundService() {
        for (minutes in listOf(1, 2, 5, 10)) assertEquals(CheckMode.FOREGROUND_SERVICE, CheckSchedule.modeFor(minutes))
    }

    @Test
    fun modeForLongIntervalsIsWorkManager() {
        for (minutes in listOf(15, 30, 60)) assertEquals(CheckMode.WORK_MANAGER, CheckSchedule.modeFor(minutes))
    }

    @Test
    fun modeUsesCoercedValue() {
        assertEquals(CheckMode.FOREGROUND_SERVICE, CheckSchedule.modeFor(12))
        assertEquals(CheckMode.WORK_MANAGER, CheckSchedule.modeFor(13))
        assertEquals(CheckMode.FOREGROUND_SERVICE, CheckSchedule.modeFor(0))
        assertEquals(CheckMode.WORK_MANAGER, CheckSchedule.modeFor(500))
    }
}
