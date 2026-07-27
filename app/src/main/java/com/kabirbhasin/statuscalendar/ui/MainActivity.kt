package com.kabirbhasin.statuscalendar.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kabirbhasin.statuscalendar.core.format.AmPmStyle
import com.kabirbhasin.statuscalendar.core.format.DateOrder
import com.kabirbhasin.statuscalendar.core.format.DisplayElement
import com.kabirbhasin.statuscalendar.core.format.DowStyle
import com.kabirbhasin.statuscalendar.core.format.FormatEngine
import com.kabirbhasin.statuscalendar.core.format.FormatSpec
import com.kabirbhasin.statuscalendar.core.format.HourStyle
import com.kabirbhasin.statuscalendar.core.format.MonthStyle
import com.kabirbhasin.statuscalendar.core.format.Presets
import com.kabirbhasin.statuscalendar.core.format.YearStyle
import com.kabirbhasin.statuscalendar.core.prefs.AppSettings
import com.kabirbhasin.statuscalendar.core.prefs.SettingsRepository
import com.kabirbhasin.statuscalendar.service.DisplayService
import com.kabirbhasin.statuscalendar.ui.theme.StatusCalendarTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = SettingsRepository(applicationContext)
        setContent {
            StatusCalendarTheme {
                SettingsScreen(repository)
            }
        }
    }
}

private val elementOrders: List<List<DisplayElement>> = listOf(
    listOf(DisplayElement.DOW, DisplayElement.DATE, DisplayElement.TIME),
    listOf(DisplayElement.DOW, DisplayElement.TIME, DisplayElement.DATE),
    listOf(DisplayElement.DATE, DisplayElement.DOW, DisplayElement.TIME),
    listOf(DisplayElement.DATE, DisplayElement.TIME, DisplayElement.DOW),
    listOf(DisplayElement.TIME, DisplayElement.DOW, DisplayElement.DATE),
    listOf(DisplayElement.TIME, DisplayElement.DATE, DisplayElement.DOW)
)

private val joiners = listOf(", ", " ", " · ", " - ", " | ")
private val dateSeparators = listOf("/", "-", ".", " ")

@Composable
private fun SettingsScreen(repository: SettingsRepository) {
    val settings by repository.flow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = settings ?: return
    val spec = current.formatSpec

    fun update(mutate: (FormatSpec) -> FormatSpec) {
        scope.launch { repository.setFormatSpec(mutate(spec)) }
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Re-assert the service whenever the display is meant to be on (covers
    // app updates and process death while the toggle remained enabled).
    LaunchedEffect(current.displayEnabled) {
        if (current.displayEnabled) DisplayService.start(context)
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Status Calendar", style = MaterialTheme.typography.headlineMedium)

            PreviewCard(current)

            ToggleRow("Show in status bar", current.displayEnabled) { enabled ->
                scope.launch {
                    repository.setDisplayEnabled(enabled)
                    if (enabled) DisplayService.start(context) else DisplayService.stop(context)
                }
            }
            ToggleRow("Notification icon engine", current.notificationEngineEnabled) {
                scope.launch { repository.setNotificationEngine(it) }
            }
            ToggleRow("Overlay engine (text in bar)", current.overlayEngineEnabled) {
                scope.launch { repository.setOverlayEngine(it) }
            }
            if (current.overlayEngineEnabled && !Settings.canDrawOverlays(context)) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            "package:${context.packageName}".let(android.net.Uri::parse)
                        )
                    )
                }) { Text("Grant \"draw over other apps\" for the overlay") }
            }
            if (current.overlayEngineEnabled) {
                OverlayCalibration(current, repository)
            }
            ToggleRow("Start after reboot", current.startOnBoot) {
                scope.launch { repository.setStartOnBoot(it) }
            }

            HorizontalDivider()
            Text("Quick presets", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())) {
                Presets.all.forEach { preset ->
                    AssistChip(
                        onClick = { scope.launch { repository.setFormatSpec(preset.spec) } },
                        label = { Text(preset.label) }
                    )
                }
            }

            HorizontalDivider()
            Text("Custom format", style = MaterialTheme.typography.titleMedium)

            CycleRow("Element order",
                spec.order.joinToString(" → ") { it.name.lowercase() }) {
                val index = elementOrders.indexOfFirst { it == spec.order }
                update { it.copy(order = elementOrders[(index + 1).mod(elementOrders.size)]) }
            }
            CycleRow("Joiner", "\"${spec.separator}\"") {
                val index = joiners.indexOf(spec.separator)
                update { it.copy(separator = joiners[(index + 1).mod(joiners.size)]) }
            }
            CycleRow("Day of week", when (spec.dowStyle) {
                DowStyle.FULL -> "Full (Wednesday)"
                DowStyle.SHORT -> "Short (Wed)"
                DowStyle.NARROW -> "Narrow (W)"
                DowStyle.NONE -> "Hidden"
            }) { update { it.copy(dowStyle = next(spec.dowStyle)) } }

            Text("Date", style = MaterialTheme.typography.labelLarge)
            ToggleRow("Show day of month", spec.dateConfig.showDay) { v ->
                update { it.copy(dateConfig = it.dateConfig.copy(showDay = v)) }
            }
            ToggleRow("Ordinal day (1st)", spec.dateConfig.dayOrdinal) { v ->
                update { it.copy(dateConfig = it.dateConfig.copy(dayOrdinal = v)) }
            }
            ToggleRow("Two-digit day (01)", spec.dateConfig.dayPadded) { v ->
                update { it.copy(dateConfig = it.dateConfig.copy(dayPadded = v)) }
            }
            CycleRow("Month", when (spec.dateConfig.monthStyle) {
                MonthStyle.FULL -> "Full (January)"
                MonthStyle.SHORT -> "Short (Jan)"
                MonthStyle.NUMBER_PADDED -> "Number (01)"
                MonthStyle.NUMBER -> "Number (1)"
                MonthStyle.NONE -> "Hidden"
            }) { update { it.copy(dateConfig = it.dateConfig.copy(monthStyle = next(spec.dateConfig.monthStyle))) } }
            CycleRow("Year", when (spec.dateConfig.yearStyle) {
                YearStyle.FULL -> "Full (2026)"
                YearStyle.TWO_DIGIT -> "Short (26)"
                YearStyle.NONE -> "Hidden"
            }) { update { it.copy(dateConfig = it.dateConfig.copy(yearStyle = next(spec.dateConfig.yearStyle))) } }
            CycleRow("Date order", spec.dateConfig.order.name) {
                update { it.copy(dateConfig = it.dateConfig.copy(order = next(spec.dateConfig.order))) }
            }
            CycleRow("Date separator", "\"${spec.dateConfig.separator}\"") {
                val index = dateSeparators.indexOf(spec.dateConfig.separator)
                update {
                    it.copy(dateConfig = it.dateConfig.copy(
                        separator = dateSeparators[(index + 1).mod(dateSeparators.size)]
                    ))
                }
            }

            Text("Time", style = MaterialTheme.typography.labelLarge)
            CycleRow("Hours", when (spec.timeConfig.hourStyle) {
                HourStyle.H24_PADDED -> "24-hour (09)"
                HourStyle.H24 -> "24-hour (9)"
                HourStyle.H12_PADDED -> "12-hour (09)"
                HourStyle.H12 -> "12-hour (9)"
                HourStyle.NONE -> "Hidden"
            }) { update { it.copy(timeConfig = it.timeConfig.copy(hourStyle = next(spec.timeConfig.hourStyle))) } }
            ToggleRow("Seconds", spec.timeConfig.showSeconds) { v ->
                update { it.copy(timeConfig = it.timeConfig.copy(showSeconds = v)) }
            }
            CycleRow("AM/PM", when (spec.timeConfig.amPm) {
                AmPmStyle.NONE -> "Hidden"
                AmPmStyle.LOWERCASE -> "am/pm"
                AmPmStyle.UPPERCASE -> "AM/PM"
            }) { update { it.copy(timeConfig = it.timeConfig.copy(amPm = next(spec.timeConfig.amPm))) } }

            ToggleRow("Calendar-icon stack", spec.stackMode) { v ->
                update { it.copy(stackMode = v) }
            }

            HorizontalDivider()
            BatteryCard()
        }
    }
}

private inline fun <reified T : Enum<T>> next(value: T): T {
    val values = enumValues<T>()
    return values[(value.ordinal + 1) % values.size]
}

@Composable
private fun PreviewCard(settings: AppSettings) {
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = ZonedDateTime.now()
            delay(1000)
        }
    }
    val rendered = FormatEngine.render(settings.formatSpec, now, Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Preview", style = MaterialTheme.typography.labelMedium)
            val text = if (rendered.stackTop != null) {
                "${rendered.stackTop}\n${rendered.stackBottom}" +
                    if (rendered.line.isNotEmpty()) "\n${rendered.line}" else ""
            } else rendered.line.ifEmpty { "(nothing selected)" }
            Text(text, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CycleRow(label: String, value: String, onCycle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onCycle) { Text(value) }
    }
}

@Composable
private fun OverlayCalibration(
    settings: AppSettings,
    repository: SettingsRepository
) {
    val scope = rememberCoroutineScope()
    val style = settings.overlayStyle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Overlay position and size", style = MaterialTheme.typography.labelLarge)
            SliderRow("Horizontal", style.offsetX.toFloat(), 0f..1400f) {
                scope.launch { repository.setOverlayStyle(style.copy(offsetX = it.toInt())) }
            }
            SliderRow("Vertical", style.offsetY.toFloat(), 0f..200f) {
                scope.launch { repository.setOverlayStyle(style.copy(offsetY = it.toInt())) }
            }
            SliderRow("Text size", style.textSizeSp, 8f..24f) {
                scope.launch { repository.setOverlayStyle(style.copy(textSizeSp = it)) }
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${value.toInt()}", style = MaterialTheme.typography.bodySmall)
        androidx.compose.material3.Slider(
            value = value, onValueChange = onChange, valueRange = range
        )
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Reliability", style = MaterialTheme.typography.titleMedium)
            Text(
                "To keep the display alive on aggressive devices, exempt this app " +
                    "from battery optimisation and allow background activity.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Open battery settings") }
        }
    }
}
