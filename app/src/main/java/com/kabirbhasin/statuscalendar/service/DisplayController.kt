package com.kabirbhasin.statuscalendar.service

import android.app.Notification
import android.content.Context
import com.kabirbhasin.statuscalendar.core.format.FormatEngine
import com.kabirbhasin.statuscalendar.core.format.Presets
import com.kabirbhasin.statuscalendar.core.format.RenderedDisplay
import com.kabirbhasin.statuscalendar.core.prefs.AppSettings
import com.kabirbhasin.statuscalendar.core.prefs.SettingsRepository
import com.kabirbhasin.statuscalendar.core.tick.TickSource
import com.kabirbhasin.statuscalendar.engine.notification.NotificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Single orchestration point used by every process host (foreground service in
 * the play flavour, accessibility keep-alive in the full flavour): watches
 * settings, listens to ticks, renders through whichever engines are enabled.
 */
class DisplayController private constructor(
    private val context: Context
) {

    // The controller outlives any single host, so it owns its scope. Borrowing a
    // caller's scope meant an Activity being destroyed silently killed the settings
    // collector and the seconds ticker for every other host.
    private val scope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        @Volatile
        private var instance: DisplayController? = null

        /**
         * A single controller per process. Every host (settings screen, foreground
         * service, accessibility service) shares it, so exactly one overlay window
         * and one notification can ever exist.
         */
        fun get(context: Context): DisplayController =
            instance ?: synchronized(this) {
                instance ?: DisplayController(context.applicationContext)
                    .also { instance = it }
            }
    }

    val notificationEngine = NotificationEngine(context)
    val overlayEngine = com.kabirbhasin.statuscalendar.engine.overlay.OverlayEngine(context)
    val slotEngine = com.kabirbhasin.statuscalendar.engine.slots.SlotEngine(context)
    private val repository = SettingsRepository(context)
    private val tickSource = TickSource(context, onRender = ::renderNow)

    private var settings: AppSettings? = null
    var onStopRequested: (() -> Unit)? = null

    private var collecting = false

    init {
        // Measure font coverage before the first render so raised endings are never
        // drawn as empty boxes.
        com.kabirbhasin.statuscalendar.core.format.GlyphProbe.probe()
    }

    fun start() {
        // Hosts come and go; the collector must be started once and stay up. If the
        // settings flow ever terminates, the flag is cleared so it can be restarted
        // rather than leaving the app permanently unable to see its own settings.
        if (collecting) {
            tickSource.start()
            return
        }
        collecting = true
        tickSource.start()
        repository.flow
            .onEach { applySettings(it) }
            .onCompletion { collecting = false }
            .launchIn(scope)
    }

    fun stop() {
        tickSource.stop()
        notificationEngine.stop()
        slotEngine.stop()
        overlayEngine.stop()
    }

    /**
     * Renders one frame without any service. Used by the settings screen so the
     * display appears immediately on devices that refuse foreground service starts.
     */
    fun renderOnceFrom(appSettings: AppSettings) {
        settings = appSettings
        // A departing service may have stopped the ticker; bring it back or the
        // clock freezes while the settings screen is open.
        if (appSettings.displayEnabled) tickSource.start()
        if (!appSettings.displayEnabled) {
            notificationEngine.stop()
            overlayEngine.stop()
            return
        }
        notificationEngine.start()
        notificationEngine.setIconColour(appSettings.iconColor.toInt())
        notificationEngine.setIconVisible(appSettings.notificationEngineEnabled)
        if (appSettings.overlayEngineEnabled && overlayEngine.canDraw()) {
            overlayEngine.start()
            overlayEngine.applyStyle(appSettings.overlayStyle)
        } else {
            overlayEngine.stop()
        }
        renderNow()
    }

    /**
     * Foreground notification for the hosting service. Until settings arrive there is
     * nothing truthful to draw, so the icon starts blank rather than showing a default
     * format that the user may not have chosen.
     */
    fun foregroundNotification(): Notification {
        if (settings == null) notificationEngine.setIconVisible(false)
        return buildForeground()
    }

    private fun buildForeground(): Notification =
        notificationEngine.build(
            com.kabirbhasin.statuscalendar.core.format.IconDisplay.forSpec(
                settings?.formatSpec ?: Presets.compactDate.spec,
                ZonedDateTime.now(), Locale.getDefault()
            )
        )

    private fun applySettings(newSettings: AppSettings) {
        settings = newSettings
        if (!newSettings.displayEnabled) {
            // Stop ticking first: leaving the ticker and the system receiver running
            // after the display is switched off costs battery for nothing.
            tickSource.setSecondsWanted(false)
            tickSource.stop()
                slotEngine.stop()
            overlayEngine.stop()
            onStopRequested?.invoke()
            return
        }
        // The foreground service owns this notification, so it always runs; the
        // toggle controls whether its icon draws anything.
        // Bring the replacement up before tearing the old one down, so the bar is
        // never momentarily empty when the mode changes.
        notificationEngine.start()
        notificationEngine.setIconColour(newSettings.iconColor.toInt())
        notificationEngine.setIconVisible(newSettings.notificationEngineEnabled)
        renderNow()
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
        notificationEngine.render(
            com.kabirbhasin.statuscalendar.core.format.IconDisplay.forSpec(
                settings?.formatSpec ?: Presets.compactDate.spec,
                ZonedDateTime.now(), Locale.getDefault()
            )
        )
        if (current.slotEngineEnabled) {
            slotEngine.render(currentDisplay(secondsCapable = false))
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
