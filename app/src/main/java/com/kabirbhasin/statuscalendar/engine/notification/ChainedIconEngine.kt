package com.kabirbhasin.statuscalendar.engine.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.engine.DisplayEngine

/**
 * Experimental: renders longer text genuinely inside the system status bar by
 * splitting it across several notification icons, ordered left-to-right with
 * sort keys. Each chunk is a real bar element — the cost is one shade entry per
 * chunk, and OEM icon caps can truncate the tail.
 */
class ChainedIconEngine(private val context: Context) : DisplayEngine {

    companion object {
        const val CHANNEL_ID = "status_display_chain"
        private const val BASE_ID = 2000
        private const val MAX_CHUNKS = 6
        private const val CHARS_PER_CHUNK = 3
    }

    private val iconFactory = IconFactory()
    private var active = false
    private var lastChunks: List<String> = emptyList()

    override fun start() {
        ensureChannel()
        active = true
    }

    override fun stop() {
        active = false
        val manager = NotificationManagerCompat.from(context)
        // Clear the whole reserved range: chunks posted before a process restart
        // are not in lastChunks and would otherwise linger in the shade.
        (0 until MAX_CHUNKS).forEach { manager.cancel(BASE_ID + it) }
        lastChunks = emptyList()
    }

    override fun render(display: RenderedDisplay) {
        if (!active) return
        val text = if (display.stackTop != null) {
            listOf(display.stackTop, display.stackBottom).joinToString(" ")
        } else display.line
        val chunks = chunk(text)
        if (chunks == lastChunks) return

        val manager = NotificationManagerCompat.from(context)
        if (!canPost() || !manager.areNotificationsEnabled()) return

        // Drop chunks no longer needed when the text shortens.
        if (lastChunks.size > chunks.size) {
            (chunks.size until lastChunks.size).forEach { manager.cancel(BASE_ID + it) }
        }
        chunks.forEachIndexed { index, chunkText ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(
                    IconCompat.createWithBitmap(
                        iconFactory.iconFor(RenderedDisplay(chunkText, null, null))
                    )
                )
                .setContentTitle(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setShowWhen(false)
                // Sort keys keep the chunks in reading order in the bar.
                .setSortKey("chain_%02d".format(index))
                .setGroup("status_chain")
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            runCatching { manager.notify(BASE_ID + index, notification) }
        }
        lastChunks = chunks
    }

    private fun chunk(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        return text.chunked(CHARS_PER_CHUNK).take(MAX_CHUNKS)
    }

    /** Android 13+ gates posting behind a runtime permission. */
    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Status bar text chain (experimental)",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                description = "Splits longer text across several status bar icons"
            }
        )
    }
}
