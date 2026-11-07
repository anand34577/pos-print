package com.posprint.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.db.HistoryDao
import com.posprint.app.data.db.HistoryEntity
import com.posprint.app.data.printer.PrintService
import com.posprint.app.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val dao: HistoryDao,
    private val printers: PrinterRepository,
    private val print: PrintService,
) : ViewModel() {
    val items: StateFlow<List<HistoryEntity>?> = dao.observeRecent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun reprint(e: HistoryEntity) {
        viewModelScope.launch {
            val full = dao.get(e.id)
            val data = full?.payload
            if (data == null) { _messages.send("This print was too large to keep for reprinting."); return@launch }
            val printer = printers.get(e.printerId) ?: printers.observeDefault().first()
            if (printer == null) { _messages.send("Add a printer first."); return@launch }
            print.enqueue(printer, e.title, data)
            _messages.send("Sent to ${printer.name}")
        }
    }

    fun delete(e: HistoryEntity) { viewModelScope.launch { dao.delete(e.id) } }
    fun clear() { viewModelScope.launch { dao.clear() } }
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = hiltViewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val list = items
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton({ confirmClear = true }, enabled = !list.isNullOrEmpty()) { Text("Clear") }
            }
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Rounded.History, "No prints yet", "Everything you print shows up here so you can reprint it.")
                else -> LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (e.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    if (e.success) "Printed" else "Failed",
                                    tint = if (e.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text("${e.printerName} · ${fmt.format(Date(e.createdAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!e.success && e.error != null) Text(e.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                                IconButton({ vm.reprint(e) }) { Icon(Icons.Rounded.Replay, "Reprint ${e.title}") }
                                IconButton({ vm.delete(e) }) { Icon(Icons.Rounded.Delete, "Delete ${e.title} from history") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all history?") },
            text = { Text("Saved prints can no longer be reprinted.") },
            confirmButton = { TextButton({ vm.clear(); confirmClear = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
        )
    }
}
