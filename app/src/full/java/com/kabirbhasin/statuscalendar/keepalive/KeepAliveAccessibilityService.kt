package com.kabirbhasin.statuscalendar.keepalive

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.kabirbhasin.statuscalendar.service.DisplayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Full-flavour host: an accessibility service keeps the process alive with NO
 * notification at all, so the overlay and system-clock integration can run
 * truly notification-free. Subscribes to no events and reads no content.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: DisplayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        controller = DisplayController.get(this, scope).also {
            it.onStopRequested = { controller?.stop() }
            it.start()
        }
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
