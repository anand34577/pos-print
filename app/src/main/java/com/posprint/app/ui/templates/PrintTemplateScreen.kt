package com.posprint.app.ui.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Print
import com.posprint.app.ui.common.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.isWideScreen
import com.posprint.app.ui.components.JobRow
import com.posprint.app.ui.preview.ReceiptPreview

/** Fill in a receipt template's variables, see it, print it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintTemplateScreen(onBack: () -> Unit, vm: PrintTemplateViewModel = hiltViewModel()) {
    val t by vm.template.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val calculated by vm.calculated.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val template = t

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(template?.name ?: "Print") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
    }) { padding ->
        if (template == null) return@Scaffold

        val form: @Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Only ask which printer when there is a real choice to make.
                if (printers.size > 1) {
                    val none = Printer(id = -1, name = "Usual printer", connection = ConnectionType.BLUETOOTH, address = "")
                    Dropdown("Print on", listOf(none) + printers, printers.firstOrNull { it.id == vm.chosenPrinterId } ?: none, { it.name }) {
                        vm.choosePrinter(if (it.id == -1L) null else it.id)
                    }
                }
                val listSources = template.blocks.filterIsInstance<TableBlock>().associate { it.source to it.columns.map { c -> c.field } }
                val vars = TemplateEngine.variablesUsed(template.blocks).filterNot { it in listSources }
                listSources.forEach { (source, fields) ->
                    com.posprint.app.ui.common.ItemsEditor(source, fields, data.lists[source].orEmpty()) { vm.setList(source, it) }
                }
                val typed = vars.filterNot { it in calculated }
                if (typed.isNotEmpty()) SectionTitle("Details")
                typed.forEach { name -> Field(com.posprint.app.ui.common.humanize(name), data.vars[name].orEmpty(), { vm.setVar(name, it) }) }
                com.posprint.app.ui.common.CalculatedSummary(vars.filter { it in calculated }.map { it to data.vars[it].orEmpty() })
                Button({ vm.print() }, Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(Icons.Rounded.Print, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("Print")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                job?.let { JobRow(it, onRetry = vm::retry, onCancel = vm::cancel) }
            }
        }
        val previewPane: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ReceiptPreview(preview.blocks, preview.cols, vm.assets)
            }
        }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight()) { form() }
                Column(Modifier.weight(1f).fillMaxHeight()) { previewPane() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                androidx.compose.material3.PrimaryTabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Details") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Preview") })
                }
                if (tab == 0) form() else previewPane()
            }
        }
    }
}
