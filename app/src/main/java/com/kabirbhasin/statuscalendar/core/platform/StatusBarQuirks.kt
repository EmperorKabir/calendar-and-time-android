package com.kabirbhasin.statuscalendar.core.platform

import android.os.Build
import java.util.Locale

/**
 * Behaviour of the status bar that varies by manufacturer rather than by API level,
 * so it cannot be detected through any platform capability call.
 */
object StatusBarQuirks {

    /**
     * These skins draw the app's own launcher icon in the status bar instead of the
     * small icon the notification carries. Measured on a OnePlus running OxygenOS 16:
     * the bar showed the launcher artwork unchanged while the notification supplied a
     * dated, recoloured bitmap, and switching the launcher alias did not affect it
     * either — the application icon is what the skin resolves.
     *
     * There is no API that reports this, so it is matched by vendor. The consequence is
     * only a wording change in the settings screen, so a wrong guess costs nothing
     * beyond a sentence that does not apply.
     */
    private val SUBSTITUTING_VENDORS = setOf(
        "oppo", "oneplus", "realme",   // ColorOS / OxygenOS
        "xiaomi", "redmi", "poco"      // MIUI / HyperOS
    )

    val substitutesLauncherIcon: Boolean by lazy {
        val vendors = listOf(Build.MANUFACTURER, Build.BRAND)
            .map { it.orEmpty().lowercase(Locale.ROOT) }
        vendors.any { it in SUBSTITUTING_VENDORS }
    }
}
