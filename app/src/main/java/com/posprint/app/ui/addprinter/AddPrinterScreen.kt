package com.posprint.app.ui.addprinter

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Usb
import com.posprint.app.ui.common.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.DiscoveredDevice
import com.posprint.app.domain.model.JobStatus
import com.posprint.app.domain.model.PaperWidth
import com.posprint.app.ui.components.JobRow
import com.posprint.app.util.bluetoothPermissions
import com.posprint.app.util.hasPermissions

/** Three steps: pick connection type, pick the device, test print. Used for onboarding and "Add printer". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPrinterScreen(onClose: () -> Unit, onDone: () -> Unit, onboarding: Boolean = false, vm: AddPrinterViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(enabled = ui.step != WizardStep.TYPE) { vm.back() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) vm.chooseType(ConnectionType.BLUETOOTH)
        else vm.showError("Bluetooth permission is needed to find your printer. Allow \"Nearby devices\" in app settings.")
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.startScan() }
    // During onboarding, Skip must be remembered; otherwise the wizard came back on every launch.
    val close = { if (onboarding) vm.finish(onClose) else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onboarding) "Set up your printer" else "Add printer") },
                navigationIcon = {
                    IconButton({ if (!vm.back()) close() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                actions = { TextButton(close) { Text(if (onboarding) "Skip" else "Cancel") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { (ui.step.ordinal + 1) / 3f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Step ${ui.step.ordinal + 1} of 3",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(ui.step, label = "wizard") { step ->
                when (step) {
                    WizardStep.TYPE -> TypeStep(
                        error = ui.error,
                        onBluetooth = {
                            val perms = bluetoothPermissions()
                            if (context.hasPermissions(perms)) vm.chooseType(ConnectionType.BLUETOOTH)
                            else permissionLauncher.launch(perms)
                        },
                        onUsb = { vm.chooseType(ConnectionType.USB) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                    WizardStep.DEVICE -> DeviceStep(
                        ui = ui,
                        onPick = vm::pick,
                        onRescan = vm::startScan,
                        onEnableBluetooth = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    )
                    WizardStep.TEST -> TestStep(
                        ui = ui,
                        onPaper = vm::setPaper,
                        onCutter = vm::setCutter,
                        onLanguage = vm::setLanguage,
                        onTest = vm::testPrint,
                        onRetry = vm::retryTest,
                        onDone = { vm.finish(onDone) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeStep(error: String?, onBluetooth: () -> Unit, onUsb: () -> Unit, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How is your printer connected?", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Switch the printer on first. Bluetooth printers need to be nearby; USB printers connect with an OTG cable.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TypeCard("Bluetooth", "Wireless, most portable receipt printers", Icons.Rounded.Bluetooth, onBluetooth)
        TypeCard("USB", "Plug in with a USB cable or OTG adapter", Icons.Rounded.Usb, onUsb)
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onOpenSettings) { Text("Open app settings") }
        }
    }
}

@Composable
private fun TypeCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            com.posprint.app.ui.components.IconBadge(icon, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceStep(
    ui: AddPrinterUiState,
    onPick: (DiscoveredDevice) -> Unit,
    onRescan: () -> Unit,
    onEnableBluetooth: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ui.type == ConnectionType.USB) "Select your USB printer" else "Select your printer",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            if (ui.scanning) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
            else IconButton(onRescan) { Icon(Icons.Rounded.Refresh, "Scan again") }
        }
        Spacer(Modifier.height(8.dp))
        if (ui.needsBluetoothEnable) {
            Text("Bluetooth is off.", style = MaterialTheme.typography.bodyLarge)
            Button(onEnableBluetooth, Modifier.padding(top = 8.dp)) { Text("Turn on Bluetooth") }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
        if (ui.devices.isEmpty() && !ui.needsBluetoothEnable) {
            Text(
                if (ui.type == ConnectionType.USB) "No USB devices found. Plug the printer in with an OTG cable, then tap refresh."
                else if (ui.scanning) "Looking for printers…"
                else "No devices found. Make sure the printer is on and in pairing mode, then scan again." +
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) " On this Android version Location must also be turned on to find Bluetooth devices." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            items(ui.devices, key = { it.address }) { d ->
                Card(
                    onClick = { onPick(d) },
                    enabled = ui.busyAddress == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.posprint.app.ui.components.IconBadge(
                            Icons.Rounded.Print,
                            container = if (d.likelyPrinter) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                            content = if (d.likelyPrinter) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                buildString {
                                    append(if (d.connection == ConnectionType.USB) "USB" else d.address)
                                    if (d.connection == ConnectionType.BLUETOOTH) append(if (d.paired) " · paired" else " · not paired")
                                    if (d.likelyPrinter) append(" · looks like a printer")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (ui.busyAddress == d.address) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TestStep(
    ui: AddPrinterUiState,
    onPaper: (PaperWidth) -> Unit,
    onCutter: (Boolean) -> Unit,
    onLanguage: (com.posprint.app.domain.model.PrinterLanguage) -> Unit,
    onTest: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
) {
    val printer = ui.printer ?: return
    val job = ui.testJob
    Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Test ${printer.name}", style = MaterialTheme.typography.headlineSmall)
        com.posprint.app.ui.common.Dropdown("Printer type", com.posprint.app.domain.model.PrinterLanguage.entries, printer.language, { it.label }, onLanguage)
        if (!printer.language.isLabel) {
            com.posprint.app.ui.common.ChipRow("Paper width", PaperWidth.entries, printer.paperWidth, { "${it.mm} mm" }, onPaper)
            com.posprint.app.ui.common.SwitchRow("Auto-cutter", printer.hasCutter, onChange = onCutter)
            com.posprint.app.ui.common.Hint("On the printed ruler the last digit should reach the right edge without wrapping. If not, change the paper width.")
        } else {
            com.posprint.app.ui.common.Hint("A small test label is printed. Load labels at least 50 × 30 mm.")
        }
        if (job == null) {
            Button(onTest, Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Rounded.Print, null)
                Spacer(Modifier.width(8.dp))
                Text("Print test page")
            }
        } else {
            JobRow(job, onRetry = onRetry, onCancel = {})
            if (job.status == JobStatus.DONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Did the page print correctly?", style = MaterialTheme.typography.bodyLarge)
                }
                Button(onDone, Modifier.fillMaxWidth().height(56.dp)) { Text("Yes, finish setup") }
                OutlinedButton(onTest, Modifier.fillMaxWidth().height(48.dp)) { Text("Print again") }
            }
        }
        if (job == null || job.status != JobStatus.DONE) {
            TextButton(onDone, Modifier.fillMaxWidth()) { Text("Finish without testing") }
        }
    }
}
