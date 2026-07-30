package com.kabirbhasin.statuscalendar.service

import android.app.Notification
import android.content.Context
import com.kabirbhasin.statuscalendar.core.format.FormatEngine
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.core.prefs.AppSettings
import com.kabirbhasin.statuscalendar.core.prefs.SettingsRepository
import com.kabirbhasin.statuscalendar.core.tick.TickSource
import com.kabirbhasin.statuscalendar.engine.notification.NotificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Single orchestration point used by every process host (foreground service in
 * the play flavour, accessibility keep-alive in the full flavour): watches
 * settings, listens to ticks, renders through whichever engines are enabled.
 */
class DisplayController private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        @Volatile
        private var instance: DisplayController? = null

        /**
         * A single controller per process. Every host (settings screen, foreground
         * service, accessibility service) shares it, so exactly one overlay window
         * and one notification can ever exist.
         */
        fun get(context: Context, scope: CoroutineScope): DisplayController =
            instance ?: synchronized(this) {
                instance ?: DisplayController(context.applicationContext, scope)
                    .also { instance = it }
            }
    }

    val notificationEngine = NotificationEngine(context)
    val overlayEngine = com.kabirbhasin.statuscalendar.engine.overlay.OverlayEngine(context)
    val slotEngine = com.kabirbhasin.statuscalendar.engine.slots.SlotEngine(context)
    val chainedEngine =
        com.kabirbhasin.statuscalendar.engine.notification.ChainedIconEngine(context)
    private val repository = SettingsRepository(context)
    private val tickSource = TickSource(context, onRender = ::renderNow)

    private var settings: AppSettings? = null
    var onStopRequested: (() -> Unit)? = null

    fun start() {
        tickSource.start()
        repository.flow
            .onEach { applySettings(it) }
            .launchIn(scope)
    }

    fun stop() {
        tickSource.stop()
        notificationEngine.stop()
        chainedEngine.stop()
        slotEngine.stop()
        overlayEngine.stop()
    }

    /**
     * Renders one frame without any service. Used by the settings screen so the
     * display appears immediately on devices that refuse foreground service starts.
     */
    fun renderOnceFrom(appSettings: AppSettings) {
        settings = appSettings
        if (!appSettings.displayEnabled) {
            notificationEngine.stop()
            overlayEngine.stop()
            return
        }
        notificationEngine.start()
        notificationEngine.setIconVisible(appSettings.notificationEngineEnabled)
        if (appSettings.overlayEngineEnabled && overlayEngine.canDraw()) {
            overlayEngine.start()
            overlayEngine.applyStyle(appSettings.overlayStyle)
        } else {
            overlayEngine.stop()
        }
        renderNow()
    }

    /** Foreground notification for the hosting service, from current settings. */
    fun foregroundNotification(): Notification =
        notificationEngine.build(currentDisplay(secondsCapable = false))

    private fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        if (!newSettings.displayEnabled) {
            // Clear every engine's output before the host stops, otherwise
            // chained chunks posted earlier stay in the shade.
            chainedEngine.stop()
            slotEngine.stop()
            overlayEngine.stop()
            onStopRequested?.invoke()
            return
        }
        // The foreground service owns this notification, so it always runs; the
        // toggle controls whether its icon draws anything.
        notificationEngine.start()
        notificationEngine.setIconVisible(newSettings.notificationEngineEnabled)
        if (newSettings.chainedEngineEnabled) chainedEngine.start() else chainedEngine.stop()
        if (newSettings.slotEngineEnabled) slotEngine.start() else slotEngine.stop()
        if (newSettings.overlayEngineEnabled && overlayEngine.canDraw()) {
            overlayEngine.start()
            overlayEngine.applyStyle(newSettings.overlayStyle)
        } else {
            overlayEngine.stop()
        }
        // The notification icon cannot tick per second; seconds ride the overlay only.
        tickSource.setSecondsWanted(
            newSettings.formatSpec.timeConfig.showSeconds && newSettings.overlayEngineEnabled
        )
        renderNow()
    }

    private fun renderNow() {
        val current = settings ?: return
        if (!current.displayEnabled) return
        notificationEngine.render(currentDisplay(secondsCapable = false))
        if (current.slotEngineEnabled) {
            slotEngine.render(currentDisplay(secondsCapable = false))
        }
        if (current.chainedEngineEnabled) {
            chainedEngine.render(currentDisplay(secondsCapable = false))
        }
        if (current.overlayEngineEnabled) {
            overlayEngine.render(currentDisplay(secondsCapable = true))
        }
    }

    private fun currentDisplay(secondsCapable: Boolean): RenderedDisplay {
        val current = settings
        val spec = current?.formatSpec?.let {
            if (secondsCapable) it
            else it.copy(timeConfig = it.timeConfig.copy(showSeconds = false))
        } ?: com.kabirbhasin.statuscalendar.core.format.Presets.compactDate.spec
        return FormatEngine.render(spec, ZonedDateTime.now(), Locale.getDefault())
    }
}
