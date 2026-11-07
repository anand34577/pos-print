package com.posprint.app.ui.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import com.posprint.app.ui.common.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.ui.components.EmptyState
import com.posprint.app.ui.components.IconBadge

@Composable
fun TemplatesScreen(
    onEdit: (Template) -> Unit,
    onPrint: (Template) -> Unit,
    onCreated: (Template) -> Unit = onEdit,
    vm: TemplatesViewModel = hiltViewModel(),
) {
    val all by vm.templates.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var filter by remember { mutableStateOf<TemplateKind?>(null) }
    var newDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Template?>(null) }
    var deleting by remember { mutableStateOf<Template?>(null) }
    var exporting by remember { mutableStateOf<Template?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            if (json != null) vm.import(json, onEdit) else vm.say("Could not read that file.")
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val t = exporting
        if (uri != null && t != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(vm.export(t).toByteArray()) } }.isSuccess
            vm.say(if (ok) "Exported ${t.name}" else "Could not save the file.")
        }
        exporting = null
    }
    LaunchedEffect(exporting) { exporting?.let { exporter.launch("${it.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "template" }}.posprint.json") } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { newDialog = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("New template") })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Templates", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                TextButton({ importer.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                    Icon(Icons.Rounded.FileOpen, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KindChip("All", filter == null) { filter = null }
                TemplateKind.entries.forEach { k -> KindChip(kindLabel(k), filter == k) { filter = k } }
            }
            val shown = all?.filter { filter == null || it.kind == filter }
            when {
                shown == null -> Unit
                shown.isEmpty() -> EmptyState(
                    Icons.Rounded.Description,
                    if (filter == null) "No templates" else "No ${kindLabel(filter!!).lowercase()} templates",
                    "Create one with New template, or import a shared template file.",
                )
                else -> {
                    val mine = shown.filterNot { it.builtIn }
                    val starters = shown.filter { it.builtIn }
                    val card: @Composable (Template) -> Unit = { t ->
                        TemplateCard(
                            t, onEdit = { onEdit(t) }, onPrint = { onPrint(t) }, onDuplicate = { vm.duplicate(t) },
                            onRename = { renaming = t }, onExport = { exporting = t }, onDelete = { deleting = t },
                        )
                    }
                    LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 104.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mine.isNotEmpty()) {
                            item(key = "h-mine") { ListHeader("Your templates") }
                            items(mine, key = { it.id }) { t -> card(t) }
                        }
                        if (starters.isNotEmpty()) {
                            item(key = "h-starters") { ListHeader("Starter templates", top = mine.isNotEmpty()) }
                            items(starters, key = { it.id }) { t -> card(t) }
                        }
                    }
                }
            }
        }
    }

    if (newDialog) {
        AlertDialog(
            onDismissRequest = { newDialog = false },
            title = { Text("New template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateKind.entries.forEach { k ->
                        Card(
                            onClick = { newDialog = false; vm.create(k, onCreated) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(kindIcon(k), size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(kindLabel(k), style = MaterialTheme.typography.titleSmall)
                                    Text(kindDescription(k), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ newDialog = false }) { Text("Cancel") } },
        )
    }
    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename template") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton({ vm.rename(t, name); renaming = null }, enabled = name.isNotBlank()) { Text("Save") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { t ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${t.name}?") },
            text = { Text("This cannot be undone. Export it first if you may need it again.") },
            confirmButton = { TextButton({ vm.delete(t); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) = FilterChip(
    selected, onClick, label = { Text(label) },
    leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }) else null,
)

@Composable
private fun ListHeader(text: String, top: Boolean = false) = Text(
    text, Modifier.padding(top = if (top) 16.dp else 4.dp, bottom = 4.dp),
    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
)

fun kindLabel(k: TemplateKind) = when (k) { TemplateKind.RECEIPT -> "Receipt"; TemplateKind.LABEL -> "Label"; TemplateKind.DOCUMENT -> "Document" }
fun kindIcon(k: TemplateKind) = when (k) { TemplateKind.RECEIPT -> Icons.Rounded.Receipt; TemplateKind.LABEL -> Icons.Rounded.Sell; TemplateKind.DOCUMENT -> Icons.Rounded.Description }
private fun kindDescription(k: TemplateKind) = when (k) {
    TemplateKind.RECEIPT -> "58 / 80 mm thermal receipts and tickets"
    TemplateKind.LABEL -> "Stickers, price tags, shipping labels"
    TemplateKind.DOCUMENT -> "A4, A5 or Letter on an office printer"
}

@Composable
fun TemplateCard(
    t: Template, onEdit: () -> Unit, onPrint: () -> Unit, onDuplicate: () -> Unit,
    onRename: () -> Unit, onExport: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(kindIcon(t.kind), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(kindLabel(t.kind))
                        if (t.kind == TemplateKind.LABEL) append(" · ${t.label.widthMm.trim()} × ${t.label.heightMm.trim()} mm")
                        if (t.kind == TemplateKind.DOCUMENT) append(" · ${t.paper}")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(onPrint, contentPadding = PaddingValues(horizontal = 16.dp)) { Text("Print") }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "More options for ${t.name}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Edit") }, { menu = false; onEdit() })
                    DropdownMenuItem({ Text("Duplicate") }, { menu = false; onDuplicate() })
                    DropdownMenuItem({ Text("Rename") }, { menu = false; onRename() })
                    DropdownMenuItem({ Text("Export (JSON file)") }, { menu = false; onExport() })
                    DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete() })
                }
            }
        }
    }
}

private fun Double.trim() = if (this % 1.0 == 0.0) toInt().toString() else toString()
