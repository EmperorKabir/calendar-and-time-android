package com.kabirbhasin.statuscalendar.core.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.map

@androidx.compose.runtime.Immutable
data class OverlayStyle(
    val offsetX: Int,
    val offsetY: Int,
    val textSizeSp: Float,
    val textColor: Long,
    val hideInFullscreen: Boolean
)

enum class DisplayMode { COMPACT, FULL_TEXT, BOTH }

@androidx.compose.runtime.Immutable
data class SavedPreset(val name: String, val spec: FormatSpec)

@androidx.compose.runtime.Immutable
data class SavedOverlayPreset(val name: String, val style: OverlayStyle)

@androidx.compose.runtime.Immutable
data class AppSettings(
    val displayEnabled: Boolean,
    val notificationEngineEnabled: Boolean,
    val overlayEngineEnabled: Boolean,
    val slotEngineEnabled: Boolean,
    val displayMode: DisplayMode,
    val startOnBoot: Boolean,
    val formatSpec: FormatSpec,
    val overlayStyle: OverlayStyle,
    val savedPresets: List<SavedPreset>,
    val savedOverlayPresets: List<SavedOverlayPreset>
)

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    // A corrupted store throws from the flow itself, upstream of any map, so it
    // cannot be caught downstream. Replacing it keeps the app usable.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val displayEnabled = booleanPreferencesKey("display_enabled")
        val notifEngine = booleanPreferencesKey("engine_notification")
        val overlayEngine = booleanPreferencesKey("engine_overlay")
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
        val savedOverlayPresets = stringPreferencesKey("saved_overlay_presets")
    }

    val flow: Flow<AppSettings> = context.dataStore.data
        .map { p -> runCatching { readSettings(p) }.getOrElse { defaults() } }
        // A read error must degrade to defaults for that emission without ending the
        // flow: catch is terminal, so a single transient IO failure previously froze
        // settings for the life of the process. Retrying keeps the stream alive.
        .retryWhen { cause, attempt ->
            if (cause is java.io.IOException && attempt < 3) {
                kotlinx.coroutines.delay(200)
                true
            } else false
        }
        .catch { emit(defaults()) }

    private fun defaults(): AppSettings = AppSettings(
        displayEnabled = false,
        notificationEngineEnabled = true,
        overlayEngineEnabled = false,
        slotEngineEnabled = true,
        displayMode = DisplayMode.COMPACT,
        startOnBoot = true,
        formatSpec = Presets.compactDate.spec,
        overlayStyle = OverlayStyle(420, 0, 13f, 0xFFFFFFFF, true),
        savedPresets = emptyList(),
        savedOverlayPresets = emptyList()
    )

    private fun readSettings(p: androidx.datastore.preferences.core.Preferences): AppSettings {
        val d = Presets.compactDate.spec
        return AppSettings(
            displayEnabled = p.safe(Keys.displayEnabled) ?: false,
            notificationEngineEnabled = enumPref(p.safe(Keys.displayMode), DisplayMode.COMPACT) != DisplayMode.FULL_TEXT,
            overlayEngineEnabled = enumPref(p.safe(Keys.displayMode), DisplayMode.COMPACT) != DisplayMode.COMPACT,
            slotEngineEnabled = p.safe(Keys.slotEngine) ?: true,
            displayMode = enumPref(p.safe(Keys.displayMode), DisplayMode.COMPACT),
            startOnBoot = p.safe(Keys.startOnBoot) ?: true,
            formatSpec = FormatSpec(
                order = p.safe(Keys.order)?.split(",")
                    ?.mapNotNull { runCatching { DisplayElement.valueOf(it) }.getOrNull() }
                    ?.ifEmpty { d.order } ?: d.order,
                dowStyle = enumPref(p.safe(Keys.dowStyle), d.dowStyle),
                dateConfig = DateConfig(
                    showDay = p.safe(Keys.showDay) ?: d.dateConfig.showDay,
                    dayPadded = p.safe(Keys.dayPadded) ?: d.dateConfig.dayPadded,
                    dayOrdinal = p.safe(Keys.dayOrdinal) ?: d.dateConfig.dayOrdinal,
                    ordinalSuperscript = p.safe(Keys.ordinalSuper) ?: d.dateConfig.ordinalSuperscript,
                    monthStyle = enumPref(p.safe(Keys.monthStyle), d.dateConfig.monthStyle),
                    yearStyle = enumPref(p.safe(Keys.yearStyle), d.dateConfig.yearStyle),
                    order = enumPref(p.safe(Keys.dateOrder), d.dateConfig.order),
                    separator = p.safe(Keys.dateSeparator) ?: d.dateConfig.separator
                ),
                timeConfig = com.kabirbhasin.statuscalendar.core.format.TimeConfig(
                    hourStyle = enumPref(p.safe(Keys.hourStyle), d.timeConfig.hourStyle),
                    showSeconds = p.safe(Keys.seconds) ?: d.timeConfig.showSeconds,
                    amPm = enumPref(p.safe(Keys.amPm), d.timeConfig.amPm)
                ),
                separator = p.safe(Keys.separator) ?: d.separator,
                stackMode = p.safe(Keys.stack) ?: d.stackMode
            ),
            overlayStyle = OverlayStyle(
                offsetX = p.safe(Keys.overlayX) ?: 420,
                offsetY = p.safe(Keys.overlayY) ?: 0,
                textSizeSp = p.safe(Keys.overlaySize) ?: 13f,
                textColor = p.safe(Keys.overlayColor)?.toLongOrNull(16) ?: 0xFFFFFFFF,
                hideInFullscreen = p.safe(Keys.overlayHideFullscreen) ?: true
            ),
            savedPresets = decodePresets(p.safe(Keys.savedPresets)),
            savedOverlayPresets = decodeOverlayPresets(p.safe(Keys.savedOverlayPresets))
        )
    }

    val defaultOverlayStyle = OverlayStyle(420, 0, 13f, 0xFFFFFFFF, true)

    suspend fun resetOverlayStyle() = setOverlayStyle(defaultOverlayStyle)

    suspend fun saveOverlayPreset(name: String, style: OverlayStyle) =
        context.dataStore.edit { prefs ->
            val kept = decodeOverlayPresets(prefs[Keys.savedOverlayPresets])
                .filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[Keys.savedOverlayPresets] =
                (kept + SavedOverlayPreset(name, style)).joinToString("") { o ->
                    listOf(
                        o.name, o.style.offsetX, o.style.offsetY, o.style.textSizeSp,
                        o.style.textColor.toString(16), o.style.hideInFullscreen
                    ).joinToString("")
                }
        }

    suspend fun deleteOverlayPreset(name: String) = context.dataStore.edit { prefs ->
        prefs[Keys.savedOverlayPresets] =
            decodeOverlayPresets(prefs[Keys.savedOverlayPresets])
                .filterNot { it.name == name }
                .joinToString("") { o ->
                    listOf(
                        o.name, o.style.offsetX, o.style.offsetY, o.style.textSizeSp,
                        o.style.textColor.toString(16), o.style.hideInFullscreen
                    ).joinToString("")
                }
    }

    private fun decodeOverlayPresets(raw: String?): List<SavedOverlayPreset> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split("").mapNotNull { entry ->
            val f = entry.split("")
            if (f.size < 6) return@mapNotNull null
            runCatching {
                SavedOverlayPreset(
                    f[0],
                    OverlayStyle(
                        offsetX = f[1].toInt(),
                        offsetY = f[2].toInt(),
                        textSizeSp = f[3].toFloat(),
                        textColor = f[4].toLong(16),
                        hideInFullscreen = f[5].toBoolean()
                    )
                )
            }.getOrNull()
        }
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
                // Tolerate extra fields written by a newer version, and refuse only
            // when required fields are genuinely missing, so an added property can
            // never silently erase a user's saved presets.
            if (f.size < 16) return@mapNotNull null
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

    /** Reading a key whose stored type no longer matches must not crash the app. */
    private fun <T> androidx.datastore.preferences.core.Preferences.safe(
        key: androidx.datastore.preferences.core.Preferences.Key<T>
    ): T? = runCatching { this[key] }.getOrNull()

    suspend fun setDisplayEnabled(value: Boolean) =
        context.dataStore.edit { it[Keys.displayEnabled] = value }

    suspend fun setNotificationEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.notifEngine] = value }

    suspend fun setOverlayEngine(value: Boolean) =
        context.dataStore.edit { it[Keys.overlayEngine] = value }

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
