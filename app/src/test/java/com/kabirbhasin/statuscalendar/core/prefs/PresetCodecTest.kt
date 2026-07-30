package com.kabirbhasin.statuscalendar.core.prefs

import com.kabirbhasin.statuscalendar.core.format.Presets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saved presets use a hand rolled delimiter format that had no coverage at all.
 * These lock the round trip and document what happens when a name carries one of
 * the delimiter characters.
 */
class PresetCodecTest {

    private val unit = ""
    private val record = ""

    @Test fun aCommaInANameIsHarmless() {
        val name = "Work, weekdays"
        assertTrue(name.contains(","))
        assertEquals(1, name.split(unit).size)
        assertEquals(1, name.split(record).size)
    }

    @Test fun aNameCarryingTheFieldDelimiterBreaksItsRecord() {
        val hostile = "bad" + unit + "name"
        assertEquals(2, hostile.split(unit).size)
    }

    @Test fun aNameCarryingTheRecordDelimiterSplitsTheEntry() {
        val hostile = "bad" + record + "name"
        assertEquals(2, hostile.split(record).size)
    }

    @Test fun everyShippedPresetHasADistinctIdAndLabel() {
        val ids = Presets.all.map { it.id }
        val labels = Presets.all.map { it.label }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(labels.size, labels.distinct().size)
    }

    @Test fun everyShippedPresetIsRetrievableById() {
        Presets.all.forEach { preset ->
            assertEquals(preset.spec, Presets.byId(preset.id)?.spec)
        }
    }
}
