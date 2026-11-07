package com.posprint.app.ui.templates

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.Hint
import com.posprint.app.ui.common.ItemsEditor
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.isWideScreen
import com.posprint.app.ui.preview.ReceiptPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateEditorScreen(onBack: () -> Unit, onPrint: (Template) -> Unit, vm: TemplateEditorViewModel = hiltViewModel()) {
    val template by vm.template.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }

    val exit = { vm.discardIfFresh(); onBack() }
    val leave = { if (dirty) confirmLeave = true else exit() }
    BackHandler(onBack = leave)

    val t = template
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(t?.name ?: "Template", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    TextButton({ vm.save() }, enabled = dirty) { Text("Save") }
                    IconButton({ vm.save(onPrint) }, enabled = t != null) { Icon(Icons.Rounded.Print, "Save and print") }
                },
            )
        },
    ) { padding ->
        if (t == null) return@Scaffold
        val editor: @Composable () -> Unit = { EditorPane(t, printers, vm) }
        val previewPane: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                ReceiptPreview(preview.blocks, preview.cols, vm.assets)
            }
        }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight()) { editor() }
                Column(Modifier.weight(1f).fillMaxHeight()) { previewPane() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                PrimaryTabRow(tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Edit") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Preview") })
                }
                if (tab == 0) editor() else previewPane()
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved changes to this template.") },
            confirmButton = { TextButton({ confirmLeave = false; vm.save { onBack() } }) { Text("Save") } },
            dismissButton = {
                Row {
                    TextButton({ confirmLeave = false; exit() }) { Text("Discard") }
                    TextButton({ confirmLeave = false }) { Text("Keep editing") }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorPane(t: Template, printers: List<Printer>, vm: TemplateEditorViewModel) {
    var editing by remember { mutableStateOf<Int?>(null) }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Field("Template name", t.name, { v -> vm.edit { it.copy(name = v) } }) }
        if (printers.size > 1) item {
            val none = Printer(id = -1, name = "Default printer", connection = com.posprint.app.domain.model.ConnectionType.BLUETOOTH, address = "")
            Dropdown("Printer for this template", listOf(none) + printers, printers.firstOrNull { it.id == t.printerId } ?: none, { it.name }) { p ->
                vm.edit { it.copy(printerId = if (p.id == -1L) null else p.id) }
            }
        }
        if (t.kind == TemplateKind.DOCUMENT) item {
            Dropdown("Paper", listOf("A4", "A5", "LETTER"), t.paper, { if (it == "LETTER") "Letter" else it }) { p -> vm.edit { it.copy(paper = p) } }
        }
        item {
            SectionTitle("Blocks")
            Hint("Tap a block to edit it. Blocks print from top to bottom.")
        }
        if (t.blocks.isEmpty()) item { Hint("No blocks yet. Add one below.", Modifier.padding(vertical = 8.dp)) }
        itemsIndexed(t.blocks) { i, b ->
            Card(
                onClick = { editing = i },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(blockTitle(b), style = MaterialTheme.typography.titleSmall)
                        Text(blockSummary(b), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Rounded.ArrowUpward, "Move ${blockTitle(b)} up") }
                    IconButton({ vm.move(i, 1) }, enabled = i < t.blocks.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move ${blockTitle(b)} down") }
                    IconButton({ vm.remove(i) }) { Icon(Icons.Rounded.Delete, "Delete ${blockTitle(b)}") }
                }
            }
        }
        item {
            SectionTitle("Add block")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                newBlockChoices.forEach { (label, make) ->
                    AssistChip(
                        { vm.add(make()) }, { Text(label) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }
        item {
            SectionTitle("Preview data")
            Hint("Sample values used in the preview and as defaults when printing.")
        }
        val vars = TemplateEngine.variablesUsed(t.blocks)
        val listSources = t.blocks.filterIsInstance<TableBlock>().associate { it.source to it.columns.map { c -> c.field } }
        items(vars.filterNot { it in listSources }) { name ->
            Field(com.posprint.app.ui.common.humanize(name), t.sample.vars[name].orEmpty(), { v -> vm.setVar(name, v) })
        }
        items(listSources.entries.toList()) { (source, fields) ->
            ItemsEditor(source, fields, t.sample.lists[source].orEmpty()) { rows -> vm.setList(source, rows) }
        }
        item { Spacer(Modifier.width(1.dp)) }
    }

    editing?.let { i ->
        t.blocks.getOrNull(i)?.let { b ->
            BlockEditorDialog(b, { vm.replace(i, it); editing = null }, { editing = null }, vm::pickImage)
        }
    }
}
