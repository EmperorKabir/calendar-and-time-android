package com.kabirbhasin.statuscalendar.core.format

import android.graphics.Paint
import android.graphics.Typeface

/**
 * Measures whether the fonts this app draws with can render the raised ordinal
 * endings. Checked against every typeface actually used: the default face for the
 * interface, and the bold face used by the status bar icon and the overlay.
 */
object GlyphProbe {

    fun probe() {
        val faces = listOf(
            Typeface.DEFAULT,
            Typeface.DEFAULT_BOLD,
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        )
        val paint = Paint()
        val complete = faces.all { face ->
            paint.typeface = face
            SuperscriptSupport.REQUIRED_CHARACTERS.all { runCatching { paint.hasGlyph(it) }
                .getOrDefault(false) }
        }
        SuperscriptSupport.record(complete)
    }
}
