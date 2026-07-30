package com.kabirbhasin.statuscalendar.core.format

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Raised endings must degrade to plain letters when the font cannot draw them,
 * because an unsupported glyph renders as an empty box on the user's status bar.
 */
class SuperscriptFallbackTest {

    @After fun restore() = SuperscriptSupport.record(true)

    @Test fun raisedEndingsAreUsedWhenTheFontHasThem() {
        SuperscriptSupport.record(true)
        assertEquals("1ˢᵗ", FormatEngine.withOrdinal(1, superscript = true))
        assertEquals("2ⁿᵈ", FormatEngine.withOrdinal(2, superscript = true))
        assertEquals("3ʳᵈ", FormatEngine.withOrdinal(3, superscript = true))
        assertEquals("4ᵗʰ", FormatEngine.withOrdinal(4, superscript = true))
    }

    @Test fun plainEndingsAreUsedWhenTheFontLacksThem() {
        SuperscriptSupport.record(false)
        assertEquals("1st", FormatEngine.withOrdinal(1, superscript = true))
        assertEquals("2nd", FormatEngine.withOrdinal(2, superscript = true))
        assertEquals("3rd", FormatEngine.withOrdinal(3, superscript = true))
        assertEquals("4th", FormatEngine.withOrdinal(4, superscript = true))
        assertEquals("11th", FormatEngine.withOrdinal(11, superscript = true))
    }

    @Test fun theWholeDateFallsBackNotJustTheSuffix() {
        SuperscriptSupport.record(false)
        val spec = Presets.fullUk.spec
        val rendered = FormatEngine.render(
            spec,
            java.time.ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, java.time.ZoneId.of("UTC")),
            java.util.Locale.UK
        )
        assertEquals(true, rendered.line.contains("1st"))
        assertEquals(false, rendered.line.contains("ˢᵗ"))
    }

    @Test fun askingForPlainNeverProducesRaised() {
        SuperscriptSupport.record(true)
        assertEquals("1st", FormatEngine.withOrdinal(1, superscript = false))
    }

    @Test fun everyRequiredCharacterIsListedForProbing() {
        // The probe must check all six, or a missing one slips through.
        assertEquals(6, SuperscriptSupport.REQUIRED_CHARACTERS.size)
        assertEquals(6, SuperscriptSupport.REQUIRED_CHARACTERS.distinct().size)
    }
}
