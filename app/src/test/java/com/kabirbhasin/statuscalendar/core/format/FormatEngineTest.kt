package com.kabirbhasin.statuscalendar.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class FormatEngineTest {

    private val locale = Locale.UK
    private val zone = ZoneId.of("Europe/London")

    // 2026-01-01 is a Thursday; 2026-01-07 is a Wednesday.
    private val newYear: ZonedDateTime = ZonedDateTime.of(2026, 1, 1, 9, 5, 7, 0, zone)
    private val midweek: ZonedDateTime = ZonedDateTime.of(2026, 1, 7, 21, 5, 7, 0, zone)

    private fun spec(
        order: List<DisplayElement> = listOf(DisplayElement.DOW, DisplayElement.DATE),
        dow: DowStyle = DowStyle.NONE,
        date: DateStyle = DateStyle.NONE,
        time: TimeStyle = TimeStyle.NONE,
        seconds: Boolean = false,
        amPm: Boolean = false,
        leadingZero: Boolean = true,
        separator: String = ", ",
        stack: Boolean = false
    ) = FormatSpec(order, dow, date, time, seconds, amPm, leadingZero, separator, stack)

    @Test
    fun fullDateWithOrdinalAndFullDow() {
        val s = spec(dow = DowStyle.FULL, date = DateStyle.FULL_ORDINAL)
        assertEquals("Thursday, 1st January 2026", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun shortDowShortDate() {
        val s = spec(dow = DowStyle.SHORT, date = DateStyle.SHORT, separator = " ")
        assertEquals("Wed 7 Jan", FormatEngine.render(s, midweek, locale).line)
    }

    @Test
    fun isoDate() {
        val s = spec(order = listOf(DisplayElement.DATE), date = DateStyle.ISO)
        assertEquals("2026-01-01", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun numericSlashDate() {
        val s = spec(order = listOf(DisplayElement.DATE), date = DateStyle.NUMERIC_SLASH)
        assertEquals("01/01/2026", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun time24WithLeadingZero() {
        val s = spec(order = listOf(DisplayElement.TIME), time = TimeStyle.H24)
        assertEquals("09:05", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun time12NoLeadingZeroLowercaseAm() {
        val s = spec(
            order = listOf(DisplayElement.TIME),
            time = TimeStyle.H12, amPm = true, leadingZero = false
        )
        assertEquals("9:05 am", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun time12PmWithoutAmPmMarker() {
        val s = spec(
            order = listOf(DisplayElement.TIME),
            time = TimeStyle.H12, amPm = false, leadingZero = false
        )
        assertEquals("9:05", FormatEngine.render(s, midweek, locale).line)
    }

    @Test
    fun time24WithSeconds() {
        val s = spec(order = listOf(DisplayElement.TIME), time = TimeStyle.H24, seconds = true)
        assertEquals("21:05:07", FormatEngine.render(s, midweek, locale).line)
    }

    @Test
    fun timeBeforeDateOrdering() {
        val s = spec(
            order = listOf(DisplayElement.TIME, DisplayElement.DATE),
            date = DateStyle.FULL_ORDINAL, time = TimeStyle.H24
        )
        assertEquals("09:05, 1st January 2026", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun customSeparator() {
        val s = spec(dow = DowStyle.SHORT, date = DateStyle.ISO, separator = " · ")
        assertEquals("Thu · 2026-01-01", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun emptyElementsAreSkipped() {
        val s = spec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME),
            dow = DowStyle.NONE, date = DateStyle.DAY_ONLY, time = TimeStyle.NONE
        )
        assertEquals("1", FormatEngine.render(s, newYear, locale).line)
    }

    @Test
    fun calendarStack() {
        val s = spec(stack = true)
        val r = FormatEngine.render(s, newYear, locale)
        assertEquals("THU", r.stackTop)
        assertEquals("1", r.stackBottom)
    }

    @Test
    fun stackDisabledYieldsNulls() {
        val s = spec(date = DateStyle.ISO)
        val r = FormatEngine.render(s, newYear, locale)
        assertEquals(null, r.stackTop)
        assertEquals(null, r.stackBottom)
    }

    @Test
    fun ordinalSuffixes() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th",
            11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st"
        )
        expected.forEach { (day, want) ->
            assertEquals(want, FormatEngine.withOrdinal(day))
        }
    }
}
