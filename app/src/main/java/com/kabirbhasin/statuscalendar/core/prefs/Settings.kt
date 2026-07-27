package com.kabirbhasin.statuscalendar.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kabirbhasin.statuscalendar.core.format.AmPmStyle
import com.kabirbhasin.statuscalendar.core.format.DateConfig
import com.kabirbhasin.statuscalendar.core.format.DateOrder
import com.kabirbhasin.statuscalendar.core.format.DisplayElement
import com.kabirbhasin.statuscalendar.core.format.DowStyle
import com.kabirbhasin.statuscalendar.core.format.FormatSpec
import com.kabirbhasin.statuscalendar.core.format.HourStyle
import com.kabirbhasin.statuscalendar.core.format.MonthStyle
import com.kabirbhasin.statuscalendar.core.format.Presets
import com.kabirbhasin.statuscalendar.core.format.YearStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class OverlayStyle(
    val offsetX: Int,
    val offsetY: Int,
    val textSizeSp: Float,
    val textColor: Long,
    val hideInFullscreen: Boolean
)

enum class DisplayMode { COMPACT, FULL_TEXT, BOTH }

data class SavedPreset(val name: String, val spec: FormatSpec)

data class AppSettings(
    val displayEnabled: Boolean,
    val notificationEngineEnabled: Boolean,
    val overlayEngineEnabled: Boolean,
    val chainedEngineEnabled: Boolean,
    val slotEngineEnabled: Boolean,
    val displayMode: DisplayMode,
    val startOnBoot: Boolean,
    val formatSpec: FormatSpec,
    val overlayStyle: OverlayStyle,
    val savedPresets: List<SavedPreset>
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val displayEnabled = booleanPreferencesKey("display_enabled")
        val notifEngine = booleanPreferencesKey("engine_notification")
        val overlayEngine = booleanPreferencesKey("engine_overlay")
        val chainedEngine = booleanPreferencesKey("engine_chained")
        val slotEngine = booleanPreferencesKey("engine_slots")
        val displayMode = stringPreferencesKey("display_mode")
        val startOnBoot = booleanPreferencesKey("start_on_boot")

        val order = stringPreferencesKey("format_order")
        val dowStyle = stringPreferencesKey("format_dow")
        val showDay = booleanPreferencesKey("format_show_day")
        val dayPadded = booleanPreferencesKey("format_day_padded")
        val dayOrdinal = booleanPreferencesKey("format_day_ordinal")
        val ordinalSuper = booleanPreferencesKey("format_ordinal_superscript")
        val monthStyle = stringPreferencesKey("format_month")
        val yearStyle = stringPreferencesKey("format_year")
        val dateOrder = stringPreferencesKey("format_date_order")
        val dateSeparator = stringPreferencesKey("format_date_separator")
        val hourStyle = stringPreferencesKey("format_hour")
        val seconds = booleanPreferencesKey("format_seconds")
        val amPm = stringPreferencesKey("format_ampm")
        val separator = stringPreferencesKey("format_separator")
        val stack = booleanPreferencesKey("format_stack")

        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val overlaySize = floatPreferencesKey("overlay_size")
        val overlayColor = stringPreferencesKey("overlay_color")
        val overlayHideFullscreen = booleanPreferencesKey("overlay_hide_fullscreen")
        val savedPresets = stringPreferencesKey("saved_presets")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        val d = Presets.compactDate.spec
        AppSettings(
            displayEnabled = p[Keys.displayEnabled] ?: false,
            notificationEngineEnabled = enumPref(p[Keys.displayMode], DisplayMode.COMPACT) != DisplayMode.FULL_TEXT,
            overlayEngineEnabled = enumPref(p[Keys.displayMode], DisplayMode.COMPACT) != DisplayMode.COMPACT,
            chainedEngineEnabled = false,
            slotEngineEnabled = p[Keys.slotEngine] ?: true,
            displayMode = enumPref(p[Keys.displayMode], DisplayMode.COMPACT),
            startOnBoot = p[Keys.startOnBoot] ?: true,
            formatSpec = FormatSpec(
                order = p[Keys.order]?.split(",")
                    ?.mapNotNull { runCatching { DisplayElement.valueOf(it) }.getOrNull() }
                    ?.ifEmpty { d.order } ?: d.order,
                dowStyle = enumPref(p[Keys.dowStyle], d.dowStyle),
                dateConfig = DateConfig(
                    showDay = p[Keys.showDay] ?: d.dateConfig.showDay,
                    dayPadded = p[Keys.dayPadded] ?: d.dateConfig.dayPadded,
                    dayOrdinal = p[Keys.dayOrdinal] ?: d.dateConfig.dayOrdinal,
                    ordinalSuperscript = p[Keys.ordinalSuper] ?: d.dateConfig.ordinalSuperscript,
                    monthStyle = enumPref(p[Keys.monthStyle], d.dateConfig.monthStyle),
                    yearStyle = enumPref(p[Keys.yearStyle], d.dateConfig.yearStyle),
                    order = enumPref(p[Keys.dateOrder], d.dateConfig.order),
                    separator = p[Keys.dateSeparator] ?: d.dateConfig.separator
                ),
                timeConfig = com.kabirbhasin.statuscalendar.core.format.TimeConfig(
                    hourStyle = enumPref(p[Keys.hourStyle], d.timeConfig.hourStyle),
                    showSeconds = p[Keys.seconds] ?: d.timeConfig.showSeconds,
                    amPm = enumPref(p[Keys.amPm], d.timeConfig.amPm)
                ),
                separator = p[Keys.separator] ?: d.separator,
                stackMode = p[Keys.stack] ?: d.stackMode
            ),
            overlayStyle = OverlayStyle(
                offsetX = p[Keys.overlayX] ?: 420,
                offsetY = p[Keys.overlayY] ?: 0,
                textSizeSp = p[Keys.overlaySize] ?: 13f,
                textColor = p[Keys.overlayColor]?.toLongOrNull(16) ?: 0xFFFFFFFF,
                hideInFullscreen = p[Keys.overlayHideFullscreen] ?: true
            ),
            savedPresets = decodePresets(p[Keys.savedPresets])
        )
    }

    suspend fun savePreset(name: String, spec: FormatSpec) = context.dataStore.edit { prefs ->
        val existing = decodePresets(prefs[Keys.savedPresets])
            .filterNot { it.name.equals(name, ignoreCase = true) }
        prefs[Keys.savedPresets] = encodePresets(existing + SavedPreset(name, spec))
    }

    suspend fun deletePreset(name: String) = context.dataStore.edit { prefs ->
        val remaining = decodePresets(prefs[Keys.savedPresets]).filterNot { it.name == name }
        prefs[Keys.savedPresets] = encodePresets(remaining)
    }

    private fun encodePresets(presets: List<SavedPreset>): String =
        presets.joinToString("") { preset ->
            val s = preset.spec
            listOf(
                preset.name,
                s.order.joinToString("+") { it.name },
                s.dowStyle.name,
                s.dateConfig.showDay.toString(),
                s.dateConfig.dayPadded.toString(),
                s.dateConfig.dayOrdinal.toString(),
                s.dateConfig.ordinalSuperscript.toString(),
                s.dateConfig.monthStyle.name,
                s.dateConfig.yearStyle.name,
                s.dateConfig.order.name,
                s.dateConfig.separator,
                s.timeConfig.hourStyle.name,
                s.timeConfig.showSeconds.toString(),
                s.timeConfig.amPm.name,
                s.separator,
                s.stackMode.toString()
            ).joinToString("")
        }

    private fun decodePresets(raw: String?): List<SavedPreset> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("").mapNotNull { entry ->
            val f = entry.split("")
            if (f.size != 16) return@mapNotNull null
            runCatching {
                SavedPreset(
                    name = f[0],
                    spec = FormatSpec(
                        order = f[1].split("+").map { DisplayElement.valueOf(it) },
                        dowStyle = DowStyle.valueOf(f[2]),
                        dateConfig = DateConfig(
                            showDay = f[3].toBoolean(),
                            dayPadded = f[4].toBoolean(),
                            dayOrdinal = f[5].toBoolean(),
                            ordinalSuperscript = f[6].toBoolean(),
                            monthStyle = MonthStyle.valueOf(f[7]),
                            yearStyle = YearStyle.valueOf(f[8]),
                            order = DateOrder.valueOf(f[9]),
                            separator = f[10]
                        ),
                        timeConfig = com.kabirbhasin.statuscalendar.core.format.TimeConfig(
                            hourStyle = HourStyle.valueOf(f[11]),
                            showSeconds = f[12].toBoolean(),
                            amPm = AmPmStyle.valueOf(f[13])
                        ),
                        separator = f[14],
                        stackMode = f[15].toBoolean()
                    )
                )
            }.getOrNull()
        }
    }

    private inline fun <reified T : Enum<T>> enumPref(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    suspend fun setDisplayEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.displayEnabled] = value }

    suspend fun setNotificationEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.notifEngine] = value }

    suspend fun setOverlayEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.overlayEngine] = value }

    suspend fun setChainedEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.chainedEngine] = value }

    suspend fun setDisplayMode(mode: DisplayMode) =
        context.dataStore.edit { it[Keys.displayMode] = mode.name }

    suspend fun setSlotEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.slotEngine] = value }

    suspend fun setStartOnBoot(value: Boolean) =
        context.dataStore.edit { it[Keys.startOnBoot] = value }

    suspend fun setFormatSpec(spec: FormatSpec) = context.dataStore.edit {
        it[Keys.order] = spec.order.joinToString(",") { e -> e.name }
        it[Keys.dowStyle] = spec.dowStyle.name
        it[Keys.showDay] = spec.dateConfig.showDay
        it[Keys.dayPadded] = spec.dateConfig.dayPadded
        it[Keys.dayOrdinal] = spec.dateConfig.dayOrdinal
        it[Keys.ordinalSuper] = spec.dateConfig.ordinalSuperscript
        it[Keys.monthStyle] = spec.dateConfig.monthStyle.name
        it[Keys.yearStyle] = spec.dateConfig.yearStyle.name
        it[Keys.dateOrder] = spec.dateConfig.order.name
        it[Keys.dateSeparator] = spec.dateConfig.separator
        it[Keys.hourStyle] = spec.timeConfig.hourStyle.name
        it[Keys.seconds] = spec.timeConfig.showSeconds
        it[Keys.amPm] = spec.timeConfig.amPm.name
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
