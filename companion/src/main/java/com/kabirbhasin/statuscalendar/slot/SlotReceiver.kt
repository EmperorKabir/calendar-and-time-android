package com.kabirbhasin.statuscalendar.slot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Companion slot. Android grants one status bar icon slot per package, so each
 * installed slot app contributes one more genuine in-bar icon. The main app
 * broadcasts the text this slot should draw.
 */
class SlotReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SHOW = "com.kabirbhasin.statuscalendar.SHOW_SLOT"
        const val ACTION_CLEAR = "com.kabirbhasin.statuscalendar.CLEAR_SLOT"
        const val EXTRA_TEXT = "text"
        const val EXTRA_TOP = "top"
        const val EXTRA_BOTTOM = "bottom"
        private const val CHANNEL_ID = "slot_display"
        private const val NOTIFICATION_ID = 1
    }

    override fun onReceive(context: Context, intent: Intent) {
        val manager = NotificationManagerCompat.from(context)
        if (intent.action == ACTION_CLEAR) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (intent.action != ACTION_SHOW) return

        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val top = intent.getStringExtra(EXTRA_TOP)
        val bottom = intent.getStringExtra(EXTRA_BOTTOM)
        if (text.isEmpty() && top == null) {
            manager.cancel(NOTIFICATION_ID)
            return
        }

        ensureChannel(context)
        val bitmap = render(text, top, bottom)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(IconCompat.createWithBitmap(bitmap))
            .setContentTitle(if (top != null) "$top $bottom" else text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setSortKey("0")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        if (manager.areNotificationsEnabled()) {
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Slot display", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
        )
    }

    private fun render(text: String, top: String?, bottom: String?): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()

        fun fit(value: String, maxWidth: Float, maxHeight: Float) {
            paint.textSize = 100f
            paint.getTextBounds(value, 0, value.length, bounds)
            val scale = minOf(
                maxWidth / bounds.width().coerceAtLeast(1),
                maxHeight / bounds.height().coerceAtLeast(1)
            )
            paint.textSize = 100f * scale
        }

        fun baseline(centreY: Float) = centreY - (paint.descent() + paint.ascent()) / 2f

        if (top != null && bottom != null) {
            fit(top, size * 0.94f, size * 0.30f)
            canvas.drawText(top, size / 2f, baseline(size * 0.19f), paint)
            fit(bottom, size * 0.94f, size * 0.58f)
            canvas.drawText(bottom, size / 2f, baseline(size * 0.66f), paint)
        } else if (text.isNotEmpty()) {
            fit(text, size * 0.96f, size * 0.62f)
            canvas.drawText(text, size / 2f, baseline(size / 2f), paint)
        }
        return bitmap
    }
}
