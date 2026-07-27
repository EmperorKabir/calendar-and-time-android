package com.kabirbhasin.statuscalendar.engine.notification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay

/**
 * Renders display text into a square monochrome bitmap for the notification
 * small-icon slot. The status bar treats the bitmap as an alpha mask, so all
 * glyphs are drawn in opaque white on a transparent background.
 */
class IconFactory {

    private val size = 192
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val bounds = Rect()

    private var lastKey: String? = null
    private var lastBitmap: Bitmap? = null

    /** Fully transparent icon: the service keeps its notification, but no glyph shows. */
    fun blank(): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

    fun iconFor(display: RenderedDisplay): Bitmap {
        val key = if (display.stackTop != null) {
            "s|${display.stackTop}|${display.stackBottom}"
        } else {
            "l|${display.line}"
        }
        lastBitmap?.let { if (key == lastKey) return it }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val stackTop = display.stackTop
        val stackBottom = display.stackBottom
        when {
            stackTop != null && stackBottom != null -> drawStack(canvas, stackTop, stackBottom)
            // A single line that would render below the legibility floor is split
            // across two rows rather than shrunk into an unreadable smear.
            tooSmallForOneLine(display.line) -> splitInTwo(display.line)
                .let { drawStack(canvas, it.first, it.second) }
            else -> drawLine(canvas, display.line)
        }

        lastKey = key
        lastBitmap = bitmap
        return bitmap
    }

    /** Below this the glyphs stop being readable in a 24dp slot. */
    private val minLegibleTextSize = size * 0.34f

    private fun tooSmallForOneLine(text: String): Boolean {
        if (text.isEmpty()) return false
        fitText(text, maxWidth = size * 0.96f, maxHeight = size * 0.62f)
        return paint.textSize < minLegibleTextSize
    }

    /** Splits on the middle space so both rows stay whole words where possible. */
    private fun splitInTwo(text: String): Pair<String, String> {
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.size < 2) {
            val half = (text.length + 1) / 2
            return text.take(half) to text.drop(half)
        }
        val mid = (words.size + 1) / 2
        return words.take(mid).joinToString(" ") to words.drop(mid).joinToString(" ")
    }

    private fun drawLine(canvas: Canvas, text: String) {
        if (text.isEmpty()) return
        fitText(text, maxWidth = size * 0.96f, maxHeight = size * 0.62f)
        canvas.drawText(text, size / 2f, baselineFor(size / 2f), paint)
    }

    private fun drawStack(canvas: Canvas, top: String, bottom: String) {
        // Day-of-week band across the top, day number filling the rest.
        fitText(top, maxWidth = size * 0.94f, maxHeight = size * 0.30f)
        canvas.drawText(top, size / 2f, baselineFor(size * 0.19f), paint)

        fitText(bottom, maxWidth = size * 0.94f, maxHeight = size * 0.58f)
        canvas.drawText(bottom, size / 2f, baselineFor(size * 0.66f), paint)
    }

    /** Sizes [paint] so [text] fits the given box. */
    private fun fitText(text: String, maxWidth: Float, maxHeight: Float) {
        paint.textSize = 100f
        paint.getTextBounds(text, 0, text.length, bounds)
        val scale = minOf(
            maxWidth / bounds.width().coerceAtLeast(1),
            maxHeight / bounds.height().coerceAtLeast(1)
        )
        paint.textSize = 100f * scale
    }

    /** Baseline y so the current text is vertically centred on [centerY]. */
    private fun baselineFor(centerY: Float): Float =
        centerY - (paint.descent() + paint.ascent()) / 2f
}
