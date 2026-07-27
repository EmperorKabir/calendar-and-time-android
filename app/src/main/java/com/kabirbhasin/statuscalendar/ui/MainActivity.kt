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
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import com.kabirbhasin.statuscalendar.engine.notification.ChainedIconEngine
import com.kabirbhasin.statuscalendar.engine.notification.IconFactory
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
import androidx.compose.material3.OutlinedTextField
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
        // The app is always pure black, so the system bars need light contents
        // regardless of the device's light/dark setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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
    ", " -> "Comma"
    " " -> "Space"
    " · " -> "Middle dot"
    " - " -> "Dash"
    " | " -> "Vertical bar"
    else -> "\"$j\""
}

private fun dateSeparatorLabel(s: String) = when (s) {
    "/" -> "Slash (01/02)"
    "-" -> "Hyphen (01-02)"
    "." -> "Full stop (01.02)"
    " " -> "Space (01 02)"
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
    // Chunks posted by the chained mode outlive the service, so clear any
    // that remain whenever the mode is off.
    LaunchedEffect(current.chainedEngineEnabled, current.displayEnabled) {
        if (!current.chainedEngineEnabled || !current.displayEnabled) {
            ChainedIconEngine(context).stop()
        }
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Display modes", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Android only lets an app put two things inside the real status bar: " +
                            "notification icons, and the phone's own clock. Each mode below " +
                            "trades length, fidelity and notification clutter differently. " +
                            "Enable whichever combination suits your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ToggleRow(
                "Show in status bar",
                "Turn the whole display on or off.",
                current.displayEnabled
            ) { enabled ->
                scope.launch {
                    repository.setDisplayEnabled(enabled)
                    if (enabled) DisplayService.start(context) else DisplayService.stop(context)
                }
            }
            HorizontalDivider()
            Text(
                "Icon options (inside the status bar)",
                style = MaterialTheme.typography.titleMedium
            )
            ToggleRow(
                "Icon option",
                "A genuine element of the system status bar, drawn by Android itself in the " +
                    "same row as other apps' icons. Text is scaled automatically to fit the " +
                    "slot, so a date such as \"Mon 27 Jul\" stays readable. Some phones, " +
                    "including OPPO and OnePlus, substitute the app logo instead; on those, " +
                    "use system clock integration or the overlay.",
                current.notificationEngineEnabled
            ) { scope.launch { repository.setNotificationEngine(it) } }
            ToggleRow(
                "Multi icon option (experimental)",
                "Splits your text across several notifications to claim extra icon slots. " +
                    "On Android 14 and newer the system merges all of an app's icons into a " +
                    "single slot, so this only adds entries to your notification list without " +
                    "adding icons. It remains available for older versions of Android, and for " +
                    "manufacturer builds that still show one slot per notification. Leave it " +
                    "switched off unless you have confirmed that it helps on your phone.",
                current.chainedEngineEnabled
            ) { scope.launch { repository.setChainedEngine(it) } }
            HorizontalDivider()
            Text(
                "Text option (drawn over the status bar)",
                style = MaterialTheme.typography.titleMedium
            )
            ToggleRow(
                "Text option",
                "For formats the status bar itself cannot hold. Your text is drawn on top of " +
                    "the bar area at any length, with live seconds if you want them. Because " +
                    "it sits above the bar rather than inside it, the text is hidden on the " +
                    "lock screen and in fullscreen apps.",
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
                "Bring the display back automatically whenever the phone restarts.",
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
            Text("Your presets", style = MaterialTheme.typography.titleMedium)
            Text(
                "Save the current format under a name, then bring it back with one tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var presetName by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    singleLine = true,
                    label = { Text("Preset name") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = {
                        val name = presetName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch { repository.savePreset(name, spec) }
                            presetName = ""
                        }
                    }
                ) { Text("Save") }
            }
            current.savedPresets.forEach { saved ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(saved.name, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { repository.setFormatSpec(saved.spec) }
                    }) { Text("Apply") }
                    TextButton(onClick = {
                        scope.launch { repository.deletePreset(saved.name) }
                    }) { Text("Delete") }
                }
            }

            HorizontalDivider()
            Text("Custom format", style = MaterialTheme.typography.titleMedium)

            DropdownRow(
                "Element order",
                "Choose which parts appear, and in what sequence.",
                orderLabel(spec.order),
                elementOrders.map { orderLabel(it) }
            ) { index -> update { it.copy(order = elementOrders[index]) } }

            DropdownRow(
                "Separator between parts",
                "The text placed between the day, date and time.",
                joinerLabel(spec.separator),
                joiners.map { joinerLabel(it) }
            ) { index -> update { it.copy(separator = joiners[index]) } }

            DropdownRow(
                "Day of week",
                "Choose how the weekday name is written.",
                dowLabel(spec.dowStyle),
                DowStyle.entries.map { dowLabel(it) }
            ) { index -> update { it.copy(dowStyle = DowStyle.entries[index]) } }

            Text("Date", style = MaterialTheme.typography.labelLarge)
            ToggleRow(
                "Day of month",
                "Show the day number, such as 27.",
                spec.dateConfig.showDay
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(showDay = v)) } }
            ToggleRow(
                "Ordinal day",
                "Write the day as 1st, 2nd or 3rd. This overrides the two digit option.",
                spec.dateConfig.dayOrdinal
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(dayOrdinal = v)) } }
            if (spec.dateConfig.dayOrdinal) {
                ToggleRow(
                    "Raised suffix",
                    "Raise the ending slightly, so the date reads 1ˢᵗ rather than 1st.",
                    spec.dateConfig.ordinalSuperscript
                ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(ordinalSuperscript = v)) } }
            }
            ToggleRow(
                "Two digit day",
                "Add a leading zero, so the first of the month reads 01 rather than 1.",
                spec.dateConfig.dayPadded
            ) { v -> update { it.copy(dateConfig = it.dateConfig.copy(dayPadded = v)) } }

            DropdownRow(
                "Month",
                "Choose how the month is written.",
                monthLabel(spec.dateConfig.monthStyle),
                MonthStyle.entries.map { monthLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(monthStyle = MonthStyle.entries[index])) } }

            DropdownRow(
                "Year",
                "Choose how the year is written.",
                yearLabel(spec.dateConfig.yearStyle),
                YearStyle.entries.map { yearLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(yearStyle = YearStyle.entries[index])) } }

            DropdownRow(
                "Date part order",
                "Choose the arrangement of day, month and year.",
                dateOrderLabel(spec.dateConfig.order),
                DateOrder.entries.map { dateOrderLabel(it) }
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(order = DateOrder.entries[index])) } }

            val numericMonth = spec.dateConfig.monthStyle == MonthStyle.NUMBER_PADDED ||
                spec.dateConfig.monthStyle == MonthStyle.NUMBER
            if (numericMonth) {
                DropdownRow(
                    "Date separator",
                    "The character between the numbers of a numeric date, such as 27/07/2026.",
                    dateSeparatorLabel(spec.dateConfig.separator),
                    dateSeparators.map { dateSeparatorLabel(it) }
                ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(separator = dateSeparators[index])) } }
            } else {
                Text(
                    "The date separator applies only when the month is shown as a number. " +
                        "Written month names are spaced instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("Time", style = MaterialTheme.typography.labelLarge)
            DropdownRow(
                "Hours",
                "Choose the clock style and whether hours carry a leading zero.",
                hourLabel(spec.timeConfig.hourStyle),
                HourStyle.entries.map { hourLabel(it) }
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(hourStyle = HourStyle.entries[index])) } }
            ToggleRow(
                "Seconds",
                "Show live seconds. Only the text overlay can display these, because a " +
                    "status bar icon cannot update once every second.",
                spec.timeConfig.showSeconds
            ) { v -> update { it.copy(timeConfig = it.timeConfig.copy(showSeconds = v)) } }
            DropdownRow(
                "AM/PM",
                "The morning or afternoon marker used by 12 hour clocks.",
                amPmLabel(spec.timeConfig.amPm),
                AmPmStyle.entries.map { amPmLabel(it) }
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(amPm = AmPmStyle.entries[index])) } }

            ToggleRow(
                "Calendar-icon stack",
                "Show the weekday above the day number, like a desk calendar. Used by " +
                    "the status bar icon.",
                spec.stackMode
            ) { v -> update { it.copy(stackMode = v) } }

            HorizontalDivider()
            if (com.kabirbhasin.statuscalendar.BuildConfig.FLAVOR == "full") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No-notification mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This build can run without any notification at all. Enable " +
                                "\"Status Calendar display keeper\" under Accessibility, then " +
                                "switch off the status bar icon and use the overlay or the " +
                                "system clock instead. The service reads no screen content.",
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
                "The deepest option. Instead of adding to the status bar, this changes the " +
                    "phone's own clock. It needs a permission that you grant once from a " +
                    "computer, using this command:",
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
                    "The phone's own clock ticks live seconds, with no overlay needed.",
                    seconds
                ) { on -> if (tweaks.setClockSeconds(on)) seconds = on }
                ToggleRow(
                    "Hide system clock",
                    "Remove the phone's own clock so that this display takes its place.",
                    hidden
                ) { on -> if (tweaks.setSystemClockHidden(on)) hidden = on }
                var h24 by remember { mutableStateOf(tweaks.is24Hour()) }
                ToggleRow(
                    "24 hour system clock",
                    "Switch the whole phone between 12 hour and 24 hour time.",
                    h24
                ) { on -> if (tweaks.set24Hour(on)) h24 = on }
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
    DowStyle.FULL -> "Full name (Wednesday)"
    DowStyle.SHORT -> "Short name (Wed)"
    DowStyle.NARROW -> "Single letter (W)"
    DowStyle.NONE -> "Hidden"
}

private fun monthLabel(style: MonthStyle) = when (style) {
    MonthStyle.FULL -> "Full name (January)"
    MonthStyle.SHORT -> "Short name (Jan)"
    MonthStyle.NUMBER_PADDED -> "Two digit number (01)"
    MonthStyle.NUMBER -> "Number (1)"
    MonthStyle.NONE -> "Hidden"
}

private fun yearLabel(style: YearStyle) = when (style) {
    YearStyle.FULL -> "Full year (2026)"
    YearStyle.TWO_DIGIT -> "Two digits (26)"
    YearStyle.NONE -> "Hidden"
}

private fun dateOrderLabel(order: DateOrder) = when (order) {
    DateOrder.DMY -> "Day, month, year"
    DateOrder.MDY -> "Month, day, year"
    DateOrder.YMD -> "Year, month, day"
}

private fun hourLabel(style: HourStyle) = when (style) {
    HourStyle.H24_PADDED -> "24 hour with leading zero (09:30)"
    HourStyle.H24 -> "24 hour (9:30)"
    HourStyle.H12_PADDED -> "12 hour with leading zero (09:30)"
    HourStyle.H12 -> "12 hour (9:30)"
    HourStyle.NONE -> "Hidden"
}

private fun amPmLabel(style: AmPmStyle) = when (style) {
    AmPmStyle.NONE -> "Hidden"
    AmPmStyle.LOWERCASE -> "Lowercase (am/pm)"
    AmPmStyle.UPPERCASE -> "Uppercase (AM/PM)"
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
    val iconFactory = remember { IconFactory() }
    // The icon engine drops seconds, so preview exactly what it will draw.
    val iconDisplay = rendered.copy(
        line = FormatEngine.render(
            settings.formatSpec.copy(
                timeConfig = settings.formatSpec.timeConfig.copy(showSeconds = false)
            ),
            now, Locale.getDefault()
        ).line
    )
    val bitmap = remember(iconDisplay) { iconFactory.iconFor(iconDisplay) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What each mode will show", style = MaterialTheme.typography.titleMedium)

            Text("Icon option", style = MaterialTheme.typography.labelLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Status bar icon preview at actual size",
                    modifier = Modifier.size(24.dp)
                )
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Status bar icon preview enlarged",
                    modifier = Modifier.size(72.dp)
                )
            }
            val iconWarning = when {
                iconDisplay.stackTop != null -> null
                iconDisplay.line.length > 6 ->
                    "This is long for one icon slot, so it is shrunk to fit and may be hard " +
                        "to read. Turn on the calendar stack, shorten the format, or use the " +
                        "text overlay."
                iconDisplay.line.isEmpty() -> "Nothing selected to show."
                else -> null
            }
            iconWarning?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (settings.formatSpec.timeConfig.showSeconds) {
                Text(
                    "Seconds are left out here, because an icon cannot update every second.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Text("Text option", style = MaterialTheme.typography.labelLarge)
            val overlayText = if (rendered.stackTop != null) {
                listOf(rendered.stackTop, rendered.stackBottom, rendered.line.ifEmpty { null })
                    .filterNotNull().joinToString(" ")
            } else rendered.line.ifEmpty { "(nothing selected)" }
            Text(overlayText, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Shown in full, on one line, exactly as written above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
                "Move the text so that it sits in an empty part of your status bar.",
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
                "Some phones close background apps aggressively. To keep the display " +
                    "running, exempt this app from battery optimisation and allow it to " +
                    "work in the background.",
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("Open battery settings") }
        }
    }
}
