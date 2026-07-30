package com.kabirbhasin.statuscalendar.core.format

/**
 * Raised ordinal endings are real Unicode characters, not styling, so they only
 * appear if the active font actually contains them. Two of the six live in the
 * Phonetic Extensions block, which several manufacturer fonts omit, and one of those
 * is used by every "th" ending. Without a check the user would see empty boxes on
 * most dates.
 *
 * The platform layer measures glyph coverage once and records it here; the format
 * engine stays pure Kotlin and testable.
 */
object SuperscriptSupport {

    /** Every character the raised endings need. */
    val REQUIRED_CHARACTERS: List<String> = listOf(
        "ˢ", // s
        "ᵗ", // t
        "ⁿ", // n
        "ᵈ", // d
        "ʳ", // r
        "ʰ"  // h
    )

    /**
     * Assumed present until the platform reports otherwise, so a unit test or a
     * headless render behaves like a device with a complete font.
     */
    @Volatile
    var available: Boolean = true
        private set

    fun record(isAvailable: Boolean) {
        available = isAvailable
    }
}
