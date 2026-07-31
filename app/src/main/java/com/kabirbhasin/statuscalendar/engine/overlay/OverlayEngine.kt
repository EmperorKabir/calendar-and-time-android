package com.kabirbhasin.statuscalendar.engine.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
    private var placedFromInsets = false
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
        // When a video or game goes fullscreen the status bar is hidden; the overlay
        // must follow it rather than floating over the content.
        textView.setOnApplyWindowInsetsListener { v, insets ->
            // Insets arrive after attachment, so the first placement cannot know the
            // cutout. Re-place exactly once: calling updateViewLayout from inside this
            // callback on every pass is a self sustaining layout loop.
            if (!placedFromInsets) {
                placedFromInsets = true
                v.post { refreshPlacement() }
            }
            if (style.hideInFullscreen) {
                val barVisible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.isVisible(WindowInsets.Type.statusBars())
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop > 0
                }
                v.visibility = if (barVisible) View.VISIBLE else View.GONE
            } else {
                // Without this the view stays GONE forever if the setting is turned
                // off while it happens to be hidden.
                v.visibility = View.VISIBLE
            }
            insets
        }
        runCatching {
            windowManager.addView(textView, layoutParams())
            view = textView
        }
    }

    override fun stop() {
        placedFromInsets = false
        val current = view ?: return
        // Only forget the view if removal succeeded, otherwise the window is
        // orphaned on screen with no reference left to remove it later.
        runCatching { windowManager.removeView(current) }
            .onSuccess { view = null }
            .onFailure { runCatching { windowManager.removeViewImmediate(current) }
                .onSuccess { view = null } }
    }

    override fun render(display: RenderedDisplay) {
        val current = view ?: return
        val text = if (display.stackTop != null) {
            listOf(display.stackTop, display.stackBottom, display.line.ifEmpty { null })
                .filterNotNull().joinToString(" ")
        } else display.line
        if (current.text.toString() == text) return
        current.text = text
        // Automatic placement is measured from the text, so it has to be redone
        // whenever the text changes width, or the right edge drifts as the time ticks.
        if (style.offsetX == OverlayStyle.AUTO_X) {
            val placed = lastAutoWidth
            val width = textWidth()
            if (placed != width) {
                lastAutoWidth = width
                refreshPlacement()
            }
        }
    }

    /** Width the automatic placement was last calculated for. */
    private var lastAutoWidth = -1

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
        // The framework resource is the authority for the bar's own height. Insets on
        // a LAYOUT_NO_LIMITS overlay can report a larger region than the bar itself,
        // which pushed the text below the bar and out of sight, so insets are only
        // consulted when the resource is unavailable and are clamped to a sane bound.
        val resourceId = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        val fromResource =
            if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
        if (fromResource > 0) return fromResource

        val fromInsets = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view?.rootWindowInsets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
        } else {
            @Suppress("DEPRECATION")
            view?.rootWindowInsets?.systemWindowInsetTop ?: 0
        }
        val ceiling = (context.resources.displayMetrics.density * 48).toInt()
        return fromInsets.coerceAtMost(ceiling)
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

    /**
     * Re-places the overlay. Deliberately does NOT clear the insets latch: clearing it
     * here made this method and the insets callback trigger each other endlessly.
     */
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

    /**
     * Room the system keeps at the trailing edge for signal, wifi and battery. The
     * text ends before it rather than running underneath.
     */
    private fun systemIconReserve(): Int =
        (context.resources.displayMetrics.widthPixels * 0.30f).toInt()

    /**
     * Width the text actually needs, measured from the live view so the placement
     * follows the chosen format and text size instead of guessing at them.
     */
    private fun textWidth(): Int {
        val current = view ?: return 0
        // measure() on the not yet laid out view over-reported by a couple of hundred
        // pixels, which dragged the text back over the icons. The paint measures the
        // glyphs actually being drawn.
        val glyphs = current.paint.measureText(current.text, 0, current.text.length)
        return glyphs.toInt() + current.paddingLeft + current.paddingRight
    }

    /**
     * Start x, kept clear of the clock and any cutout, and inside the screen.
     *
     * Left to itself the text is pushed against the trailing edge, in the gap between
     * the notification icons and the system ones. Starting it just after the clock
     * put it straight through the notification icons, which cannot be hidden: skins
     * that draw the app's own icon there ignore a blank one, and the foreground
     * service has to keep its notification. Nothing can report how wide that run of
     * icons is, so the text is placed where they are not.
     */
    private fun startX(): Int {
        val screen = context.resources.displayMetrics.widthPixels
        val minimum = maxOf(clockReserve(), cutoutRight())
        val ceiling = (screen - 120).coerceAtLeast(minimum)
        if (style.offsetX == OverlayStyle.AUTO_X) {
            val trailing = screen - systemIconReserve() - textWidth()
            return trailing.coerceIn(minimum, ceiling)
        }
        return style.offsetX.coerceIn(minimum, ceiling)
    }

    /** Space left between the text's start edge and the right of the screen. */
    private fun availableWidth(): Int =
        (context.resources.displayMetrics.widthPixels - startX()).coerceAtLeast(1)

    /**
     * Permits one further insets driven placement. Called on a real posture change,
     * where the cutout and bar height genuinely differ from the last measurement.
     */
    fun allowReplacement() {
        placedFromInsets = false
    }

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
        // Never place below the bar: the text must stay inside it whatever the nudge.
        val centred = ((barHeight - textHeight) / 2).coerceAtLeast(0)
        y = (centred + style.offsetY).coerceIn(0, (barHeight - textHeight / 2).coerceAtLeast(0))
    }
}
