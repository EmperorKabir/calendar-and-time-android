package com.kabirbhasin.statuscalendar.engine.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
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
        // A view already attached means this engine is showing; re-adding would
        // stack a second window that never updates.
        if (view != null || !canDraw()) return
        val textView = TextView(context).apply {
            setTextColor(style.textColor.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
            includeFontPadding = false
            // Status bars vary from white to black between phones and wallpapers, so a
            // contrasting shadow keeps the text readable without knowing the background.
            setShadowLayer(3f, 0f, 1f, 0xC0000000.toInt())
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
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
        current.setShadowLayer(3f, 0f, 1f, 0xC0000000.toInt())
        runCatching { windowManager.updateViewLayout(current, layoutParams()) }
    }

    /**
     * Status bar height taken from live window insets where available, so notches,
     * punch holes and foldable posture changes are respected rather than assumed.
     */
    private fun statusBarHeight(): Int {
        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            view?.rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        if (fromInsets > 0) return fromInsets
        val resourceId = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }

    /**
     * Horizontal span the cutout occupies, so the text is never placed underneath a
     * punch hole or notch. Returns 0 when the display has no cutout.
     */
    private fun cutoutRight(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return 0
        val cutout = view?.rootWindowInsets?.displayCutout ?: return 0
        return cutout.boundingRects.maxOfOrNull { it.right } ?: 0
    }

    /** Re-places the overlay after a fold, unfold or rotation. */
    fun refreshPlacement() {
        val current = view ?: return
        runCatching { windowManager.updateViewLayout(current, layoutParams()) }
    }

    /**
     * Smallest x that clears the OEM clock. The clock sits at the leading edge, so a
     * conservative reserve stops the text being drawn on top of it.
     */
    private fun clockReserve(): Int =
        (context.resources.displayMetrics.widthPixels * 0.22f).toInt()

    /** Start x, kept clear of the clock and any cutout, and inside the screen. */
    private fun startX(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        val minimum = maxOf(clockReserve(), cutoutRight())
        return style.offsetX.coerceIn(minimum, (screen - 120).coerceAtLeast(minimum))
    }

    /** Space left between the text's start edge and the right of the screen. */
    private fun availableWidth(): Int =
        (context.resources.displayMetrics.widthPixels - startX()).coerceAtLeast(1)

    private fun layoutParams() = WindowManager.LayoutParams(
        availableWidth(),
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = startX()
        // Centre the text within the status bar, then apply the user's nudge.
        val barHeight = statusBarHeight()
        val textHeight = (style.textSizeSp * context.resources.displayMetrics.scaledDensity).toInt()
        y = ((barHeight - textHeight) / 2).coerceAtLeast(0) + style.offsetY
    }
}
