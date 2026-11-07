package com.posprint.app.ui.printers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import com.posprint.app.ui.common.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionState
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.ui.components.ConnectionChip
import com.posprint.app.ui.components.EmptyState
import com.posprint.app.ui.components.JobRow

@Composable
fun PrintersScreen(onAddPrinter: () -> Unit, vm: PrintersViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<Printer?>(null) }
    var editing by remember { mutableStateOf<Printer?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            // The empty state has its own Add button; two identical calls to action would compete.
            if (state.printers.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = onAddPrinter, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add printer") })
            }
        },
    ) { padding ->
        if (state.printers.isEmpty() && !state.loading) {
            EmptyState(
                Icons.Rounded.Print, "No printers saved",
                "Add a Bluetooth or USB thermal printer. You can save several and pick a default.",
                Modifier.padding(padding),
            ) { Button(onAddPrinter) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add printer") } }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Text("Printers", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 4.dp)) }
                items(state.printers, key = { it.id }) { printer ->
                    PrinterCard(
                        printer = printer,
                        connection = state.connections[printer.id] ?: ConnectionState.DISCONNECTED,
                        onConnect = { vm.connect(printer) },
                        onDisconnect = { vm.disconnect(printer) },
                        onTest = { vm.testPrint(printer) },
                        onSelfTest = { vm.selfTest(printer) },
                        onMakeDefault = { vm.setDefault(printer) },
                        onEditProfile = { editing = printer },
                        onDelete = { deleting = printer },
                    )
                }
                val recent = state.jobs.take(5)
                if (recent.isNotEmpty()) {
                    item { Text("Print queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(recent, key = { "job${it.id}" }) { job ->
                        JobRow(job, onRetry = { vm.retry(job.id) }, onCancel = { vm.cancel(job.id) })
                    }
                }
            }
        }
    }

    editing?.let { p -> PrinterProfileDialog(p, { vm.update(it); editing = null }, { editing = null }) }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("The printer is only removed from this app. You can add it again any time.") },
            confirmButton = { TextButton({ vm.delete(p); deleting = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PrinterCard(
    printer: Printer,
    connection: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onTest: () -> Unit,
    onSelfTest: () -> Unit,
    onMakeDefault: () -> Unit,
    onEditProfile: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.posprint.app.ui.components.IconBadge(
                    if (printer.connection == ConnectionType.BLUETOOTH) Icons.Rounded.Bluetooth else Icons.Rounded.Usb,
                    container = if (printer.isDefault) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    content = if (printer.isDefault) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(printer.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (printer.isDefault) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Star, "Default printer", Modifier.padding(top = 1.dp).width(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text(
                        "${printer.connection.label} · ${printer.language.label.substringBefore(" (")}" +
                            (if (printer.language.isLabel) "" else " · ${printer.paperWidth.mm} mm") + if (printer.hasCutter) " · cutter" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${printer.name}") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Edit profile") }, { menu = false; onEditProfile() })
                        if (!printer.isDefault) DropdownMenuItem({ Text("Make default") }, { menu = false; onMakeDefault() })
                        DropdownMenuItem({ Text("Printer self-test") }, { menu = false; onSelfTest() })
                        if (connection == ConnectionState.CONNECTED) DropdownMenuItem({ Text("Disconnect") }, { menu = false; onDisconnect() })
                        DropdownMenuItem({ Text("Remove", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ConnectionChip(connection)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onTest, Modifier.weight(1f).height(48.dp)) { Icon(Icons.Rounded.Print, null, Modifier.width(18.dp)); Spacer(Modifier.width(8.dp)); Text("Test print") }
                OutlinedButton(
                    onConnect,
                    Modifier.weight(1f).height(48.dp),
                    enabled = connection != ConnectionState.CONNECTING,
                ) { Text(if (connection == ConnectionState.CONNECTED) "Check status" else "Connect") }
            }
        }
    }
}
