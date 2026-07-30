package com.kabirbhasin.statuscalendar.core.tick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Delivers render ticks with minimal battery cost:
 * - minute-level changes ride the system's own ACTION_TIME_TICK broadcast;
 * - second-level ticks use a Handler aligned to the wall-clock second and run
 *   ONLY while the screen is on and seconds are wanted;
 * - no alarms, no wakelocks. Screen-off renders are suppressed and replayed
 *   once on SCREEN_ON.
 */
class TickSource(
    private val context: Context,
    private val onRender: () -> Unit
) {

    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var secondsWanted = false
    private var screenOn = true
    private var dirtyWhileOff = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    if (dirtyWhileOff) {
                        dirtyWhileOff = false
                        onRender()
                    }
                    restartSecondsIfWanted()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    stopSecondsTicker()
                }
                else -> tick()
            }
        }
    }

    private val secondsTicker = object : Runnable {
        override fun run() {
            onRender()
            scheduleNextSecond()
        }
    }

    fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // Android 14 requires the export intent to be stated; these are system
        // broadcasts only, so the receiver is not exported.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
        tick()
    }

    fun stop() {
        if (!registered) return
        context.unregisterReceiver(receiver)
        registered = false
        stopSecondsTicker()
    }

    fun setSecondsWanted(wanted: Boolean) {
        if (secondsWanted == wanted) return
        secondsWanted = wanted
        stopSecondsTicker()
        restartSecondsIfWanted()
    }

    private fun tick() {
        if (screenOn) onRender() else dirtyWhileOff = true
    }

    private fun restartSecondsIfWanted() {
        if (secondsWanted && screenOn && registered) scheduleNextSecond()
    }

    private fun scheduleNextSecond() {
        val delay = 1000L - (System.currentTimeMillis() % 1000L)
        handler.postAtTime(secondsTicker, SystemClock.uptimeMillis() + delay)
    }

    private fun stopSecondsTicker() {
        handler.removeCallbacks(secondsTicker)
    }
}
