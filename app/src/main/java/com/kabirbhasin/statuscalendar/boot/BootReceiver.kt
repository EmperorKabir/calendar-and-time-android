package com.kabirbhasin.statuscalendar.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kabirbhasin.statuscalendar.core.prefs.SettingsRepository
import com.kabirbhasin.statuscalendar.service.DisplayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = SettingsRepository(context.applicationContext).flow.first()
                if (settings.displayEnabled && settings.startOnBoot) {
                    DisplayService.start(context.applicationContext)
                }
            } catch (t: Throwable) {
                // A failure here must never crash the device's boot sequence.
            } finally {
                pending.finish()
            }
        }
    }
}
