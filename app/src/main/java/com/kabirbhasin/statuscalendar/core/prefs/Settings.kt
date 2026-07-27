package com.kabirbhasin.statuscalendar.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kabirbhasin.statuscalendar.core.format.DateStyle
import com.kabirbhasin.statuscalendar.core.format.DisplayElement
import com.kabirbhasin.statuscalendar.core.format.DowStyle
import com.kabirbhasin.statuscalendar.core.format.FormatSpec
import com.kabirbhasin.statuscalendar.core.format.Presets
import com.kabirbhasin.statuscalendar.core.format.TimeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class OverlayStyle(
    val offsetX: Int,
    val offsetY: Int,
    val textSizeSp: Float,
    val textColor: Long,
    val hideInFullscreen: Boolean
)

data class AppSettings(
    val displayEnabled: Boolean,
    val notificationEngineEnabled: Boolean,
    val overlayEngineEnabled: Boolean,
    val startOnBoot: Boolean,
    val formatSpec: FormatSpec,
    val overlayStyle: OverlayStyle
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val displayEnabled = booleanPreferencesKey("display_enabled")
        val notifEngine = booleanPreferencesKey("engine_notification")
        val overlayEngine = booleanPreferencesKey("engine_overlay")
        val startOnBoot = booleanPreferencesKey("start_on_boot")

        val order = stringPreferencesKey("format_order")
        val dowStyle = stringPreferencesKey("format_dow")
        val dateStyle = stringPreferencesKey("format_date")
        val timeStyle = stringPreferencesKey("format_time")
        val seconds = booleanPreferencesKey("format_seconds")
        val amPm = booleanPreferencesKey("format_ampm")
        val leadingZero = booleanPreferencesKey("format_leading_zero")
        val separator = stringPreferencesKey("format_separator")
        val stack = booleanPreferencesKey("format_stack")

        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val overlaySize = floatPreferencesKey("overlay_size")
        val overlayColor = stringPreferencesKey("overlay_color")
        val overlayHideFullscreen = booleanPreferencesKey("overlay_hide_fullscreen")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val default = Presets.compactDate.spec
        AppSettings(
            displayEnabled = p[Keys.displayEnabled] ?: false,
            notificationEngineEnabled = p[Keys.notifEngine] ?: true,
            overlayEngineEnabled = p[Keys.overlayEngine] ?: false,
            startOnBoot = p[Keys.startOnBoot] ?: true,
            formatSpec = FormatSpec(
                order = p[Keys.order]?.split(",")
                    ?.mapNotNull { runCatching { DisplayElement.valueOf(it) }.getOrNull() }
                    ?.ifEmpty { default.order } ?: default.order,
                dowStyle = p[Keys.dowStyle]?.let { enumOr(it, default.dowStyle) } ?: default.dowStyle,
                dateStyle = p[Keys.dateStyle]?.let { enumOr(it, default.dateStyle) } ?: default.dateStyle,
                timeStyle = p[Keys.timeStyle]?.let { enumOr(it, default.timeStyle) } ?: default.timeStyle,
                showSeconds = p[Keys.seconds] ?: default.showSeconds,
                showAmPm = p[Keys.amPm] ?: default.showAmPm,
                leadingZero = p[Keys.leadingZero] ?: default.leadingZero,
                separator = p[Keys.separator] ?: default.separator,
                stackMode = p[Keys.stack] ?: default.stackMode
            ),
            overlayStyle = OverlayStyle(
                offsetX = p[Keys.overlayX] ?: 0,
                offsetY = p[Keys.overlayY] ?: 0,
                textSizeSp = p[Keys.overlaySize] ?: 13f,
                textColor = p[Keys.overlayColor]?.toLongOrNull(16) ?: 0xFFFFFFFF,
                hideInFullscreen = p[Keys.overlayHideFullscreen] ?: true
            )
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    suspend fun setDisplayEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.displayEnabled] = value }

    suspend fun setNotificationEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.notifEngine] = value }

    suspend fun setOverlayEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.overlayEngine] = value }

    suspend fun setStartOnBoot(value: Boolean) =
        context.dataStore.edit { it[Keys.startOnBoot] = value }

    suspend fun setFormatSpec(spec: FormatSpec) = context.dataStore.edit {
        it[Keys.order] = spec.order.joinToString(",") { e -> e.name }
        it[Keys.dowStyle] = spec.dowStyle.name
        it[Keys.dateStyle] = spec.dateStyle.name
        it[Keys.timeStyle] = spec.timeStyle.name
        it[Keys.seconds] = spec.showSeconds
        it[Keys.amPm] = spec.showAmPm
        it[Keys.leadingZero] = spec.leadingZero
        it[Keys.separator] = spec.separator
        it[Keys.stack] = spec.stackMode
    }

    suspend fun setOverlayStyle(style: OverlayStyle) = context.dataStore.edit {
        it[Keys.overlayX] = style.offsetX
        it[Keys.overlayY] = style.offsetY
        it[Keys.overlaySize] = style.textSizeSp
        it[Keys.overlayColor] = style.textColor.toString(16)
        it[Keys.overlayHideFullscreen] = style.hideInFullscreen
    }
}
