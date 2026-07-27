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

    fun iconFor(display: RenderedDisplay): Bitmap {
        val key = if (display.stackTop != null) {
            "s|${display.stackTop}|${display.stackBottom}"
        } else {
            "l|${display.line}"
        }
        lastBitmap?.let { if (key == lastKey) return it }

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (display.stackTop != null && display.stackBottom != null) {
            drawStack(canvas, display.stackTop, display.stackBottom)
        } else {
            drawLine(canvas, display.line)
        }

        lastKey = key
        lastBitmap = bitmap
        return bitmap
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
