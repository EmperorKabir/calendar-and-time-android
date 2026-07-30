package com.kabirbhasin.statuscalendar.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kabirbhasin.statuscalendar.engine.notification.NotificationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class DisplayService : Service() {

    companion object {
        fun start(context: Context) {
            // ColorOS and Android 12+ refuse background foreground-service starts;
            // failing to launch must never take the app down with it.
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, DisplayService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DisplayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var controller: DisplayController

    override fun onCreate() {
        super.onCreate()
        controller = DisplayController.get(this)
        controller.onStopRequested = { stopSelf() }
        // Only take ownership of the display if the system actually let us go
        // foreground; otherwise leave it to whichever host is already running.
        if (startedForeground()) {
            controller.start()
        } else {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Folding, unfolding and rotation all move the status bar and its cutouts, so
        // one further insets driven placement is permitted here.
        controller.overlayEngine.allowReplacement()
        controller.overlayEngine.refreshPlacement()
    }

    override fun onDestroy() {
        // The controller is shared; tearing it down here would blank the display
        // for the settings screen and the accessibility host as well.
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startedForeground(): Boolean =
        runCatching { startForegroundInternal() }.isSuccess

    private fun startForegroundInternal() {
        val notification = controller.foregroundNotification()
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(
            this, NotificationEngine.NOTIFICATION_ID, notification, type
        )
    }
}
