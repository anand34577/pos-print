package com.posprint.app.ui.labels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Print
import com.posprint.app.ui.common.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.common.SwitchRow
import com.posprint.app.ui.common.isWideScreen
import com.posprint.app.ui.components.JobRow
import kotlin.math.roundToInt

/** Single label or a CSV/Excel batch: variables, copies, serial numbers, live preview. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrintScreen(onBack: () -> Unit, vm: LabelPrintViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val vars by vm.variables.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val t = s.template

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.import(uri) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(t?.name ?: "Print label", maxLines = 1) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
    }) { padding ->
        if (t == null) return@Scaffold
        val previewPane: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preview" + if (s.batch) " (row ${s.previewRow + 1} of ${vm.rowCount(s)})" else "", style = MaterialTheme.typography.titleSmall)
                preview?.let { img ->
                    Canvas(
                        Modifier.fillMaxWidth().aspectRatio((t.label.widthMm / t.label.heightMm).toFloat().coerceIn(0.2f, 5f))
                            .background(Color.White).border(1.dp, Color(0x33000000)),
                    ) {
                        drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.None)
                    }
                }
                if (s.batch && vm.rowCount(s) > 1) Stepper("Preview row", s.previewRow + 1, 1..vm.rowCount(s)) { r -> vm.update { it.copy(previewRow = r - 1) } }
            }
        }
        val form: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (printers.size > 1) {
                    val none = Printer(id = -1, name = "Usual printer", connection = ConnectionType.BLUETOOTH, address = "")
                    Dropdown("Print on", listOf(none) + printers, printers.firstOrNull { it.id == s.printerId } ?: none, { it.name }) { p ->
                        vm.update { it.copy(printerId = if (p.id == -1L) null else p.id) }
                    }
                }
                ChipRow("Source", listOf(false, true), s.batch, { if (it) "CSV / Excel batch" else "Single label" }) { b -> vm.update { it.copy(batch = b) } }

                if (!s.batch) {
                    vars.forEach { v -> Field(com.posprint.app.ui.common.humanize(v), s.vars[v].orEmpty(), { vm.setVar(v, it) }) }
                } else {
                    OutlinedButton({ picker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain", "*/*")) }, Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Rounded.FileOpen, null); Text("  Choose .csv or .xlsx file")
                    }
                    s.table?.let { table ->
                        Text("${table.rows.size} rows · columns: ${table.headers.joinToString()}", style = MaterialTheme.typography.bodySmall)
                        SectionTitle("Match fields to columns")
                        vars.forEach { v ->
                            val label = com.posprint.app.ui.common.humanize(v)
                            Dropdown("$label comes from", listOf("") + table.headers, s.mapping[v].orEmpty(), { if (it.isEmpty()) "Same on every label" else "Column: $it" }) { vm.map(v, it) }
                            if (s.mapping[v].isNullOrEmpty()) Field("$label (same on every label)", s.vars[v].orEmpty(), { vm.setVar(v, it) })
                        }
                        Text("A column named qty, quantity or copies sets the number of labels for each row.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Stepper("Copies of each label", s.copies, 1..999) { c -> vm.update { it.copy(copies = c) } }
                SwitchRow("Auto-increment {{serial}}", s.serialOn) { on -> vm.update { it.copy(serialOn = on) } }
                if (s.serialOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Start", s.serialStart, { v -> vm.update { it.copy(serialStart = v.filter(Char::isDigit)) } }, Modifier.weight(1f), number = true)
                        Field("Step", s.serialStep, { v -> vm.update { it.copy(serialStep = v.filter(Char::isDigit)) } }, Modifier.weight(1f), number = true)
                    }
                    Field("Prefix (optional)", s.serialPrefix, { v -> vm.update { it.copy(serialPrefix = v) } })
                    Stepper("Zero-pad to", s.serialPad, 0..10) { p -> vm.update { it.copy(serialPad = p) } }
                    Text("Add {{serial}} to a text or barcode on the label.", style = MaterialTheme.typography.bodySmall)
                }

                Button({ vm.print() }, Modifier.fillMaxWidth().height(56.dp), enabled = !s.batch || s.table != null) {
                    Icon(Icons.Rounded.Print, null); Text("  Print ${vm.totalLabels(s)} label(s)")
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                job?.let { JobRow(it, onRetry = vm::retry, onCancel = vm::cancel) }
            }
        }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { form() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { previewPane() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { previewPane(); form() }
        }
    }
}
