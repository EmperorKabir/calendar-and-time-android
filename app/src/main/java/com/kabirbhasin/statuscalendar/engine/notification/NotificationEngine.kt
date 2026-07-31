package com.kabirbhasin.statuscalendar.engine.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.engine.DisplayEngine
import com.kabirbhasin.statuscalendar.ui.MainActivity

class NotificationEngine(private val context: Context) : DisplayEngine {

    companion object {
        const val CHANNEL_ID = "status_display"
        const val SILENT_CHANNEL_ID = "status_service"
        const val NOTIFICATION_ID = 1001

        /**
         * The platform documents a maximum of 7 characters for the chip's short text,
         * and drops the text entirely when less than half of it fits. Anything longer
         * than this produces an icon-only chip, which is worse than not promoting.
         */
        const val SHORT_TEXT_LIMIT = 7

        /**
         * A colorized background has to carry auto-contrasted text, so a very light
         * user colour would produce a glaring card in the shade. Colours above this
         * relative luminance are darkened to sit at it instead.
         */
        private const val MAX_CARD_LUMINANCE = 0.34
    }

    private val iconFactory = IconFactory()

    /**
     * True when the platform renders promoted ongoing notifications as a status bar
     * chip. The chip carries text, unlike an icon, so long formats stay readable.
     */
    fun chipSupported(): Boolean =
        Build.VERSION.SDK_INT >= 36 &&
            runCatching {
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
            }.getOrDefault(false)
    private var lastRendered: RenderedDisplay? = null
    private var active = false
    private var iconVisible = true

    /** When false the notification persists (the service needs it) with no visible glyph. */
    fun setIconColour(colour: Int) {
        if (iconFactory.colour == colour) return
        iconFactory.colour = colour
        lastRendered?.let { post(it) }
    }

    fun setIconVisible(visible: Boolean) {
        if (iconVisible == visible) return
        iconVisible = visible
        lastRendered?.let { post(it) }
    }

    override fun start() {
        ensureChannel()
        active = true
    }

    override fun stop() {
        active = false
        lastRendered = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    override fun render(display: RenderedDisplay) {
        // The timestamp is fixed, so there is nothing to refresh: post only on change.
        if (display == lastRendered) return
        lastRendered = display
        post(display)
    }

    /** Builds the current notification without posting; used as the FGS notification. */
    fun build(display: RenderedDisplay): Notification {
        ensureChannel()
        lastRendered = display
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentText = if (display.stackTop != null) {
            "${display.stackTop} ${display.stackBottom}"
        } else display.line
        return NotificationCompat.Builder(
            context,
            if (iconVisible) CHANNEL_ID else SILENT_CHANNEL_ID
        )
            .setSmallIcon(
                IconCompat.createWithBitmap(
                    if (iconVisible) iconFactory.iconFor(display) else iconFactory.blank()
                )
            )
            .setContentTitle(contentText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            // Left at the post time. Skins exist that print the timestamp regardless of
            // setShowWhen, and a doctored one showed up in the shade as "in 73 y".
            .setWhen(System.currentTimeMillis())
            // Status bar icons are ordered by the platform's notification comparator,
            // and a colorized foreground-service notification sits in its highest
            // non-system tier — above conversations, which otherwise take the lead
            // position. Measured on this device: without it the icon sat behind a
            // messaging app, with it the icon leads. Colorizing only counts while the
            // icon is actually shown; the blank service notification stays plain.
            .setColorized(iconVisible)
            .setColor(if (iconVisible) cardColour(iconFactory.colour) else Color.TRANSPARENT)
            .setSortKey("0")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(
                if (iconVisible) NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                else NotificationCompat.FOREGROUND_SERVICE_DEFERRED
            )
            .apply {
                // Android 16 promotes an ongoing notification to a status bar chip and
                // renders the short critical text inside it. That is the platform's own
                // way of putting our text in the bar, so it needs no overlay at all.
                // Promoting with text that will not fit yields an icon-only chip, so
                // promotion is requested only when the format is genuinely short.
                // The stacked pair reads as a date ("THU 30"); stackBottom alone is a
                // bare number and told the user nothing.
                val chipText = when {
                    display.stackTop != null ->
                        "${display.stackTop} ${display.stackBottom}"
                    else -> contentText
                }
                // Requested without consulting canPostPromotedNotifications first. That
                // call returned false on a device whose own flags had the feature on and
                // which recorded this app as promotable, so it is not a reliable gate.
                // The platform simply ignores the request where promotion is not allowed.
                if (iconVisible && chipText.length <= SHORT_TEXT_LIMIT) {
                    setRequestPromotedOngoing(true)
                    setShortCriticalText(chipText)
                }
            }
            .build()
    }

    /**
     * The shade card keeps the colour the user picked for the icon, so the two read as
     * one setting, but is darkened when that colour is too light to sit behind text.
     */
    private fun cardColour(colour: Int): Int {
        val luminance = androidx.core.graphics.ColorUtils.calculateLuminance(colour)
        if (luminance <= MAX_CARD_LUMINANCE) return colour or Color.BLACK
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(colour, hsl)
        // Pure white and other unsaturated picks have no hue worth keeping, so they
        // land on a neutral dark grey rather than an arbitrary tint.
        hsl[2] = 0.30f
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    private fun post(display: RenderedDisplay) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        // Checked inline rather than through a helper so the permission guard is
        // statically verifiable at the call site.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        runCatching { manager.notify(NOTIFICATION_ID, build(display)) }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        // Minimum importance keeps the service alive without claiming an icon slot.
        manager.createNotificationChannel(
            NotificationChannel(
                SILENT_CHANNEL_ID,
                "Background service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                description = "Keeps the display running with no status bar icon"
            }
        )
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Status bar display",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            description = "Hosts the calendar/clock icon shown in the status bar"
        }
        manager.createNotificationChannel(channel)
    }
}
