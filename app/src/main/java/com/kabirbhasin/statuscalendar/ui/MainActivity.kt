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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import com.kabirbhasin.statuscalendar.engine.notification.ChainedIconEngine
import com.kabirbhasin.statuscalendar.engine.notification.IconFactory
import com.kabirbhasin.statuscalendar.engine.slots.SlotEngine
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.kabirbhasin.statuscalendar.core.prefs.DisplayMode
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
private val dowLabels = DowStyle.entries.map { dowLabel(it) }
private val monthLabels = MonthStyle.entries.map { monthLabel(it) }
private val yearLabels = YearStyle.entries.map { yearLabel(it) }
private val dateOrderLabels = DateOrder.entries.map { dateOrderLabel(it) }
private val hourLabels = HourStyle.entries.map { hourLabel(it) }
private val amPmLabels = AmPmStyle.entries.map { amPmLabel(it) }
private val orderLabels = elementOrders.map { orderLabel(it) }
private val joinerLabels = joiners.map { joinerLabel(it) }
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
    // Start the service where permitted, and always render directly as well so the
    // display never depends on a foreground service the OEM may refuse.
    val fallbackController = remember {
        com.kabirbhasin.statuscalendar.service.DisplayController.get(context.applicationContext)
    }
    LaunchedEffect(current) {
        if (current.displayEnabled) DisplayService.start(context)
        fallbackController.renderOnceFrom(current)
    }
    // Chunks posted by the chained mode outlive the service, so clear any
    // that remain whenever the mode is off.
    LaunchedEffect(current.chainedEngineEnabled, current.displayEnabled) {
        if (!current.chainedEngineEnabled || !current.displayEnabled) {
            ChainedIconEngine(context).stop()
        }
    }

    // On a tablet or an unfolded foldable the content is centred and width limited,
    // so lines never stretch to an unreadable length.
    val wideScreen = LocalConfiguration.current.screenWidthDp >= 600
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Pinned: the preview stays in view while the settings scroll beneath it.
            // statusBarsPadding keeps the content clear of the system bar and any cutout.
            Column(
                Modifier
                    .statusBarsPadding()
                    .then(if (wideScreen) Modifier.widthIn(max = 720.dp) else Modifier)
                    .padding(horizontal = 16.dp)
            ) {
                Text("Status Calendar", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.size(10.dp))
                PreviewCard(current)
                Spacer(Modifier.size(10.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .navigationBarsPadding()
                .then(if (wideScreen) Modifier.widthIn(max = 720.dp) else Modifier)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

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
            Text("How it should appear", style = MaterialTheme.typography.titleMedium)
            Text(
                "These choices are mutually exclusive, so pick the one that suits you.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Re-checked whenever the screen resumes, so returning from the system
            // permission page immediately unlocks the text modes.
            var overlayReady by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        overlayReady = Settings.canDrawOverlays(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            val chip = remember {
                com.kabirbhasin.statuscalendar.engine.notification
                    .NotificationEngine(context).chipSupported()
            }
            if (chip) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Your phone can show text in the bar",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This version of Android puts ongoing notifications in the status " +
                                "bar as a small chip with words in it, not just an icon. " +
                                "Compact uses it automatically, so you get readable text " +
                                "without the draw over option and without its drawbacks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            ModeChoice(
                label = "Compact",
                description = if (chip)
                    "Text in the status bar itself, shown as a chip beside the clock. " +
                        "Your phone supports this, so nothing is drawn on top of anything."
                else
                    "A readable icon inside the status bar, beside the other app icons. " +
                        "Long formats show the weekday above a large date number.",
                selected = current.displayMode == DisplayMode.COMPACT,
                enabled = true
            ) { scope.launch { repository.setDisplayMode(DisplayMode.COMPACT) } }
            ModeChoice(
                label = "Full text",
                description = if (overlayReady)
                    "Your whole format, any length, with live seconds. Drawn over the " +
                        "status bar, so it is hidden on the lock screen."
                else
                    "Needs permission to draw over other apps before it can be chosen.",
                selected = current.displayMode == DisplayMode.FULL_TEXT,
                enabled = overlayReady
            ) { scope.launch { repository.setDisplayMode(DisplayMode.FULL_TEXT) } }
            ModeChoice(
                label = "Both",
                description = if (overlayReady)
                    "The icon and the full text together. The text is placed clear of " +
                        "the clock so the two do not overlap."
                else
                    "Needs permission to draw over other apps before it can be chosen.",
                selected = current.displayMode == DisplayMode.BOTH,
                enabled = overlayReady
            ) { scope.launch { repository.setDisplayMode(DisplayMode.BOTH) } }
            if (!overlayReady) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) { Text("Grant permission to draw over other apps") }
            }
            if (current.displayMode != DisplayMode.COMPACT && overlayReady) {
                OverlayCalibration(current, repository)
            }

            ToggleRow(
                "Start after reboot",
                "Bring the display back automatically whenever the phone restarts.",
                current.startOnBoot
            ) { scope.launch { repository.setStartOnBoot(it) } }

            HorizontalDivider()
            Text("Presets", style = MaterialTheme.typography.titleMedium)
            DropdownRow(
                "Built in presets",
                "Pick a ready made format.",
                Presets.all.firstOrNull { it.spec == spec }?.label ?: "Custom",
                Presets.all.map { it.label }
            ) { index -> scope.launch { repository.setFormatSpec(Presets.all[index].spec) } }
            if (current.savedPresets.isNotEmpty()) {
                DropdownRow(
                    "Your saved presets",
                    "Apply one of your own saved formats.",
                    current.savedPresets.firstOrNull { it.spec == spec }?.name ?: "Choose",
                    current.savedPresets.map { it.name }
                ) { index ->
                    scope.launch { repository.setFormatSpec(current.savedPresets[index].spec) }
                }
            }

            Text("Save current format", style = MaterialTheme.typography.titleMedium)
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
            if (current.savedPresets.isNotEmpty()) {
                DropdownRow(
                    "Delete a saved preset",
                    "Removes one of your saved formats.",
                    "Choose",
                    current.savedPresets.map { it.name }
                ) { index ->
                    scope.launch { repository.deletePreset(current.savedPresets[index].name) }
                }
            }

            HorizontalDivider()
            Text("Custom format", style = MaterialTheme.typography.titleMedium)

            DropdownRow(
                "Element order",
                "Choose which parts appear, and in what sequence.",
                orderLabel(spec.order),
                orderLabels
            ) { index -> update { it.copy(order = elementOrders[index]) } }

            DropdownRow(
                "Separator between parts",
                "The text placed between the day, date and time.",
                joinerLabel(spec.separator),
                joinerLabels
            ) { index -> update { it.copy(separator = joiners[index]) } }

            DropdownRow(
                "Day of week",
                "Choose how the weekday name is written.",
                dowLabel(spec.dowStyle),
                dowLabels
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
                monthLabels
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(monthStyle = MonthStyle.entries[index])) } }

            DropdownRow(
                "Year",
                "Choose how the year is written.",
                yearLabel(spec.dateConfig.yearStyle),
                yearLabels
            ) { index -> update { it.copy(dateConfig = it.dateConfig.copy(yearStyle = YearStyle.entries[index])) } }

            DropdownRow(
                "Date part order",
                "Choose the arrangement of day, month and year.",
                dateOrderLabel(spec.dateConfig.order),
                dateOrderLabels
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
                hourLabels
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(hourStyle = HourStyle.entries[index])) } }
            val secondsPossible = current.displayMode != DisplayMode.COMPACT
            ToggleRow(
                "Seconds",
                if (secondsPossible)
                    "Show live seconds, updated every second."
                else
                    "Unavailable in Compact, because a status bar icon cannot update once " +
                        "every second. Choose Full text or Both to use seconds.",
                spec.timeConfig.showSeconds && secondsPossible,
                enabled = secondsPossible
            ) { v -> update { it.copy(timeConfig = it.timeConfig.copy(showSeconds = v)) } }
            DropdownRow(
                "AM/PM",
                "The morning or afternoon marker used by 12 hour clocks.",
                amPmLabel(spec.timeConfig.amPm),
                amPmLabels
            ) { index -> update { it.copy(timeConfig = it.timeConfig.copy(amPm = AmPmStyle.entries[index])) } }

            ToggleRow(
                "Calendar-icon stack",
                "Show the weekday above the day number, like a desk calendar. Used by " +
                    "the status bar icon.",
                spec.stackMode
            ) { v -> update { it.copy(stackMode = v) } }

            HorizontalDivider()
            var advancedOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "System clock changes, extra icon slots and reliability settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { advancedOpen = !advancedOpen }) {
                    Text(if (advancedOpen) "Hide" else "Show")
                }
            }
            if (advancedOpen) {
            val slotEngine = remember { SlotEngine(context) }
            val installed = remember(current) { slotEngine.installedSlots().size }
            ToggleRow(
                "Extra icon slots",
                if (installed > 0)
                    "$installed companion app(s) installed, giving $installed extra icon(s) " +
                        "inside the status bar. Your text is split across them."
                else
                    "Android gives each app one icon slot. Companion slot apps add more real " +
                        "icons inside the status bar. None are installed yet.",
                current.slotEngineEnabled
            ) { scope.launch { repository.setSlotEngine(it) } }
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
            Spacer(Modifier.size(24.dp))
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
    // Tick only while this card is on screen, and only as often as the format needs:
    // a format without seconds changes once a minute, so a one second loop was
    // recomposing the screen sixty times more than necessary.
    val needsSeconds = settings.formatSpec.timeConfig.showSeconds
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LaunchedEffect(needsSeconds) {
        while (true) {
            now = ZonedDateTime.now()
            delay(if (needsSeconds) 1000L else 20_000L)
        }
    }
    val rendered = FormatEngine.render(settings.formatSpec, now, Locale.getDefault())
    val iconFactory = remember { IconFactory() }
    // The icon engine drops seconds, so preview exactly what it will draw.
    val iconDisplay = com.kabirbhasin.statuscalendar.core.format.IconDisplay.forSpec(
        settings.formatSpec, now, Locale.getDefault()
    )
    val bitmap = remember(iconDisplay) { iconFactory.iconFor(iconDisplay) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What you will actually see", style = MaterialTheme.typography.titleMedium)

            val iconOn = settings.displayMode != DisplayMode.FULL_TEXT
            Text(
                if (iconOn) "Compact (status icon)" else "Compact (status icon) — not selected",
                style = MaterialTheme.typography.labelLarge,
                color = if (iconOn) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Live representation of the compact icon",
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    "This is exactly what will appear next to your other status bar icons.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val iconWarning = when {
                !iconOn -> null
                iconDisplay.line.isEmpty() && iconDisplay.stackTop == null ->
                    "Nothing is selected to show."
                iconDisplay.stackTop != null ->
                    "There is only room for a few characters here, so the day of the week " +
                        "is shown above a large date number. Pick the draw over option if " +
                        "you want the whole thing written out."
                else -> null
            }
            iconWarning?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (settings.formatSpec.timeConfig.showSeconds && iconOn) {
                Text(
                    "Seconds are not shown here. Android only lets an icon change once a " +
                        "minute, so a ticking second hand is not possible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            val textOn = settings.displayMode != DisplayMode.COMPACT
            Text(
                if (textOn) "Draw over effect over the status bar" else "Draw over effect over the status bar — not selected",
                style = MaterialTheme.typography.labelLarge,
                color = if (textOn) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            val overlayText = if (rendered.stackTop != null) {
                listOf(rendered.stackTop, rendered.stackBottom, rendered.line.ifEmpty { null })
                    .filterNotNull().joinToString(" ")
            } else rendered.line.ifEmpty { "(nothing selected)" }
            Text(overlayText, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Your text is painted on top of the status bar, so it can be as long as you " +
                    "like and can tick every second. It is hidden on the lock screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeChoice(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
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
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
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
            val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels.toFloat()
            SliderRow("Horizontal", style.offsetX.toFloat(), 0f..(screenWidth - 120f)) {
                scope.launch { repository.setOverlayStyle(style.copy(offsetX = it.toInt())) }
            }
            SliderRow("Vertical", style.offsetY.toFloat(), 0f..200f) {
                scope.launch { repository.setOverlayStyle(style.copy(offsetY = it.toInt())) }
            }
            SliderRow("Text size", style.textSizeSp, 8f..24f) {
                scope.launch { repository.setOverlayStyle(style.copy(textSizeSp = it)) }
            }
            ToggleRow(
                "Hide in fullscreen",
                "Disappear while a video or game hides the status bar, so the text never " +
                    "floats over fullscreen content.",
                style.hideInFullscreen
            ) { v ->
                scope.launch { repository.setOverlayStyle(style.copy(hideInFullscreen = v)) }
            }

            HorizontalDivider()
            Text("Saved positions", style = MaterialTheme.typography.labelLarge)
            Text(
                "Keep a position and size you like, then bring it back on any phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.savedOverlayPresets.isNotEmpty()) {
                DropdownRow(
                    "Apply a saved position",
                    "Restores the horizontal, vertical and size settings.",
                    settings.savedOverlayPresets
                        .firstOrNull { it.style == style }?.name ?: "Choose",
                    settings.savedOverlayPresets.map { it.name }
                ) { index ->
                    scope.launch {
                        repository.setOverlayStyle(settings.savedOverlayPresets[index].style)
                    }
                }
            }
            var overlayName by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = overlayName,
                    onValueChange = { overlayName = it },
                    singleLine = true,
                    label = { Text("Position name") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = {
                    val name = overlayName.trim()
                    if (name.isNotEmpty()) {
                        scope.launch { repository.saveOverlayPreset(name, style) }
                        overlayName = ""
                    }
                }) { Text("Save") }
            }
            if (settings.savedOverlayPresets.isNotEmpty()) {
                DropdownRow(
                    "Delete a saved position",
                    "Removes one of your saved positions.",
                    "Choose",
                    settings.savedOverlayPresets.map { it.name }
                ) { index ->
                    scope.launch {
                        repository.deleteOverlayPreset(settings.savedOverlayPresets[index].name)
                    }
                }
            }
            TextButton(onClick = { scope.launch { repository.resetOverlayStyle() } }) {
                Text("Reset position and size to default")
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
    // Writing to storage on every drag frame caused visible stalls, so the value is
    // held locally while dragging and saved once when the finger lifts.
    var dragging by remember { mutableStateOf(false) }
    var local by remember(value) { mutableStateOf(value) }
    val shown = if (dragging) local else value
    Column {
        Text("$label: ${shown.toInt()}", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = shown,
            onValueChange = { dragging = true; local = it },
            onValueChangeFinished = { dragging = false; onChange(local) },
            valueRange = range
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
