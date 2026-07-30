package com.kabirbhasin.statuscalendar.engine.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.content.Intent
import androidx.core.app.NotificationCompat
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

        /** The chip is a small surface; longer strings are dropped by the system. */
        const val SHORT_TEXT_LIMIT = 24
    }

    private val iconFactory = IconFactory()

    /**
     * True when the platform renders promoted ongoing notifications as a status bar
     * chip. The chip carries text, unlike an icon, so long formats stay readable.
     */
    fun chipSupported(): Boolean = Build.VERSION.SDK_INT >= 36
    private var lastRendered: RenderedDisplay? = null
    private var active = false
    private var iconVisible = true

    /** When false the notification persists (the service needs it) with no visible glyph. */
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
                if (chipSupported() && iconVisible) {
                    setRequestPromotedOngoing(true)
                    setShortCriticalText(contentText.take(SHORT_TEXT_LIMIT))
                }
            }
            .build()
    }

    private fun post(display: RenderedDisplay) {
        val manager = NotificationManagerCompat.from(context)
        if (!canPost() || !manager.areNotificationsEnabled()) return
        runCatching { manager.notify(NOTIFICATION_ID, build(display)) }
    }

    /** Android 13+ gates posting behind a runtime permission. */
    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

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
