package com.kabirbhasin.statuscalendar.engine.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.time.LocalDate

/**
 * Some manufacturer skins, ColorOS among them, draw an app's launcher icon in the
 * status bar instead of its monochrome notification icon. On those phones the only way
 * to show a changing date in colour is to change the launcher icon itself, which
 * Android allows through activity aliases.
 *
 * Exactly one alias is enabled at a time. Switching is a package manager operation, so
 * it runs once a day rather than on every tick.
 */
class LauncherIconSwitcher(private val context: Context) {

    private val packageName: String = context.packageName

    private fun alias(day: Int) = ComponentName(packageName, "$packageName.Day$day")

    private val defaultEntry = ComponentName(packageName, "$packageName.ui.MainActivity")

    /** Enables the alias for [day] and disables the rest. */
    fun showDay(day: Int) {
        if (day !in 1..31) return
        val pm = context.packageManager
        if (isEnabled(pm, alias(day))) return

        runCatching {
            pm.setComponentEnabledSetting(
                alias(day),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            (1..31).filter { it != day }.forEach { other ->
                if (isEnabled(pm, alias(other))) {
                    pm.setComponentEnabledSetting(
                        alias(other),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            // The plain entry must go, or the launcher lists the app twice.
            pm.setComponentEnabledSetting(
                defaultEntry,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun showToday() = showDay(LocalDate.now().dayOfMonth)

    /** Restores the ordinary launcher entry and disables every dated alias. */
    fun restoreDefault() {
        val pm = context.packageManager
        runCatching {
            pm.setComponentEnabledSetting(
                defaultEntry,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            (1..31).forEach { day ->
                if (isEnabled(pm, alias(day))) {
                    pm.setComponentEnabledSetting(
                        alias(day),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    private fun isEnabled(pm: PackageManager, component: ComponentName): Boolean =
        pm.getComponentEnabledSetting(component) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
}
