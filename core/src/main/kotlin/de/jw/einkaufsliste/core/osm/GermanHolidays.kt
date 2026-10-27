package de.jw.einkaufsliste.core.osm

import java.time.LocalDate
import java.time.Month

/**
 * The public holidays that apply in all of Germany, computed locally (no network, no data files).
 *
 * Only the nine nationwide holidays are known: Neujahr, Karfreitag, Ostermontag, Tag der Arbeit, Christi
 * Himmelfahrt, Pfingstmontag, Tag der Deutschen Einheit and the 1. and 2. Weihnachtstag, with today's rules for
 * every year. Regional holidays (Heilige Drei Könige, Fronleichnam, Mariä Himmelfahrt, Reformationstag,
 * Allerheiligen, Buß- und Bettag, Frauentag, Weltkindertag, Augsburger Friedensfest, Oster- and Pfingstsonntag in
 * Brandenburg) and one-off holidays are not known, because the app does not know the state or town a shop is in;
 * on those days `PH` rules of [OpeningHours] do not apply and a shop keeps its hours for that weekday. Heiligabend
 * and Silvester are not public holidays.
 */
object GermanHolidays {
    /** First year of the Gregorian calendar; [easterSunday] is defined from then on. */
    const val FIRST_GREGORIAN_YEAR = 1583

    /** True when [date] is one of the nationwide public holidays; always false before [FIRST_GREGORIAN_YEAR]. */
    fun isNationwideHoliday(date: LocalDate): Boolean =
        date.year >= FIRST_GREGORIAN_YEAR && date in nationwide(date.year)

    /**
     * The nationwide public holidays of [year] with their German names, in calendar order. Two holidays on the
     * same date share one entry (1 May 2008 was also Christi Himmelfahrt: "Tag der Arbeit und Christi Himmelfahrt").
     */
    fun nationwide(year: Int): Map<LocalDate, String> {
        val easter = easterSunday(year)
        return listOf(
            LocalDate.of(year, Month.JANUARY, 1) to "Neujahr",
            easter.minusDays(2) to "Karfreitag",
            easter.plusDays(1) to "Ostermontag",
            LocalDate.of(year, Month.MAY, 1) to "Tag der Arbeit",
            easter.plusDays(39) to "Christi Himmelfahrt",
            easter.plusDays(50) to "Pfingstmontag",
            LocalDate.of(year, Month.OCTOBER, 3) to "Tag der Deutschen Einheit",
            LocalDate.of(year, Month.DECEMBER, 25) to "1. Weihnachtstag",
            LocalDate.of(year, Month.DECEMBER, 26) to "2. Weihnachtstag",
        )
            .sortedBy { it.first }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.joinToString(" und ") }
    }

    /**
     * Easter Sunday of [year] (from [FIRST_GREGORIAN_YEAR] on) by the anonymous Gregorian computus
     * (Meeus/Jones/Butcher). It is exact for every Gregorian year, including those for which the plain Gauss
     * formula needs its exceptions (1981: 19 April, 2049: 18 April, not a week later).
     */
    fun easterSunday(year: Int): LocalDate {
        require(year >= FIRST_GREGORIAN_YEAR) { "Not a Gregorian year: $year" }
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val monthAndDay = h + l - 7 * m + 114
        return LocalDate.of(year, monthAndDay / 31, monthAndDay % 31 + 1)
    }
}
