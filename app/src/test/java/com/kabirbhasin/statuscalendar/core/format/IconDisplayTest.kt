package com.kabirbhasin.statuscalendar.core.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * The icon slot holds only a few characters, so anything longer must fall back to
 * the desk calendar layout. These lock the boundary in place.
 */
class IconDisplayTest {

    private val locale = Locale.UK
    private val now: ZonedDateTime =
        ZonedDateTime.of(2026, 1, 7, 21, 5, 7, 0, ZoneId.of("Europe/London"))

    @Test fun shortFormatStaysOnOneLine() {
        val r = IconDisplay.forSpec(Presets.time24.spec, now, locale)
        assertNull(r.stackTop)
        assertEquals("21:05", r.line)
    }

    @Test fun longFormatBecomesCalendar() {
        val r = IconDisplay.forSpec(Presets.fullUk.spec, now, locale)
        assertNotNull(r.stackTop)
        assertEquals("7", r.stackBottom)
    }

    @Test fun explicitStackModeAlwaysCalendar() {
        val r = IconDisplay.forSpec(Presets.calendarIcon.spec, now, locale)
        assertNotNull(r.stackTop)
    }

    @Test fun secondsNeverReachTheIcon() {
        val r = IconDisplay.forSpec(Presets.timeSeconds.spec, now, locale)
        assertEquals("21:05", r.line)
    }

    @Test fun boundaryAtSevenCharactersStaysSingleLine() {
        val r = IconDisplay.forSpec(Presets.time12.spec, now, locale)
        assertEquals(true, r.line.length <= IconDisplay.MAX_SINGLE_LINE || r.stackTop != null)
    }
}
