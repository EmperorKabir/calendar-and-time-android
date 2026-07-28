package com.kabirbhasin.statuscalendar.engine.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.core.prefs.OverlayStyle
import com.kabirbhasin.statuscalendar.engine.DisplayEngine

/**
 * Draws the display as text in the status bar's empty region via an
 * application overlay window. Unlike the notification engine this shows real
 * text on every OEM and can tick seconds; it is not visible on the lockscreen.
 */
class OverlayEngine(private val context: Context) : DisplayEngine {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var style = OverlayStyle(0, 0, 13f, 0xFFFFFFFF, true)

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    override fun start() {
        if (view != null || !canDraw()) return
        val textView = TextView(context).apply {
            setTextColor(style.textColor.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            includeFontPadding = false
        }
        runCatching {
            windowManager.addView(textView, layoutParams())
            view = textView
        }
    }

    override fun stop() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    override fun render(display: RenderedDisplay) {
        val current = view ?: return
        val text = if (display.stackTop != null) {
            listOf(display.stackTop, display.stackBottom, display.line.ifEmpty { null })
                .filterNotNull().joinToString(" ")
        } else display.line
        if (current.text.toString() != text) current.text = text
    }

    fun applyStyle(newStyle: OverlayStyle) {
        style = newStyle
        val current = view ?: return
        current.setTextColor(newStyle.textColor.toInt())
        current.setTextSize(TypedValue.COMPLEX_UNIT_SP, newStyle.textSizeSp)
        runCatching { windowManager.updateViewLayout(current, layoutParams()) }
    }

    /** Height of the status bar, so text sits inside it rather than above it. */
    private fun statusBarHeight(): Int {
        val resourceId = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = style.offsetX
        // Centre the text within the status bar, then apply the user's nudge.
        val barHeight = statusBarHeight()
        val textHeight = (style.textSizeSp * context.resources.displayMetrics.scaledDensity).toInt()
        y = ((barHeight - textHeight) / 2).coerceAtLeast(0) + style.offsetY
    }
}
