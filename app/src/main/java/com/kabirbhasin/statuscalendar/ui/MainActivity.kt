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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import com.kabirbhasin.statuscalendar.core.format.FormatEngine
import com.kabirbhasin.statuscalendar.core.format.Presets
import com.kabirbhasin.statuscalendar.core.prefs.AppSettings
import com.kabirbhasin.statuscalendar.core.prefs.SettingsRepository
import com.kabirbhasin.statuscalendar.service.DisplayService
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
            MaterialTheme {
                SettingsScreen(repository)
            }
        }
    }
}

@Composable
private fun SettingsScreen(repository: SettingsRepository) {
    val settings by repository.flow.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = settings ?: return

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Status Calendar", style = MaterialTheme.typography.headlineMedium)

            PreviewCard(current)

            ToggleRow(
                label = "Show in status bar",
                checked = current.displayEnabled
            ) { enabled ->
                scope.launch {
                    repository.setDisplayEnabled(enabled)
                    if (enabled) DisplayService.start(context) else DisplayService.stop(context)
                }
            }

            ToggleRow(
                label = "Notification icon engine",
                checked = current.notificationEngineEnabled
            ) { scope.launch { repository.setNotificationEngine(it) } }

            ToggleRow(
                label = "Start after reboot",
                checked = current.startOnBoot
            ) { scope.launch { repository.setStartOnBoot(it) } }

            HorizontalDivider()

            Text("Format", style = MaterialTheme.typography.titleMedium)
            Presets.all.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = current.formatSpec == preset.spec,
                            onClick = { scope.launch { repository.setFormatSpec(preset.spec) } }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = current.formatSpec == preset.spec,
                        onClick = { scope.launch { repository.setFormatSpec(preset.spec) } }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(preset.label)
                }
            }

            HorizontalDivider()

            BatteryCard()
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
                "${rendered.stackTop}\n${rendered.stackBottom}"
            } else rendered.line
            Text(text, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun BatteryCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Reliability", style = MaterialTheme.typography.titleMedium)
            Text(
                "To keep the display alive on aggressive devices, exempt this app " +
                    "from battery optimisation and allow background activity.",
                style = MaterialTheme.typography.bodyMedium
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            TextButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                )
            }) { Text("Open battery settings") }
        }
    }
}
