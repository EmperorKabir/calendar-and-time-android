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

    private val date = DateConfig(
        showDay = true, dayPadded = false, dayOrdinal = false, ordinalSuperscript = false,
        monthStyle = MonthStyle.NONE, yearStyle = YearStyle.NONE,
        order = DateOrder.DMY, separator = "/"
    )
    private val time = TimeConfig(HourStyle.NONE, showSeconds = false, amPm = AmPmStyle.NONE)

    private fun spec(
        order: List<DisplayElement> = listOf(DisplayElement.DOW, DisplayElement.DATE),
        dow: DowStyle = DowStyle.NONE,
        dateConfig: DateConfig = date,
        timeConfig: TimeConfig = time,
        separator: String = ", ",
        stack: Boolean = false
    ) = FormatSpec(order, dow, dateConfig, timeConfig, separator, stack)

    private fun render(s: FormatSpec, at: ZonedDateTime = newYear) =
        FormatEngine.render(s, at, locale).line

    // Day-of-week styles
    @Test fun dowFull() =
        assertEquals("Thursday", render(spec(order = listOf(DisplayElement.DOW), dow = DowStyle.FULL)))

    @Test fun dowShort() =
        assertEquals("Thu", render(spec(order = listOf(DisplayElement.DOW), dow = DowStyle.SHORT)))

    @Test fun dowNarrow() =
        assertEquals("T", render(spec(order = listOf(DisplayElement.DOW), dow = DowStyle.NARROW)))

    // Month styles
    @Test fun monthFullName() =
        assertEquals("1 January 2026", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(monthStyle = MonthStyle.FULL, yearStyle = YearStyle.FULL))))

    @Test fun monthShortName() =
        assertEquals("1 Jan", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(monthStyle = MonthStyle.SHORT))))

    @Test fun monthNumberPadded() =
        assertEquals("01/01/2026", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(dayPadded = true, monthStyle = MonthStyle.NUMBER_PADDED,
                yearStyle = YearStyle.FULL))))

    @Test fun monthNumberBare() =
        assertEquals("1/1/26", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(monthStyle = MonthStyle.NUMBER, yearStyle = YearStyle.TWO_DIGIT))))

    // Date component order + separators
    @Test fun isoStyle() =
        assertEquals("2026-01-01", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(dayPadded = true, monthStyle = MonthStyle.NUMBER_PADDED,
                yearStyle = YearStyle.FULL, order = DateOrder.YMD, separator = "-"))))

    @Test fun mdyDotted() =
        assertEquals("01.07.2026", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(dayPadded = true, monthStyle = MonthStyle.NUMBER_PADDED,
                yearStyle = YearStyle.FULL, order = DateOrder.MDY, separator = ".")), midweek))

    @Test fun ordinalBeatsPadding() =
        assertEquals("1st January 2026", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(dayPadded = true, dayOrdinal = true,
                monthStyle = MonthStyle.FULL, yearStyle = YearStyle.FULL))))

    @Test fun dayOnly() =
        assertEquals("7", render(spec(order = listOf(DisplayElement.DATE)), midweek))

    // Hour styles
    @Test fun hour24Padded() =
        assertEquals("09:05", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = time.copy(hourStyle = HourStyle.H24_PADDED))))

    @Test fun hour24Bare() =
        assertEquals("9:05", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = time.copy(hourStyle = HourStyle.H24))))

    @Test fun hour12PaddedUppercaseAm() =
        assertEquals("09:05 AM", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = TimeConfig(HourStyle.H12_PADDED, false, AmPmStyle.UPPERCASE))))

    @Test fun hour12BareLowercasePm() =
        assertEquals("9:05 pm", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = TimeConfig(HourStyle.H12, false, AmPmStyle.LOWERCASE)), midweek))

    @Test fun hour12NoMarker() =
        assertEquals("9:05", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = TimeConfig(HourStyle.H12, false, AmPmStyle.NONE)), midweek))

    @Test fun secondsShown() =
        assertEquals("21:05:07", render(spec(order = listOf(DisplayElement.TIME),
            timeConfig = TimeConfig(HourStyle.H24_PADDED, true, AmPmStyle.NONE)), midweek))

    // Element ordering + joiner
    @Test fun fullCombination() =
        assertEquals("Thursday, 1st January 2026", render(spec(
            dow = DowStyle.FULL,
            dateConfig = date.copy(dayOrdinal = true, monthStyle = MonthStyle.FULL,
                yearStyle = YearStyle.FULL))))

    @Test fun timeFirstCustomJoiner() =
        assertEquals("09:05 · Thu", render(spec(
            order = listOf(DisplayElement.TIME, DisplayElement.DOW),
            dow = DowStyle.SHORT,
            timeConfig = time.copy(hourStyle = HourStyle.H24_PADDED),
            separator = " · ")))

    @Test fun emptyElementsSkipped() =
        assertEquals("7", render(spec(
            order = listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME)), midweek))

    // Stack
    @Test fun calendarStack() {
        val r = FormatEngine.render(spec(stack = true), newYear, locale)
        assertEquals("THU", r.stackTop)
        assertEquals("1", r.stackBottom)
    }

    @Test fun stackOffYieldsNulls() {
        val r = FormatEngine.render(spec(), newYear, locale)
        assertEquals(null, r.stackTop)
        assertEquals(null, r.stackBottom)
    }

    @Test fun superscriptOrdinal() =
        assertEquals("1ˢᵗ January 2026", render(spec(
            order = listOf(DisplayElement.DATE),
            dateConfig = date.copy(dayOrdinal = true, ordinalSuperscript = true,
                monthStyle = MonthStyle.FULL, yearStyle = YearStyle.FULL))))

    @Test fun superscriptSuffixVariants() {
        assertEquals("2ⁿᵈ", FormatEngine.withOrdinal(2, superscript = true))
        assertEquals("3ʳᵈ", FormatEngine.withOrdinal(3, superscript = true))
        assertEquals("4ᵗʰ", FormatEngine.withOrdinal(4, superscript = true))
        assertEquals("11ᵗʰ", FormatEngine.withOrdinal(11, superscript = true))
        assertEquals("21ˢᵗ", FormatEngine.withOrdinal(21, superscript = true))
    }

    // Ordinals
    @Test fun ordinalSuffixes() {
        val expected = mapOf(
            1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th",
            11 to "11th", 12 to "12th", 13 to "13th",
            21 to "21st", 22 to "22nd", 23 to "23rd", 31 to "31st"
        )
        expected.forEach { (day, want) -> assertEquals(want, FormatEngine.withOrdinal(day)) }
    }
}
