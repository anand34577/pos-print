package com.posprint.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
import com.posprint.app.ui.common.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.printer.CommandLog
import com.posprint.app.data.printer.PrintService
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Parses "1B 40", "0x1B,0x40" or "1b40" into bytes. Null if malformed. */
fun parseHex(input: String): ByteArray? {
    val cleaned = input.replace(Regex("0x|[,\\s]", RegexOption.IGNORE_CASE), "")
    if (cleaned.isEmpty() || cleaned.length % 2 != 0 || !cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return ByteArray(cleaned.length / 2) { cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

fun toHex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

@HiltViewModel
class DeveloperViewModel @Inject constructor(
    printerRepo: PrinterRepository,
    private val print: PrintService,
    val log: CommandLog,
) : ViewModel() {
    val printers = printerRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    fun send(printerId: Long?, bytes: ByteArray) {
        val p = printers.value.firstOrNull { it.id == printerId } ?: printers.value.firstOrNull { it.isDefault } ?: printers.value.firstOrNull()
        if (p == null) { _status.value = "Add a printer first."; return }
        viewModelScope.launch {
            _status.value = try { print.sendRaw(p, bytes); "Sent ${bytes.size} byte(s) to ${p.name}" } catch (e: PrinterException) { e.error.message }
        }
    }

    fun checkStatus(printerId: Long?) {
        val p = printers.value.firstOrNull { it.id == printerId } ?: printers.value.firstOrNull() ?: return
        viewModelScope.launch {
            _status.update { null }
            _status.value = try {
                val s = print.checkStatus(p)
                if (s == null) "No status reply (normal for many printers)" else "cover open=${s.coverOpen}, paper out=${s.paperOut}, near end=${s.paperNearEnd}, error=${s.errorFlag}"
            } catch (e: PrinterException) { e.error.message }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeveloperScreen(onBack: () -> Unit, vm: DeveloperViewModel = hiltViewModel()) {
    val printers by vm.printers.collectAsStateWithLifecycle()
    val entries by vm.log.entries.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Long?>(null) }
    var hex by remember { mutableStateOf("1B 40") }
    var text by remember { mutableStateOf("") }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Scaffold(topBar = { TopAppBar(title = { Text("Developer tools") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (printers.isNotEmpty()) Dropdown("Printer", printers, printers.firstOrNull { it.id == selected } ?: printers.firstOrNull { it.isDefault } ?: printers.first(), { it.name }) { selected = it.id }
                else Text("Add a printer first.")
            }
            item {
                SectionTitle("Raw hex")
                Field("Hex bytes", hex, { hex = it }, hint = "e.g. 1B 40 or 0x1D,0x56,0x42,0x03", singleLine = false)
                val bytes = parseHex(hex)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ bytes?.let { vm.send(selected, it) } }, enabled = bytes != null && printers.isNotEmpty()) { Text("Send hex") }
                    TextButton({ vm.checkStatus(selected) }, enabled = printers.isNotEmpty()) { Text("Query status") }
                }
                if (bytes == null && hex.isNotBlank()) Text("Not valid hex", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Field("Text (sent as-is, then a line feed)", text, { text = it })
                Button({ vm.send(selected, (text + "\n").toByteArray()) }, enabled = text.isNotEmpty() && printers.isNotEmpty()) { Text("Send text") }
            }
            item {
                SectionTitle("Presets")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Initialise" to "1B 40", "Feed 3" to "1B 64 03", "Partial cut" to "1D 56 42 03", "Drawer kick" to "1B 70 00 19 FA", "Beep" to "1B 42 02 03", "Self-test (Epson)" to "1D 28 41 02 00 00 02")
                        .forEach { (label, h) -> AssistChip({ hex = h }, { Text(label) }) }
                }
                status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    SectionTitle("Command log", Modifier.weight(1f))
                    TextButton({ vm.log.clear() }) { Text("Clear") }
                }
                if (entries.isEmpty()) Text("Nothing sent yet.", style = MaterialTheme.typography.bodySmall)
            }
            items(entries.asReversed()) { e ->
                Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("${time.format(Date(e.time))}  ${if (e.outgoing) "TX →" else "RX ←"}  ${e.printer}  (${e.bytes.size}${if (e.bytes.size == 512) "+" else ""} B)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(toHex(e.bytes.copyOf(minOf(e.bytes.size, 96))) + if (e.bytes.size > 96) " …" else "", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
