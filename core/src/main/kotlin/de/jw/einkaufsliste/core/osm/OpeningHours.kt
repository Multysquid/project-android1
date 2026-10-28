package de.jw.einkaufsliste.core.osm

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Evaluates a subset of the OSM `opening_hours` syntax.
 *
 * Supported: `24/7`; rules separated by `;` (a later rule replaces the times of the days it matches) or by `,`
 * before a day selector (additional rule, adds times); weekday selectors (`Mo-Fr`, wrapping `Sa-Mo`, lists
 * `Mo,We,Fr`, mixed `Mo-We,Fr`); the public holiday selector `PH`, alone or in a list (`Su,PH`); time ranges
 * `08:00-12:00,14:00-18:30` (end `24:00`, extended `26:00`, or an end before the start, which runs into the early
 * hours of the next day); `off`/`closed`.
 *
 * `PH` matches the nationwide German public holidays of [GermanHolidays]: a rule applies to a day when it names
 * the day's weekday, or names `PH` and the day is such a holiday. So `Mo-Sa 08:00-20:00; PH off` is closed on
 * Ostermontag, and `Mo-Fr 08:00-18:00; PH 10:00-14:00` has the short hours on holidays. Regional holidays
 * (Fronleichnam, Allerheiligen, …) are not known: on them `PH` rules do not apply and the weekday rules do.
 * A spec made only of `PH` rules says nothing about ordinary days and is unknown. Anything else (months, weeks,
 * dates, `sunrise`, open end `+`, `||`, comments, `SH`, holiday offsets like `PH +1 day`, `PH Mo`, weekday-only
 * rules) makes the spec unknown.
 */
object OpeningHours {
    /** True/false when [spec] can be evaluated for [at]; null when [spec] is missing or not understood. */
    fun isOpen(spec: String?, at: LocalDateTime): Boolean? {
        val schedule = parseSchedule(spec) ?: return null
        val date = at.toLocalDate()
        val minute = at.hour * 60 + at.minute
        if (schedule.spansOn(date).any { minute >= it.start && minute < it.end }) return true
        return schedule.spansOn(date.minusDays(1)).any { minute + MINUTES_PER_DAY < it.end }
    }

    /** True when [spec] is in the supported subset. */
    fun isSupported(spec: String?): Boolean = parseSchedule(spec) != null

    private const val MINUTES_PER_DAY = 24 * 60
    private const val MAX_END_MINUTE = 48 * 60
    private val WEEKDAYS = listOf("mo", "tu", "we", "th", "fr", "sa", "su")

    /** Minutes after midnight of the day it belongs to; [end] (exclusive) may exceed one day. */
    private class Span(val start: Int, val end: Int)

    /** [weekdays]: index 0 = Monday, empty when only `PH` is named. */
    private class Selector(val weekdays: Set<Int>, val publicHolidays: Boolean)

    /** [selector] null = no day selector (every day, holiday or not). [spans] empty = closed. */
    private class Rule(val selector: Selector?, val spans: List<Span>) {
        fun matches(weekday: Int, holiday: Boolean): Boolean =
            selector == null || weekday in selector.weekdays || (holiday && selector.publicHolidays)

        /** False for a rule that applies to public holidays only. */
        val matchesOrdinaryDays: Boolean get() = selector == null || selector.weekdays.isNotEmpty()
    }

    /** The times of each weekday (index 0 = Monday), once for ordinary days and once for public holidays. */
    private class Schedule(private val ordinary: List<List<Span>>, private val holidays: List<List<Span>>) {
        fun spansOn(date: LocalDate): List<Span> {
            val week = if (GermanHolidays.isNationwideHoliday(date)) holidays else ordinary
            return week[date.dayOfWeek.value - 1]
        }
    }

    private class Unsupported : Exception()

    /** Null when [spec] is blank or not understood. */
    private fun parseSchedule(spec: String?): Schedule? {
        if (spec.isNullOrBlank()) return null
        val text = spec.map { if (it in DASHES) '-' else it }.joinToString("")
        return try {
            Parser(text).parse()
        } catch (e: Unsupported) {
            null
        }
    }

    private val DASHES = setOf('‐', '‑', '‒', '–', '—', '−')

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Schedule? {
            val ordinary = List(7) { mutableListOf<Span>() }
            val holidays = List(7) { mutableListOf<Span>() }
            var applied = false
            var additional = false
            while (true) {
                skipWs()
                if (atEnd()) break
                if (peek() == ';') {
                    pos++
                    additional = false
                    continue
                }
                val rule = parseRule()
                if (additional && rule.selector == null) fail()
                for (day in 0..6) {
                    if (rule.matches(day, holiday = false)) apply(rule, additional, ordinary[day])
                    if (rule.matches(day, holiday = true)) apply(rule, additional, holidays[day])
                }
                if (rule.matchesOrdinaryDays) applied = true
                skipWs()
                if (atEnd()) break
                when (peek()) {
                    ';' -> additional = false
                    ',' -> additional = true
                    else -> fail()
                }
                pos++
            }
            return if (applied) Schedule(ordinary, holidays) else null
        }

        /** A `;` rule replaces the day's times, an additional `,` rule adds to them; `off` always closes. */
        private fun apply(rule: Rule, additional: Boolean, day: MutableList<Span>) {
            if (!additional || rule.spans.isEmpty()) day.clear()
            day.addAll(rule.spans)
        }

        private fun parseRule(): Rule {
            skipWs()
            if (text.startsWith("24/7", pos)) {
                pos += 4
                return Rule(null, listOf(Span(0, MINUTES_PER_DAY)))
            }
            var selector: Selector? = null
            if (peekIsLetter() && peekWord().lowercase() !in OFF_WORDS) selector = parseSelector()
            skipWs()
            val spans = when {
                peekIsLetter() -> if (readWord().lowercase() in OFF_WORDS) emptyList() else fail()
                peekIsDigit() -> parseTimes()
                else -> fail()
            }
            return Rule(selector, spans)
        }

        private fun parseSelector(): Selector {
            val days = mutableSetOf<Int>()
            var publicHolidays = false
            while (true) {
                skipWs()
                val word = readWord().lowercase()
                if (word == "ph") {
                    publicHolidays = true
                } else {
                    val first = WEEKDAYS.indexOf(word).takeIf { it >= 0 } ?: fail()
                    skipWs()
                    if (peek() == '-') {
                        pos++
                        skipWs()
                        val last = WEEKDAYS.indexOf(readWord().lowercase()).takeIf { it >= 0 } ?: fail()
                        var day = first
                        while (true) {
                            days += day
                            if (day == last) break
                            day = (day + 1) % 7
                        }
                    } else {
                        days += first
                    }
                }
                skipWs()
                if (peek() == ',' && charAfterWs(pos + 1)?.isLetter() == true) {
                    pos++
                    continue
                }
                break
            }
            skipWs()
            if (peek() == ':') pos++
            return Selector(days, publicHolidays)
        }

        private fun parseTimes(): List<Span> {
            val spans = mutableListOf<Span>()
            while (true) {
                skipWs()
                val start = readTime()
                if (start >= MINUTES_PER_DAY) fail()
                skipWs()
                if (peek() != '-') fail()
                pos++
                skipWs()
                var end = readTime()
                if (end > MAX_END_MINUTE) fail()
                if (end <= start) end += MINUTES_PER_DAY
                spans += Span(start, end)
                skipWs()
                if (peek() == ',' && charAfterWs(pos + 1)?.isDigit() == true) {
                    pos++
                    continue
                }
                return spans
            }
        }

        /** `H:MM` or `HH:MM` as minutes. */
        private fun readTime(): Int {
            val hourStart = pos
            while (pos < text.length && text[pos].isAsciiDigit() && pos - hourStart < 2) pos++
            if (pos == hourStart || peek() != ':') fail()
            val hours = text.substring(hourStart, pos).toInt()
            pos++
            if (pos + 2 > text.length || !text[pos].isAsciiDigit() || !text[pos + 1].isAsciiDigit()) fail()
            val minutes = text.substring(pos, pos + 2).toInt()
            pos += 2
            if (minutes >= 60) fail()
            if (pos < text.length && text[pos].isAsciiDigit()) fail()
            return hours * 60 + minutes
        }

        private fun readWord(): String {
            val start = pos
            while (pos < text.length && text[pos].isLetter()) pos++
            return text.substring(start, pos)
        }

        private fun peekWord(): String {
            var end = pos
            while (end < text.length && text[end].isLetter()) end++
            return text.substring(pos, end)
        }

        private fun charAfterWs(from: Int): Char? {
            var i = from
            while (i < text.length && text[i].isWhitespace()) i++
            return text.getOrNull(i)
        }

        private fun skipWs() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun atEnd() = pos >= text.length
        private fun peek(): Char? = text.getOrNull(pos)
        private fun peekIsLetter() = peek()?.isLetter() == true
        private fun peekIsDigit() = peek()?.isAsciiDigit() == true
        private fun Char.isAsciiDigit() = this in '0'..'9'
        private fun fail(): Nothing = throw Unsupported()
    }

    private val OFF_WORDS = setOf("off", "closed")
}
