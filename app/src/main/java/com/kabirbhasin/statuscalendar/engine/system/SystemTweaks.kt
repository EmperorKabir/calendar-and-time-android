package com.kabirbhasin.statuscalendar.engine.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Tier-3 system integration: writes the hidden SystemUI settings that control
 * the REAL status bar clock. Requires WRITE_SECURE_SETTINGS, grantable without
 * root via one adb command (or Shizuku):
 *   adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS
 */
class SystemTweaks(private val context: Context) {

    companion object {
        private const val CLOCK_SECONDS = "clock_seconds"
        private const val ICON_BLACKLIST = "icon_blacklist"
        private const val CLOCK_ICON = "clock"
    }

    fun granted(): Boolean = context.checkSelfPermission(
        Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

    fun grantCommand(): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Live seconds on the system status bar clock. */
    fun isClockSecondsOn(): Boolean =
        Settings.Secure.getInt(context.contentResolver, CLOCK_SECONDS, 0) == 1

    fun setClockSeconds(on: Boolean): Boolean = write {
        Settings.Secure.putInt(context.contentResolver, CLOCK_SECONDS, if (on) 1 else 0)
    }

    /** Hide the system clock so this app's display can take its place. */
    fun isSystemClockHidden(): Boolean =
        blacklist().contains(CLOCK_ICON)

    fun setSystemClockHidden(hidden: Boolean): Boolean = write {
        val entries = blacklist().toMutableSet()
        if (hidden) entries.add(CLOCK_ICON) else entries.remove(CLOCK_ICON)
        Settings.Secure.putString(
            context.contentResolver, ICON_BLACKLIST, entries.joinToString(",")
        )
    }

    private fun blacklist(): Set<String> =
        Settings.Secure.getString(context.contentResolver, ICON_BLACKLIST)
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun write(block: () -> Unit): Boolean {
        if (!granted()) return false
        return runCatching { block() }.isSuccess
    }
}
