package com.posprint.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Usb
import com.posprint.app.ui.common.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.ui.components.ConnectionChip
import com.posprint.app.ui.components.IconBadge
import com.posprint.app.ui.components.JobRow

@Composable
fun HomeScreen(
    onAddPrinter: () -> Unit,
    onQuickPrint: () -> Unit,
    onNewLabel: () -> Unit,
    onScan: () -> Unit,
    onMiniPos: () -> Unit,
    onPrintFile: () -> Unit,
    onHistory: () -> Unit = {},
    onPrinters: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                Text(
                    state.business.ifBlank { "POS Print" },
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.printer == null) "Let's connect your first printer" else "Ready when you are",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            val printer = state.printer
            if (printer == null) NoPrinterCard(onAddPrinter)
            else PrinterCard(printer, state.connection, onTest = { vm.testPrint(printer) }, onConnect = { vm.connect(printer) }, onManage = onPrinters)
        }
        item { SectionHeader("Quick actions") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigAction("Quick print", "Type and print text", Icons.AutoMirrored.Rounded.ShortText, onQuickPrint, Modifier.weight(1f))
                    BigAction("Mini POS", "Ring up a sale", Icons.Rounded.PointOfSale, onMiniPos, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallAction("New label", Icons.Rounded.Sell, onNewLabel, Modifier.weight(1f))
                    SmallAction("Scan", Icons.Rounded.QrCodeScanner, onScan, Modifier.weight(1f))
                    SmallAction("Image / PDF", Icons.Rounded.Image, onPrintFile, Modifier.weight(1f))
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader("Recent prints", Modifier.weight(1f))
                TextButton(onHistory) { Text("See all") }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                if (state.jobs.isEmpty()) {
                    Text(
                        if (state.printer == null) "Prints you send will appear here." else "Nothing printed yet. Tap Test print to check your printer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        state.jobs.forEachIndexed { i, job ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            JobRow(job, onRetry = { vm.retry(job.id) }, onCancel = { vm.cancel(job.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.titleMedium)

@Composable
private fun NoPrinterCard(onAddPrinter: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(20.dp)) {
            IconBadge(Icons.Rounded.Print, container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary, size = 48.dp)
            Spacer(Modifier.height(16.dp))
            Text("No printer yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Connect a Bluetooth or USB printer. It takes about a minute.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onAddPrinter, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add printer")
            }
        }
    }
}

@Composable
private fun PrinterCard(printer: Printer, connection: ConnectionState, onTest: () -> Unit, onConnect: () -> Unit, onManage: () -> Unit) {
    Card(
        onClick = onManage,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    if (printer.connection == ConnectionType.BLUETOOTH) Icons.Rounded.Bluetooth else Icons.Rounded.Usb,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(printer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "Default printer · ${printer.connection.label} · " + if (printer.language.isLabel) printer.language.name else "${printer.paperWidth.mm} mm",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Manage printers", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            ConnectionChip(connection)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onTest, Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Rounded.Print, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Test print")
                }
                if (connection != ConnectionState.CONNECTED) {
                    OutlinedButton(onConnect, Modifier.weight(1f).height(48.dp), enabled = connection != ConnectionState.CONNECTING) {
                        Text(if (connection == ConnectionState.ERROR) "Try again" else "Connect")
                    }
                }
            }
        }
    }
}

@Composable
private fun BigAction(label: String, sub: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick, modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 96.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, Modifier.size(28.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick, modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
