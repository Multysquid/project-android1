package de.jw.einkaufsliste.core.osm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GermanHolidaysTest {
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun assertHoliday(text: String) =
        assertTrue(text, GermanHolidays.isNationwideHoliday(date(text)))

    private fun assertNoHoliday(text: String) =
        assertFalse(text, GermanHolidays.isNationwideHoliday(date(text)))

    @Test
    fun easterSundayOfSeveralYears() {
        mapOf(
            1583 to "1583-04-10",
            2000 to "2000-04-23",
            2008 to "2008-03-23",
            2011 to "2011-04-24",
            2019 to "2019-04-21",
            2024 to "2024-03-31",
            2025 to "2025-04-20",
            2026 to "2026-04-05",
            2027 to "2027-03-28",
            2028 to "2028-04-16",
        ).forEach { (year, easter) -> assertEquals("$year", date(easter), GermanHolidays.easterSunday(year)) }
    }

    @Test
    fun earliestAndLatestEaster() {
        assertEquals(date("1818-03-22"), GermanHolidays.easterSunday(1818))
        assertEquals(date("2285-03-22"), GermanHolidays.easterSunday(2285))
        assertEquals(date("1943-04-25"), GermanHolidays.easterSunday(1943))
        assertEquals(date("2038-04-25"), GermanHolidays.easterSunday(2038))
    }

    @Test
    fun yearsWhereThePlainGaussFormulaIsAWeekLate() {
        assertEquals(date("1954-04-18"), GermanHolidays.easterSunday(1954))
        assertEquals(date("1981-04-19"), GermanHolidays.easterSunday(1981))
        assertEquals(date("2049-04-18"), GermanHolidays.easterSunday(2049))
        assertEquals(date("2076-04-19"), GermanHolidays.easterSunday(2076))
    }

    @Test
    fun easterIsAlwaysASundayBetween22MarchAnd25April() {
        for (year in GermanHolidays.FIRST_GREGORIAN_YEAR..2600) {
            val easter = GermanHolidays.easterSunday(year)
            assertEquals("$year", DayOfWeek.SUNDAY, easter.dayOfWeek)
            assertTrue("$year: $easter", easter >= LocalDate.of(year, 3, 22) && easter <= LocalDate.of(year, 4, 25))
        }
    }

    @Test
    fun easterBasedHolidays() {
        // 2026: Easter Sunday on 5 April
        assertHoliday("2026-04-03") // Karfreitag
        assertHoliday("2026-04-06") // Ostermontag
        assertHoliday("2026-05-14") // Christi Himmelfahrt
        assertHoliday("2026-05-25") // Pfingstmontag
        // 2025: Easter Sunday on 20 April
        assertHoliday("2025-04-18")
        assertHoliday("2025-04-21")
        assertHoliday("2025-05-29")
        assertHoliday("2025-06-09")
        // 2027: Easter Sunday on 28 March
        assertHoliday("2027-03-26")
        assertHoliday("2027-03-29")
        assertHoliday("2027-05-06")
        assertHoliday("2027-05-17")
        // The 2026 dates are no holidays in 2025 or 2027
        assertNoHoliday("2025-04-03")
        assertNoHoliday("2027-04-06")
        assertNoHoliday("2025-05-14")
        assertNoHoliday("2027-05-25")
    }

    @Test
    fun easterBasedHolidaysFallOnTheirWeekdays() {
        for (year in 2000..2100) {
            val holidays = GermanHolidays.nationwide(year).keys
            val easter = GermanHolidays.easterSunday(year)
            val karfreitag = easter.minusDays(2)
            val himmelfahrt = easter.plusDays(39)
            val pfingstmontag = easter.plusDays(50)
            assertEquals(DayOfWeek.FRIDAY, karfreitag.dayOfWeek)
            assertEquals(DayOfWeek.THURSDAY, himmelfahrt.dayOfWeek)
            assertEquals(DayOfWeek.MONDAY, pfingstmontag.dayOfWeek)
            assertTrue("$year", holidays.containsAll(listOf(karfreitag, easter.plusDays(1), himmelfahrt, pfingstmontag)))
        }
    }

    @Test
    fun allNationwideHolidaysOf2026() {
        assertEquals(
            listOf(
                date("2026-01-01") to "Neujahr",
                date("2026-04-03") to "Karfreitag",
                date("2026-04-06") to "Ostermontag",
                date("2026-05-01") to "Tag der Arbeit",
                date("2026-05-14") to "Christi Himmelfahrt",
                date("2026-05-25") to "Pfingstmontag",
                date("2026-10-03") to "Tag der Deutschen Einheit",
                date("2026-12-25") to "1. Weihnachtstag",
                date("2026-12-26") to "2. Weihnachtstag",
            ),
            GermanHolidays.nationwide(2026).toList(),
        )
    }

    @Test
    fun fixedHolidays() {
        for (year in listOf(2024, 2026, 2030)) {
            assertHoliday("$year-01-01")
            assertHoliday("$year-05-01")
            assertHoliday("$year-10-03")
            assertHoliday("$year-12-25")
            assertHoliday("$year-12-26")
        }
    }

    @Test
    fun holidaysOnTheSameDayShareOneEntry() {
        val holidays = GermanHolidays.nationwide(2008)
        assertEquals(8, holidays.size)
        assertEquals("Tag der Arbeit und Christi Himmelfahrt", holidays[date("2008-05-01")])
        assertEquals(holidays.keys.sorted(), holidays.keys.toList())
    }

    @Test
    fun regionalAndOrdinaryDaysAreNoHolidays() {
        assertNoHoliday("2026-01-06") // Heilige Drei Könige (regional)
        assertNoHoliday("2026-03-08") // Frauentag (Berlin, Mecklenburg-Vorpommern)
        assertNoHoliday("2026-04-05") // Ostersonntag (Brandenburg)
        assertNoHoliday("2026-05-24") // Pfingstsonntag (Brandenburg)
        assertNoHoliday("2026-06-04") // Fronleichnam (regional)
        assertNoHoliday("2026-08-15") // Mariä Himmelfahrt (regional)
        assertNoHoliday("2026-09-20") // Weltkindertag (Thüringen)
        assertNoHoliday("2026-10-31") // Reformationstag (regional)
        assertNoHoliday("2026-11-01") // Allerheiligen (regional)
        assertNoHoliday("2026-11-18") // Buß- und Bettag (Sachsen)
        assertNoHoliday("2026-12-24") // Heiligabend
        assertNoHoliday("2026-12-31") // Silvester
        assertNoHoliday("2026-04-07")
        assertNoHoliday("2026-10-19")
    }

    @Test
    fun noHolidaysBeforeTheGregorianCalendar() {
        assertNoHoliday("1500-01-01")
        assertHoliday("1583-01-01")
        assertTrue(runCatching { GermanHolidays.easterSunday(1582) }.exceptionOrNull() is IllegalArgumentException)
    }
}
