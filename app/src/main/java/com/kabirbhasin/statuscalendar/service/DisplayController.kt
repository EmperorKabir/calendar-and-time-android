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
class DisplayController(
    private val context: Context,
    private val scope: CoroutineScope
) {

    val notificationEngine = NotificationEngine(context)
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
    }

    /** Foreground notification for the hosting service, from current settings. */
    fun foregroundNotification(): Notification =
        notificationEngine.build(currentDisplay(secondsCapable = false))

    private fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        if (!newSettings.displayEnabled) {
            onStopRequested?.invoke()
            return
        }
        if (newSettings.notificationEngineEnabled) {
            notificationEngine.start()
        } else {
            notificationEngine.stop()
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
        if (current.notificationEngineEnabled) {
            notificationEngine.render(currentDisplay(secondsCapable = false))
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
