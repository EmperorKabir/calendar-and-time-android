package com.kabirbhasin.statuscalendar.core.oem

import android.os.Build

enum class OemFamily { COLOR_OS, ONE_UI, MIUI, PIXEL_AOSP, OTHER }

/**
 * OEM detection plus the per-family guidance the status bar behaviour demands.
 * Detection uses only public Build fields — no reflection into SystemProperties,
 * which OEMs increasingly block.
 */
data class OemProfile(
    val family: OemFamily,
    val label: String,
    val iconCaveat: String?,
    val backgroundSteps: List<String>
) {
    companion object {
        fun detect(): OemProfile {
            val manufacturer = Build.MANUFACTURER.lowercase()
            return when {
                manufacturer.contains("oppo") || manufacturer.contains("oneplus") ||
                    manufacturer.contains("realme") -> OemProfile(
                    family = OemFamily.COLOR_OS,
                    label = "ColorOS / OxygenOS (OPPO, OnePlus, realme)",
                    iconCaveat = "This phone shows the app's logo in the status bar instead " +
                        "of the icon's text. Use the text overlay for readable text in the bar.",
                    backgroundSteps = listOf(
                        "Settings → Apps → Status Calendar → Allow background activity",
                        "Battery → More settings → allow auto-launch for Status Calendar"
                    )
                )
                manufacturer.contains("samsung") -> OemProfile(
                    family = OemFamily.ONE_UI,
                    label = "One UI (Samsung)",
                    iconCaveat = "Samsung limits how many notification icons show; " +
                        "keep other notifications low or use the text overlay.",
                    backgroundSteps = listOf(
                        "Settings → Battery → Background usage limits → add Status Calendar " +
                            "to Never-sleeping apps"
                    )
                )
                manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ||
                    manufacturer.contains("poco") -> OemProfile(
                    family = OemFamily.MIUI,
                    label = "MIUI / HyperOS (Xiaomi)",
                    iconCaveat = "MIUI may hide notification icons; enable the text overlay " +
                        "for a reliable display.",
                    backgroundSteps = listOf(
                        "Settings → Apps → Status Calendar → Autostart ON",
                        "Battery saver → No restrictions for Status Calendar"
                    )
                )
                manufacturer.contains("google") -> OemProfile(
                    family = OemFamily.PIXEL_AOSP,
                    label = "Pixel / stock Android",
                    iconCaveat = null,
                    backgroundSteps = listOf(
                        "Settings → Apps → Status Calendar → Battery → Unrestricted"
                    )
                )
                else -> OemProfile(
                    family = OemFamily.OTHER,
                    label = Build.MANUFACTURER,
                    iconCaveat = null,
                    backgroundSteps = listOf(
                        "Exempt Status Calendar from battery optimisation in system settings"
                    )
                )
            }
        }
    }
}
