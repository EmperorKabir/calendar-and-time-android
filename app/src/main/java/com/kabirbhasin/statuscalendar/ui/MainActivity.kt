package com.kabirbhasin.statuscalendar.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

private fun DisplayElement.label() = when (this) {
    DisplayElement.DOW -> "Day"
    DisplayElement.DATE -> "Date"
    DisplayElement.TIME -> "Time"
}

private fun orderLabel(order: List<DisplayElement>) =
    order.joinToString(" · ") { it.label() }

private val joiners = listOf(", ", " ", " · ", " - ", " | ")
private val dateSeparators = listOf("/", "-", ".", " ")

private fun joinerLabel(j: String) = when (j) {
    ", " -> "Comma  (, )"
    " " -> "Space"
    " · " -> "Dot  ( · )"
    " - " -> "Dash  ( - )"
    " | " -> "Bar  ( | )"
    else -> "\"$j\""
}

private fun dateSeparatorLabel(s: String) = when (s) {
    "/" -> "Slash  (01/02)"
    "-" -> "Hyphen  (01-02)"
    "." -> "Full stop  (01.02)"
    " " -> "Space  (01 02)"
    else -> "\"$s\""
}

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

            ToggleRow(
                "Show in status bar",
                "Master switch for the whole display.",
                current.displayEnabled
            ) { enabled ->
                scope.launch {
                    repository.setDisplayEnabled(enabled)
                    if (enabled) DisplayService.start(context) else DisplayService.stop(context)
                }
            }
            ToggleRow(
                "Status bar icon",
                "Adds a real icon to the system status bar, like other apps' icons. " +
                    "Compact text only; some phones (e.g. OPPO/OnePlus) replace it with the app logo.",
                current.notificationEngineEnabled
            ) { scope.launch { repository.setNotificationEngine(it) } }
            ToggleRow(
                "Text overlay",
                "Draws your text on top of the status bar area. Shows any format, " +
                    "including seconds, on every phone. Not visible on the lock screen.",
                current.overlayEngineEnabled
            ) { scope.launch { repository.setOverlayEngine(it) } }
            if (current.overlayEngineEnabled && !Settings.canDrawOverlays(context)) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Grant \"display over other apps\" to enable the overlay") }
            }
            if (current.overlayEngineEnabled) {
                OverlayCalibration(current, repository)
            }
            ToggleRow(
                "Start after reboot",
                "Bring the display back automatically every time the phone restarts.",
                current.startOnBoot
            ) { scope.launch { repository.setStartOnBoot(it) } }

            HorizontalDivider()
            Text("Quick presets", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Presets.all.forEach { preset ->
                    AssistChip(
                        onClick = { scope.launch { repository.setFormatSpec(preset.spec) } },
                        label = { Text(preset.label) }
                    )
                }
            }

            HorizontalDivider()
            Text("Custom format", style = MaterialTheme.typography.titleMedium)

            DropdownRow(
                "Element order",
                "Which parts show, and in what sequence.",
                orderLabel(spec.order),
                elementOrders.map { orderLabel(it) }
            ) { index -> update { it.copy(order = elementOrders[index]) } }

            DropdownRow(
                "Separator between parts",
                "Text placed between day, date and time.",
                joinerLabel(spec.separator),
                joiners.map { joinerLabel(it) }
            ) { index -> update { it.copy(separator = joiners[index]) } }

            DropdownRow(
                "Day of week",
                "How the weekday name is written.",
                dowLabel(spec.dowStyle),
                DowStyle.entries.map { dowLabel(it) }
            ) { index -> update { it.copy(dowStyle = DowStyle.entries[index]) } }

            Text("Date", style = MaterialTheme.typography.labelLarge)
            ToggleRow(
                "Day of month",
                "Show the day number (e.g. 27).",
                spec.dateConfig.showDay
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(showDay = v)) } }
            ToggleRow(
                "Ordinal day",
                "Write the day as 1st, 2nd, 3rd… Overrides the two-digit option.",
                spec.dateConfig.dayOrdinal
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(dayOrdinal = v)) } }
            if (spec.dateConfig.dayOrdinal) {
                ToggleRow(
                    "Raised suffix",
                    "Show the ordinal ending slightly raised: 1ˢᵗ instead of 1st.",
                    spec.dateConfig.ordinalSuperscript
                ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(ordinalSuperscript = v)) } }
            }
            ToggleRow(
                "Two-digit day",
                "Pad single days with a zero (01 instead of 1).",
                spec.dateConfig.dayPadded
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(dayPadded = v)) } }

            DropdownRow(
                "Month",
                "How the month is written.",
                monthLabel(spec.dateConfig.monthStyle),
                MonthStyle.entries.map { monthLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(monthStyle = MonthStyle.entries[index])) } }

            DropdownRow(
                "Year",
                "How the year is written.",
                yearLabel(spec.dateConfig.yearStyle),
                YearStyle.entries.map { yearLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(yearStyle = YearStyle.entries[index])) } }

            DropdownRow(
                "Date part order",
                "Arrangement of day, month and year within the date.",
                dateOrderLabel(spec.dateConfig.order),
                DateOrder.entries.map { dateOrderLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(order = DateOrder.entries[index])) } }

            val numericMonth = spec.dateConfig.monthStyle == MonthStyle.NUMBER_PADDED ||
                spec.dateConfig.monthStyle == MonthStyle.NUMBER
            if (numericMonth) {
                DropdownRow(
                    "Date separator",
                    "Between the numbers of a numeric date (e.g. 27/07/2026).",
                    dateSeparatorLabel(spec.dateConfig.separator),
                    dateSeparators.map { dateSeparatorLabel(it) }
                ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(separator = dateSeparators[index])) } }
            } else {
                Text(
                    "Date separator applies only when the month is a number; " +
                        "written month names use spaces.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Time", style = MaterialTheme.typography.labelLarge)
            DropdownRow(
                "Hours",
                "Clock style and zero-padding.",
                hourLabel(spec.timeConfig.hourStyle),
                HourStyle.entries.map { hourLabel(it) }
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(hourStyle = HourStyle.entries[index])) } }
            ToggleRow(
                "Seconds",
                "Tick live seconds. Shown by the text overlay only — the status bar " +
                    "icon cannot update once a second.",
                spec.timeConfig.showSeconds
            ) { v -> update { it.copy(timeConfig = it.timeConfig.copy(showSeconds = v)) } }
            DropdownRow(
                "AM/PM",
                "Morning/afternoon marker for 12-hour clocks.",
                amPmLabel(spec.timeConfig.amPm),
                AmPmStyle.entries.map { amPmLabel(it) }
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(amPm = AmPmStyle.entries[index])) } }

            ToggleRow(
                "Calendar-icon stack",
                "Show the weekday stacked above the day number, like a desk calendar " +
                    "(used by the status bar icon).",
                spec.stackMode
            ) { v -> update { it.copy(stackMode = v) } }

            HorizontalDivider()
            if (com.kabirbhasin.statuscalendar.BuildConfig.FLAVOR == "full") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No-notification mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This build can run with no notification at all: enable " +
                                "\"Status Calendar display keeper\" under Accessibility, then " +
                                "switch off the status bar icon and use the overlay or system " +
                                "clock. The service reads no screen content.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) { Text("Open accessibility settings") }
                    }
                }
            }
            SystemIntegrationCard()
            BatteryCard()
        }
    }
}

@Composable
private fun SystemIntegrationCard() {
    val context = LocalContext.current
    val tweaks = remember { com.kabirbhasin.statuscalendar.engine.system.SystemTweaks(context) }
    var refresh by remember { mutableStateOf(0) }
    val granted = remember(refresh) { tweaks.granted() }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("System clock integration", style = MaterialTheme.typography.titleMedium)
            Text(
                "Deepest option: changes the phone's OWN status bar clock rather than " +
                    "adding to it. Needs a one-off permission granted from a computer:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                tweaks.grantCommand(),
                style = MaterialTheme.typography.bodySmall
            )
            if (!granted) {
                TextButton(onClick = { refresh++ }) { Text("Check permission again") }
            } else {
                var seconds by remember { mutableStateOf(tweaks.isClockSecondsOn()) }
                var hidden by remember { mutableStateOf(tweaks.isSystemClockHidden()) }
                ToggleRow(
                    "System clock seconds",
                    "The phone's own clock ticks live seconds — no overlay needed.",
                    seconds
                ) { on -> if (tweaks.setClockSeconds(on)) seconds = on }
                ToggleRow(
                    "Hide system clock",
                    "Remove the phone's clock so this app's display takes its place.",
                    hidden
                ) { on -> if (tweaks.setSystemClockHidden(on)) hidden = on }
            }
            val oem = remember { com.kabirbhasin.statuscalendar.core.oem.OemProfile.detect() }
            Text("Your device: ${oem.label}", style = MaterialTheme.typography.labelLarge)
            oem.iconCaveat?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            oem.backgroundSteps.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun dowLabel(style: DowStyle) = when (style) {
    DowStyle.FULL -> "Full  (Wednesday)"
    DowStyle.SHORT -> "Short  (Wed)"
    DowStyle.NARROW -> "Letter  (W)"
    DowStyle.NONE -> "Hidden"
}

private fun monthLabel(style: MonthStyle) = when (style) {
    MonthStyle.FULL -> "Full name  (January)"
    MonthStyle.SHORT -> "Short name  (Jan)"
    MonthStyle.NUMBER_PADDED -> "Two-digit number  (01)"
    MonthStyle.NUMBER -> "Number  (1)"
    MonthStyle.NONE -> "Hidden"
}

private fun yearLabel(style: YearStyle) = when (style) {
    YearStyle.FULL -> "Full  (2026)"
    YearStyle.TWO_DIGIT -> "Two-digit  (26)"
    YearStyle.NONE -> "Hidden"
}

private fun dateOrderLabel(order: DateOrder) = when (order) {
    DateOrder.DMY -> "Day-Month-Year"
    DateOrder.MDY -> "Month-Day-Year"
    DateOrder.YMD -> "Year-Month-Day"
}

private fun hourLabel(style: HourStyle) = when (style) {
    HourStyle.H24_PADDED -> "24-hour, two-digit  (09:30)"
    HourStyle.H24 -> "24-hour  (9:30)"
    HourStyle.H12_PADDED -> "12-hour, two-digit  (09:30)"
    HourStyle.H12 -> "12-hour  (9:30)"
    HourStyle.NONE -> "Hidden"
}

private fun amPmLabel(style: AmPmStyle) = when (style) {
    AmPmStyle.NONE -> "Hidden"
    AmPmStyle.LOWERCASE -> "Lowercase  (am/pm)"
    AmPmStyle.UPPERCASE -> "Uppercase  (AM/PM)"
}

@Composable
private fun DropdownRow(
    label: String,
    description: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.widthIn(min = 200.dp)
            ) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelect(index)
                        }
                    )
                }
            }
        }
    }
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
private fun ToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
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
            Text(
                "Move the text so it sits in an empty part of your status bar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Slider(value = value, onValueChange = onChange, valueRange = range)
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
