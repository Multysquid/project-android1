package de.jw.einkaufsliste.core.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OpeningHoursTest {
    /** A moment in the week of Monday 2024-01-08, which has no public holidays. */
    private fun at(day: DayOfWeek, time: String): LocalDateTime =
        LocalDateTime.of(MONDAY.plusDays((day.value - 1).toLong()), LocalTime.parse(time))

    private fun open(spec: String?, day: DayOfWeek, time: String): Boolean? = OpeningHours.isOpen(spec, at(day, time))

    private fun assertOpen(spec: String, day: DayOfWeek, time: String) =
        assertEquals("$spec at $day $time", true, open(spec, day, time))

    private fun assertClosed(spec: String, day: DayOfWeek, time: String) =
        assertEquals("$spec at $day $time", false, open(spec, day, time))

    private fun assertUnknown(spec: String?) =
        assertNull("$spec", open(spec, DayOfWeek.WEDNESDAY, "12:00"))

    /** [dateTime] like `2026-12-25T11:00`, for rules that depend on public holidays. */
    private fun assertOpenOn(spec: String, dateTime: String) =
        assertEquals("$spec at $dateTime", true, OpeningHours.isOpen(spec, LocalDateTime.parse(dateTime)))

    private fun assertClosedOn(spec: String, dateTime: String) =
        assertEquals("$spec at $dateTime", false, OpeningHours.isOpen(spec, LocalDateTime.parse(dateTime)))

    @Test
    fun weekOfTestIsRight() {
        assertEquals(DayOfWeek.MONDAY, MONDAY.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, at(DayOfWeek.SATURDAY, "01:00").dayOfWeek)
        for (offset in -1L..6L) assertFalse(GermanHolidays.isNationwideHoliday(MONDAY.plusDays(offset)))
    }

    @Test
    fun missingSpecIsUnknown() {
        assertUnknown(null)
        assertUnknown("")
        assertUnknown("   ")
    }

    @Test
    fun alwaysOpen() {
        for (day in DayOfWeek.values()) {
            assertOpen("24/7", day, "00:00")
            assertOpen("24/7", day, "12:34")
            assertOpen("24/7", day, "23:59")
        }
        assertOpen("  24/7  ", DayOfWeek.SUNDAY, "03:00")
    }

    @Test
    fun supermarketMondayToSaturday() {
        val spec = "Mo-Sa 07:00-22:00"
        assertClosed(spec, DayOfWeek.MONDAY, "06:59")
        assertOpen(spec, DayOfWeek.MONDAY, "07:00")
        assertOpen(spec, DayOfWeek.WEDNESDAY, "13:30")
        assertOpen(spec, DayOfWeek.SATURDAY, "21:59")
        assertClosed(spec, DayOfWeek.SATURDAY, "22:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "12:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "01:00")
    }

    @Test
    fun weekdaysSaturdayAndSundayOff() {
        val spec = "Mo-Fr 08:00-20:00; Sa 08:00-18:00; Su off"
        assertOpen(spec, DayOfWeek.FRIDAY, "19:30")
        assertClosed(spec, DayOfWeek.FRIDAY, "20:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "17:59")
        assertClosed(spec, DayOfWeek.SATURDAY, "19:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "10:00")
        assertClosed(spec, DayOfWeek.MONDAY, "07:59")
    }

    @Test
    fun bakeryWithSundayMorning() {
        val spec = "Mo-Fr 06:00-18:00; Sa 06:00-13:00; Su 07:00-11:00"
        assertOpen(spec, DayOfWeek.TUESDAY, "06:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "12:59")
        assertClosed(spec, DayOfWeek.SATURDAY, "13:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "06:59")
        assertOpen(spec, DayOfWeek.SUNDAY, "07:30")
        assertClosed(spec, DayOfWeek.SUNDAY, "11:00")
    }

    @Test
    fun allDayEveryDay() {
        val spec = "Mo-Su 00:00-24:00"
        for (day in DayOfWeek.values()) {
            assertOpen(spec, day, "00:00")
            assertOpen(spec, day, "23:59")
        }
    }

    @Test
    fun pastMidnightAppliesToEarlyHoursOfNextDay() {
        val spec = "Fr-Sa 18:00-02:00"
        assertClosed(spec, DayOfWeek.FRIDAY, "17:59")
        assertOpen(spec, DayOfWeek.FRIDAY, "18:00")
        assertOpen(spec, DayOfWeek.FRIDAY, "23:59")
        assertClosed(spec, DayOfWeek.FRIDAY, "01:00") // Thursday has no hours
        assertOpen(spec, DayOfWeek.SATURDAY, "01:00") // from Friday
        assertClosed(spec, DayOfWeek.SATURDAY, "02:00")
        assertClosed(spec, DayOfWeek.SATURDAY, "12:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "20:00")
        assertOpen(spec, DayOfWeek.SUNDAY, "01:00") // from Saturday
        assertClosed(spec, DayOfWeek.SUNDAY, "02:00")
        assertClosed(spec, DayOfWeek.MONDAY, "01:00")
    }

    @Test
    fun pastMidnightFromSundayWrapsToMonday() {
        val spec = "Su 20:00-03:00"
        assertOpen(spec, DayOfWeek.MONDAY, "02:59")
        assertClosed(spec, DayOfWeek.MONDAY, "03:00")
    }

    @Test
    fun extendedEndTimes() {
        val spec = "Fr 18:00-26:00"
        assertOpen(spec, DayOfWeek.FRIDAY, "23:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "01:59")
        assertClosed(spec, DayOfWeek.SATURDAY, "02:00")
    }

    @Test
    fun endAtMidnight() {
        val spec = "Mo 22:00-24:00"
        assertOpen(spec, DayOfWeek.MONDAY, "23:59")
        assertClosed(spec, DayOfWeek.TUESDAY, "00:00")
    }

    @Test
    fun laterRuleReplacesTimesOfItsDays() {
        val spec = "Mo-Fr 08:00-18:00; We 08:00-12:00"
        assertOpen(spec, DayOfWeek.WEDNESDAY, "10:00")
        assertClosed(spec, DayOfWeek.WEDNESDAY, "13:00")
        assertOpen(spec, DayOfWeek.THURSDAY, "13:00")
        assertOpen(spec, DayOfWeek.TUESDAY, "17:00")
    }

    @Test
    fun laterOffRuleClosesItsDays() {
        val spec = "Mo-Sa 08:00-20:00; We off"
        assertClosed(spec, DayOfWeek.WEDNESDAY, "10:00")
        assertOpen(spec, DayOfWeek.THURSDAY, "10:00")
        assertClosed("Mo-Sa 08:00-20:00; We closed", DayOfWeek.WEDNESDAY, "10:00")
    }

    @Test
    fun laterRuleWithoutWeekdaysReplacesAllDays() {
        val spec = "Mo-Fr 08:00-18:00; 10:00-12:00"
        assertClosed(spec, DayOfWeek.MONDAY, "09:00")
        assertOpen(spec, DayOfWeek.MONDAY, "11:00")
        assertOpen(spec, DayOfWeek.SUNDAY, "11:00")
    }

    @Test
    fun earlierRuleDoesNotOverrideLaterOne() {
        val spec = "Sa 10:00-12:00; Mo-Su 08:00-20:00"
        assertOpen(spec, DayOfWeek.SATURDAY, "15:00")
    }

    @Test
    fun overnightHoursSurviveNextDayOverride() {
        val spec = "Fr-Sa 18:00-02:00; Su off"
        assertOpen(spec, DayOfWeek.SUNDAY, "01:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "19:00")

        val daily = "Mo-Su 08:00-02:00; We off"
        assertOpen(daily, DayOfWeek.WEDNESDAY, "01:00") // Tuesday's night
        assertClosed(daily, DayOfWeek.WEDNESDAY, "10:00")
        assertClosed(daily, DayOfWeek.THURSDAY, "01:00") // Wednesday was off
        assertOpen(daily, DayOfWeek.THURSDAY, "10:00")
    }

    @Test
    fun ruleWithoutWeekdaysAppliesToEveryDay() {
        val spec = "10:00-18:00"
        for (day in DayOfWeek.values()) {
            assertOpen(spec, day, "12:00")
            assertClosed(spec, day, "09:00")
        }
        assertClosed("08:00-20:00; Su off", DayOfWeek.SUNDAY, "10:00")
        assertOpen("08:00-20:00; Su off", DayOfWeek.MONDAY, "10:00")
    }

    @Test
    fun severalTimeRanges() {
        val spec = "Mo-Fr 08:00-12:00,14:00-18:30"
        assertOpen(spec, DayOfWeek.MONDAY, "11:59")
        assertClosed(spec, DayOfWeek.MONDAY, "13:00")
        assertOpen(spec, DayOfWeek.MONDAY, "18:29")
        assertClosed(spec, DayOfWeek.MONDAY, "18:30")
        assertClosed(spec, DayOfWeek.SATURDAY, "10:00")
    }

    @Test
    fun wrappingWeekdayRange() {
        val spec = "Sa-Mo 10:00-14:00"
        assertOpen(spec, DayOfWeek.SATURDAY, "12:00")
        assertOpen(spec, DayOfWeek.SUNDAY, "12:00")
        assertOpen(spec, DayOfWeek.MONDAY, "12:00")
        assertClosed(spec, DayOfWeek.TUESDAY, "12:00")
        assertClosed(spec, DayOfWeek.FRIDAY, "12:00")
    }

    @Test
    fun weekdayListsAndMixedSelectors() {
        val list = "Mo,We,Fr 09:00-12:00"
        assertOpen(list, DayOfWeek.MONDAY, "10:00")
        assertClosed(list, DayOfWeek.TUESDAY, "10:00")
        assertOpen(list, DayOfWeek.WEDNESDAY, "10:00")
        assertClosed(list, DayOfWeek.THURSDAY, "10:00")
        assertOpen(list, DayOfWeek.FRIDAY, "10:00")

        val mixed = "Mo-We,Fr 09:00-17:00"
        assertOpen(mixed, DayOfWeek.TUESDAY, "10:00")
        assertClosed(mixed, DayOfWeek.THURSDAY, "10:00")
        assertOpen(mixed, DayOfWeek.FRIDAY, "10:00")
        assertClosed(mixed, DayOfWeek.SATURDAY, "10:00")
    }

    @Test
    fun publicHolidayRulesLeaveOrdinaryDaysAlone() {
        assertOpen("Mo-Sa 08:00-20:00; PH off", DayOfWeek.MONDAY, "10:00")
        assertOpen("Mo-Fr 08:00-18:00; PH 10:00-12:00", DayOfWeek.MONDAY, "09:00")
        val spec = "Mo-Sa 08:00-20:00; Su,PH off"
        assertOpen(spec, DayOfWeek.SATURDAY, "10:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "10:00")
        assertOpen("Mo-Fr 09:00-18:00; Sa 10:00-14:00; Su,PH closed", DayOfWeek.SATURDAY, "13:00")
        assertOpen("PH,Su 10:00-12:00", DayOfWeek.SUNDAY, "11:00")
        assertClosed("PH,Su 10:00-12:00", DayOfWeek.MONDAY, "11:00")
    }

    @Test
    fun onlyPublicHolidayRulesAreUnknown() {
        assertUnknown("PH off")
        assertUnknown("PH 10:00-12:00")
        assertUnknown("PH off; PH 10:00-12:00")
        assertNull(OpeningHours.isOpen("PH off", LocalDateTime.parse("2026-12-25T11:00")))
        assertNull(OpeningHours.isOpen("PH 10:00-12:00", LocalDateTime.parse("2026-12-25T11:00")))
        assertFalse(OpeningHours.isSupported("PH off"))
    }

    @Test
    fun publicHolidayOffClosesOnNationwideHolidays() {
        val spec = "Mo-Sa 08:00-20:00; PH off"
        assertClosedOn(spec, "2026-01-01T11:00") // Neujahr, Thursday
        assertClosedOn(spec, "2026-04-03T11:00") // Karfreitag
        assertClosedOn(spec, "2026-04-06T11:00") // Ostermontag
        assertClosedOn(spec, "2026-05-01T11:00") // Tag der Arbeit, Friday
        assertClosedOn(spec, "2026-05-14T11:00") // Christi Himmelfahrt
        assertClosedOn(spec, "2026-05-25T11:00") // Pfingstmontag
        assertClosedOn(spec, "2026-10-03T11:00") // Tag der Deutschen Einheit, Saturday
        assertClosedOn(spec, "2026-12-25T11:00") // 1. Weihnachtstag, Friday
        assertClosedOn(spec, "2026-12-26T11:00") // 2. Weihnachtstag, Saturday
        assertOpenOn(spec, "2026-04-07T11:00")
        assertOpenOn(spec, "2026-12-24T11:00") // Heiligabend is no public holiday
        assertOpenOn(spec, "2026-12-28T11:00")
        assertOpenOn(spec, "2026-06-04T11:00") // Fronleichnam is regional and not known
        assertClosedOn("Mo-Sa 08:00-20:00; PH closed", "2026-12-25T11:00")
        assertClosedOn("Mo-Sa 08:00-20:00; ph off", "2026-12-25T11:00")
    }

    @Test
    fun sundayAndPublicHolidayOff() {
        val spec = "Mo-Sa 08:00-20:00; Su,PH off"
        assertOpenOn(spec, "2026-12-24T11:00")
        assertClosedOn(spec, "2026-12-25T11:00")
        assertClosedOn(spec, "2026-12-26T11:00")
        assertClosedOn(spec, "2026-12-27T11:00") // Sunday
        assertOpenOn(spec, "2026-12-28T11:00")
        val saturday = "Mo-Fr 09:00-18:00; Sa 10:00-14:00; Su, PH closed"
        assertClosedOn(saturday, "2026-10-03T13:00")
        assertOpenOn(saturday, "2026-10-10T13:00")
    }

    @Test
    fun publicHolidayHours() {
        val spec = "Mo-Fr 08:00-18:00; PH 10:00-14:00"
        // Christi Himmelfahrt 2026 is a Thursday
        assertClosedOn(spec, "2026-05-14T09:00")
        assertOpenOn(spec, "2026-05-14T10:00")
        assertOpenOn(spec, "2026-05-14T13:59")
        assertClosedOn(spec, "2026-05-14T14:00")
        assertClosedOn(spec, "2026-05-14T16:00")
        assertOpenOn(spec, "2026-05-21T09:00")
        assertOpenOn(spec, "2026-05-21T16:00")
        // PH matches whatever the weekday, also on days without ordinary hours
        assertOpenOn(spec, "2026-10-03T11:00") // Saturday
        assertClosedOn(spec, "2026-10-10T11:00")
        assertOpenOn(spec, "2027-12-26T11:00") // Sunday
        assertClosedOn(spec, "2027-12-19T11:00")
    }

    @Test
    fun publicHolidayListedWithWeekdays() {
        val spec = "PH,Su 10:00-12:00"
        assertOpenOn(spec, "2026-04-03T11:00") // Karfreitag
        assertClosedOn(spec, "2026-04-03T13:00")
        assertClosedOn(spec, "2026-04-10T11:00") // ordinary Friday
        assertOpenOn(spec, "2026-04-05T11:00") // Ostersonntag, a Sunday anyway
    }

    @Test
    fun laterRulesOverrideEarlierOnesOnHolidaysToo() {
        val spec = "Mo-Sa 08:00-20:00; PH off; Sa 08:00-14:00"
        assertOpenOn(spec, "2026-10-03T11:00") // a holiday Saturday: the Sa rule comes last
        assertClosedOn(spec, "2026-10-03T15:00")
        assertClosedOn(spec, "2026-12-25T11:00")
        val early = "PH off; Mo-Sa 08:00-20:00"
        assertOpenOn(early, "2026-12-25T11:00")
        assertClosedOn(early, "2026-12-27T11:00")
    }

    @Test
    fun additionalPublicHolidayRules() {
        val off = "Mo-Fr 08:00-18:00, PH off"
        assertClosedOn(off, "2026-12-25T10:00")
        assertOpenOn(off, "2026-12-24T10:00")
        val adds = "Mo-Fr 08:00-12:00, PH 14:00-16:00"
        assertOpenOn(adds, "2026-04-06T10:00") // Ostermontag keeps Monday's hours and gets more
        assertOpenOn(adds, "2026-04-06T15:00")
        assertClosedOn(adds, "2026-04-07T15:00")
    }

    @Test
    fun pastMidnightAroundPublicHolidays() {
        val spec = "Mo-Su 18:00-02:00; PH off"
        assertOpenOn(spec, "2026-12-25T01:00") // Heiligabend's night runs into the holiday
        assertClosedOn(spec, "2026-12-25T19:00")
        assertClosedOn(spec, "2026-12-26T01:00") // the holiday before had no hours
        assertClosedOn(spec, "2026-12-26T19:00")
        assertClosedOn(spec, "2026-12-27T01:00")
        assertOpenOn(spec, "2026-12-27T19:00")
        assertOpenOn(spec, "2026-12-28T01:00")

        val late = "Mo-Sa 08:00-20:00; PH 18:00-02:00"
        assertClosedOn(late, "2026-04-06T10:00") // Ostermontag
        assertOpenOn(late, "2026-04-06T23:00")
        assertOpenOn(late, "2026-04-07T01:59") // from the holiday
        assertClosedOn(late, "2026-04-07T02:00")
        assertOpenOn(late, "2026-04-07T10:00")
        assertClosedOn(late, "2026-04-08T01:00")
    }

    @Test
    fun alwaysOpenExceptPublicHolidays() {
        val spec = "24/7; PH off"
        assertOpenOn(spec, "2026-10-02T23:59")
        assertClosedOn(spec, "2026-10-03T00:00")
        assertClosedOn(spec, "2026-10-03T23:59")
        assertOpenOn(spec, "2026-10-04T00:00")
    }

    @Test
    fun unsupportedPublicHolidaySyntaxIsUnknown() {
        listOf(
            "Mo-Sa 08:00-20:00; PH +1 day off",
            "Mo-Sa 08:00-20:00; PH -1 day 08:00-12:00",
            "Mo-Sa 08:00-20:00; PH Mo off",
            "Mo-Sa 08:00-20:00; PH-Mo off",
            "Mo-Sa 08:00-20:00; PH",
        ).forEach(::assertUnknown)
    }

    @Test
    fun alwaysClosed() {
        assertClosed("off", DayOfWeek.MONDAY, "10:00")
        assertClosed("closed", DayOfWeek.SATURDAY, "10:00")
    }

    @Test
    fun alwaysOpenWithExceptions() {
        assertClosed("24/7; Su off", DayOfWeek.SUNDAY, "10:00")
        assertOpen("24/7; Su off", DayOfWeek.MONDAY, "03:00")
        assertOpen("24/7; PH off", DayOfWeek.SUNDAY, "03:00")
    }

    @Test
    fun whitespaceAndDashTolerance() {
        val spec = "  Mo - Fr  08:00 - 20:00 ;  Sa 09:00-14:00 ; "
        assertOpen(spec, DayOfWeek.MONDAY, "08:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "13:59")
        assertClosed(spec, DayOfWeek.SATURDAY, "14:00")
        assertOpen("Mo–Fr 08:00–20:00", DayOfWeek.TUESDAY, "12:00")
        assertOpen("Mo-Fr 08:00-12:00 , 14:00-18:00", DayOfWeek.TUESDAY, "15:00")
        assertOpen("Mo-Fr 08:00-20:00", DayOfWeek.TUESDAY, "12:00")
        assertOpen("Mo-Fr 08:00-20:00;;Sa 10:00-12:00", DayOfWeek.SATURDAY, "11:00")
    }

    @Test
    fun lenientVariants() {
        assertOpen("Mo-Fr: 08:00-18:00", DayOfWeek.MONDAY, "10:00")
        assertOpen("mo-fr 08:00-18:00", DayOfWeek.MONDAY, "10:00")
        assertOpen("Mo-Fr 8:00-18:00", DayOfWeek.MONDAY, "09:00")
        assertClosed("Mo-Fr 8:00-18:00", DayOfWeek.MONDAY, "07:59")
        assertClosed("Mo-Sa 08:00-20:00; SU OFF", DayOfWeek.SUNDAY, "10:00")
    }

    @Test
    fun additionalRulesAfterComma() {
        val spec = "Mo-Fr 08:00-18:00, Sa 09:00-13:00"
        assertOpen(spec, DayOfWeek.MONDAY, "10:00")
        assertOpen(spec, DayOfWeek.SATURDAY, "10:00")
        assertClosed(spec, DayOfWeek.SATURDAY, "14:00")
        assertClosed(spec, DayOfWeek.SUNDAY, "10:00")

        val adds = "Mo-Fr 08:00-12:00, We 14:00-16:00"
        assertOpen(adds, DayOfWeek.WEDNESDAY, "10:00")
        assertOpen(adds, DayOfWeek.WEDNESDAY, "15:00")
        assertClosed(adds, DayOfWeek.TUESDAY, "15:00")

        assertClosed("Mo-Fr 08:00-18:00, We off", DayOfWeek.WEDNESDAY, "10:00")
        assertOpen("Mo-Fr 08:00-18:00, PH off", DayOfWeek.WEDNESDAY, "10:00")
    }

    @Test
    fun unsupportedSyntaxIsUnknown() {
        listOf(
            "Jan-Mar Mo-Fr 08:00-18:00",
            "Mo-Fr 08:00-18:00; Dec 24 08:00-12:00",
            "Mo-Fr 08:00-18:00; Dec 25 off",
            "week 1-26 Mo 08:00-12:00",
            "Mo-Fr sunrise-sunset",
            "Mo-Fr 08:00-sunset",
            "Mo-Fr (sunrise+01:00)-18:00",
            "Mo-Fr 08:00+",
            "Mo-Fr 18:00+",
            "Mo-Fr 08:00-18:00 || \"nach Vereinbarung\"",
            "Mo-Fr 08:00-18:00 \"Kommentar\"",
            "\"nach Vereinbarung\"",
            "SH off",
            "Mo-Fr 08:00-18:00; SH off",
            "Mo-Fr 08:00-18:00; SH,PH off",
            "2024 Mo-Fr 08:00-12:00",
            "Mo[1] 08:00-12:00",
            "Mo-Fr",
            "Mo-Fr; Sa 08:00-12:00",
            "Di-Fr 08:00-18:00",
            "Mo-Fr 8-18",
            "Mo-Fr 08.00-18.00",
            "Mo-Fr 24:00-26:00",
            "Mo-Fr 08:60-09:00",
            "Mo-Fr 08:00-49:00",
            "Mo-Fr 08:000-12:00",
            "Mo-Fr 08:00-18:00 open",
            "Mo-Fr 08:00-12:00 off",
            "Mo-Fr 08:00-12:00 14:00-18:00",
            "Mo-Fr 08:00-18:00 Sa 09:00-12:00",
            "Mo-Fr 08:00-18:00, 09:00",
            "Mo-Fr 08:00-18:00, off",
            "Mo-Fr 08:00-18:00, 24/7",
            "Mo, off",
            "easter off",
            "unknown",
            "Mo-Fr 08:00 bis 18:00",
            "garbage",
            ";",
            "24/7 Mo off",
            "Mo-Fr 08:00-",
            "Mo-",
        ).forEach(::assertUnknown)
    }

    @Test
    fun isSupportedMatchesIsOpen() {
        assertTrue(OpeningHours.isSupported("Mo-Sa 07:00-22:00"))
        assertTrue(OpeningHours.isSupported("24/7"))
        assertFalse(OpeningHours.isSupported("Mo-Fr 08:00-18:00; SH off"))
        assertFalse(OpeningHours.isSupported(null))
    }

    @Test
    fun secondsAreIgnored() {
        val time = LocalDateTime.of(MONDAY, LocalTime.of(21, 59, 59))
        assertEquals(true, OpeningHours.isOpen("Mo-Sa 07:00-22:00", time))
    }

    private companion object {
        val MONDAY: LocalDate = LocalDate.of(2024, 1, 8)
    }
}
