package com.posprint.app.ui.labels

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlipToFront
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.BarcodeMatrix
import com.posprint.app.domain.template.Alignment as Al
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LBox
import com.posprint.app.domain.template.LImage
import com.posprint.app.domain.template.LLine
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.MediaType
import com.posprint.app.domain.template.Template
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.SwitchRow
import com.posprint.app.ui.common.isWideScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelDesignerScreen(onBack: () -> Unit, onPrint: (Long) -> Unit, vm: LabelDesignerViewModel = hiltViewModel()) {
    val t by vm.template.collectAsStateWithLifecycle()
    val dirty by vm.dirty.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val snap by vm.snap.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    val exit = { vm.discardIfFresh(); onBack() }
    val leave = { if (dirty) confirmLeave = true else exit() }
    BackHandler(onBack = leave)

    val template = t
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(template?.name ?: "Label", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = {
                TextButton({ vm.save() }, enabled = dirty) { Text("Save") }
                IconButton({ vm.save { onPrint(template?.id ?: 0) } }, enabled = template != null) { Icon(Icons.Rounded.Print, "Save and print") }
            },
        )
    }) { padding ->
        if (template == null) return@Scaffold
        val canvas: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LabelCanvas(template.label, template.elements, preview, selected, snap, vm::select, vm::update, Modifier.fillMaxWidth())
                }
                AddBar(vm::add)
            }
        }
        val panel: @Composable () -> Unit = { Panel(template, selected, snap, printers, vm) }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState())) { canvas() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { panel() }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { canvas(); panel() }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Save changes?") },
            text = { Text("You have unsaved changes to this label.") },
            confirmButton = { TextButton({ confirmLeave = false; vm.save(onBack) }) { Text("Save") } },
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
private fun AddBar(add: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("text" to "Text", "barcode" to "Barcode", "qr" to "QR code", "image" to "Image", "line" to "Line", "box" to "Box")
            .forEach { (k, label) ->
                AssistChip(
                    { add(k) }, { Text(label) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(androidx.compose.material3.AssistChipDefaults.IconSize)) },
                )
            }
    }
}

@Composable
private fun Panel(t: Template, selectedId: String?, snap: Boolean, printers: List<Printer>, vm: LabelDesignerViewModel) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val el = t.elements.firstOrNull { it.id == selectedId }
        if (el != null) ElementCard(el, vm) else com.posprint.app.ui.common.Hint("Tap an element on the label to edit it. Drag to move; drag the corner handle to resize.")

        SectionTitle("Label")
        Field("Name", t.name, { v -> vm.edit { it.copy(name = v) } })
        Dropdown("Size preset", LABEL_PRESETS, LABEL_PRESETS.firstOrNull { it.second == (t.label.widthMm to t.label.heightMm) } ?: ("Custom" to (0.0 to 0.0)), { it.first }) { (_, wh) ->
            vm.setSpec { it.copy(widthMm = wh.first, heightMm = wh.second) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MmField("Width (mm)", t.label.widthMm, 10.0..300.0, Modifier.weight(1f)) { v -> vm.setSpec { it.copy(widthMm = v) } }
            MmField("Height (mm)", t.label.heightMm, 5.0..600.0, Modifier.weight(1f)) { v -> vm.setSpec { it.copy(heightMm = v) } }
        }
        ChipRow("Media", MediaType.entries, t.label.media, { it.label }) { m -> vm.setSpec { it.copy(media = m) } }
        if (t.label.media != MediaType.CONTINUOUS) MmField("Gap / mark height (mm)", t.label.gapMm, 0.0..20.0) { v -> vm.setSpec { it.copy(gapMm = v) } }
        SwitchRow("Snap to 1 mm grid", snap) { vm.setSnap(it) }
        if (printers.size > 1) {
            val none = Printer(id = -1, name = "Default printer", connection = ConnectionType.BLUETOOTH, address = "")
            Dropdown("Printer for this label", listOf(none) + printers, printers.firstOrNull { it.id == t.printerId } ?: none, { it.name }) { p ->
                vm.edit { it.copy(printerId = if (p.id == -1L) null else p.id) }
            }
        }

        SectionTitle("Preview data")
        val vars = com.posprint.app.domain.render.LabelPlanner.variablesUsed(t.elements)
        if (vars.isEmpty()) Text("Use {{name}} in text to make a field variable.", style = MaterialTheme.typography.bodySmall)
        vars.forEach { name -> Field(com.posprint.app.ui.common.humanize(name), t.sample.vars[name].orEmpty(), { v -> vm.edit { it.copy(sample = it.sample.copy(vars = it.sample.vars + (name to v))) } }, singleLine = false) }
    }
}

/** Millimetre input: reports only in-range values, so typing is never fought by clamping (see DecimalField). */
@Composable
private fun MmField(label: String, value: Double, range: ClosedFloatingPointRange<Double>, modifier: Modifier = Modifier, onChange: (Double) -> Unit) =
    com.posprint.app.ui.common.DecimalField(label, value, range, onChange, modifier)

@Composable
private fun ElementCard(el: LabelElement, vm: LabelDesignerViewModel) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(elementName(el), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton({ vm.rotate(el.id) }) { Icon(Icons.Rounded.RotateRight, "Rotate 90 degrees") }
                IconButton({ vm.bringToFront(el.id) }) { Icon(Icons.Rounded.FlipToFront, "Bring to front") }
                IconButton({ vm.duplicate(el.id) }) { Icon(Icons.Rounded.ContentCopy, "Duplicate") }
                IconButton({ vm.remove(el.id) }) { Icon(Icons.Rounded.Delete, "Delete") }
            }
            when (el) {
                is LText -> {
                    Field("Text", el.text, { vm.update(el.copy(text = it)) }, singleLine = false, minLines = 2, hint = "Use {{product_name}}, {{price|money}}, {{date}}, {{serial}}")
                    MmField("Font height (mm)", el.fontMm, 1.5..40.0) { vm.update(el.copy(fontMm = it)) }
                    SwitchRow("Bold", el.bold) { vm.update(el.copy(bold = it)) }
                    ChipRow("Alignment", Al.entries, el.align, { com.posprint.app.ui.common.titleCase(it.name) }) { vm.update(el.copy(align = it)) }
                }
                is LBarcode -> {
                    Field("Data", el.data, { vm.update(el.copy(data = it)) }, hint = "Use {{sku}} for a variable")
                    Dropdown("Type", BarcodeType.entries, el.type, { it.label }) { vm.update(el.copy(type = it)) }
                    SwitchRow("Show text below", el.humanReadable) { vm.update(el.copy(humanReadable = it)) }
                    if (!el.data.contains("{{")) BarcodeMatrix.validate(el.type, el.data)?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error) }
                }
                is LQr -> Field("Data", el.data, { vm.update(el.copy(data = it)) }, hint = "Use {{sku}} for a variable")
                is LImage -> {
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                        if (uri != null) scope.launch { vm.pickImage(uri)?.let { vm.update(el.copy(source = it)) } }
                    }
                    ChipRow("Source", listOf("logo", "custom"), if (el.source == "logo") "logo" else "custom", { if (it == "logo") "Business logo" else "Pick image" }) {
                        if (it == "logo") vm.update(el.copy(source = "logo")) else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    ChipRow("Dithering", DitherMode.entries, el.dither, { it.label }) { vm.update(el.copy(dither = it)) }
                }
                is LLine -> MmField("Thickness (mm)", el.thicknessMm, 0.1..5.0) { vm.update(el.copy(thicknessMm = it)) }
                is LBox -> MmField("Border (mm)", el.thicknessMm, 0.1..5.0) { vm.update(el.copy(thicknessMm = it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmField("X (mm)", el.xMm, 0.0..600.0, Modifier.weight(1f)) { vm.update(el.moved(it, el.yMm)) }
                MmField("Y (mm)", el.yMm, 0.0..600.0, Modifier.weight(1f)) { vm.update(el.moved(el.xMm, it)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MmField("Width (mm)", el.wMm, 0.5..600.0, Modifier.weight(1f)) { vm.update(el.moved(el.xMm, el.yMm, it)) }
                MmField("Height (mm)", el.hMm, 0.5..600.0, Modifier.weight(1f)) { vm.update(el.moved(el.xMm, el.yMm, el.wMm, it)) }
            }
            Text("Rotation ${el.rotation}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun elementName(e: LabelElement) = when (e) {
    is LText -> "Text"; is LBarcode -> "Barcode"; is LQr -> "QR code"; is LImage -> "Image"; is LLine -> "Line"; is LBox -> "Box"
}
