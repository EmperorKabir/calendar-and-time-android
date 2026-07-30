package com.kabirbhasin.statuscalendar.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hex field accepts typing, so it sees half finished and malformed input on every
 * keystroke. It must never throw and never return a wrong colour.
 */
class HexColourTest {

    @Test fun sixDigitsGainFullOpacity() {
        assertEquals(0xFFFFFFFF, parseHexColour("#FFFFFF"))
        assertEquals(0xFF000000, parseHexColour("#000000"))
        assertEquals(0xFF4FC3F7, parseHexColour("#4FC3F7"))
    }

    @Test fun theHashIsOptional() {
        assertEquals(0xFFFFC107, parseHexColour("FFC107"))
    }

    @Test fun eightDigitsKeepTheirAlpha() {
        assertEquals(0x80FF0000, parseHexColour("#80FF0000"))
    }

    @Test fun threeDigitsExpand() {
        assertEquals(0xFFFFFFFF, parseHexColour("#FFF"))
        assertEquals(0xFF112233, parseHexColour("#123"))
    }

    @Test fun surroundingSpaceIsIgnored() {
        assertEquals(0xFF000000, parseHexColour("  #000000  "))
    }

    @Test fun lowercaseIsAccepted() {
        assertEquals(0xFF4FC3F7, parseHexColour("#4fc3f7"))
    }

    @Test fun partialTypingIsRejectedRatherThanGuessed() {
        assertNull(parseHexColour("#"))
        assertNull(parseHexColour("#F"))
        assertNull(parseHexColour("#FF"))
        assertNull(parseHexColour("#FFFF"))
        assertNull(parseHexColour("#FFFFF"))
    }

    @Test fun rubbishIsRejected() {
        assertNull(parseHexColour("hello!"))
        assertNull(parseHexColour("#GGGGGG"))
        assertNull(parseHexColour(""))
        assertNull(parseHexColour("#FFFFFFFFF"))
    }
}
