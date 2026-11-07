package com.posprint.app.ui.templates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posprint.app.domain.render.BarcodeMatrix
import com.posprint.app.domain.template.Alignment as Al
import com.posprint.app.domain.template.BarcodeBlock
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.Block
import com.posprint.app.domain.template.Column
import com.posprint.app.domain.template.ColumnsBlock
import com.posprint.app.domain.template.CommandBlock
import com.posprint.app.domain.template.CommandKind
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.DividerBlock
import com.posprint.app.domain.template.FooterBlock
import com.posprint.app.domain.template.HeaderBlock
import com.posprint.app.domain.template.ImageBlock
import com.posprint.app.domain.template.QrBlock
import com.posprint.app.domain.template.SpacerBlock
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.domain.template.TextBlock
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.common.SwitchRow
import com.posprint.app.ui.common.titleCase
import kotlinx.coroutines.launch

fun commandLabel(k: CommandKind) = when (k) { CommandKind.CUT -> "Cut paper"; CommandKind.DRAWER -> "Open cash drawer"; CommandKind.BEEP -> "Beep" }

fun blockTitle(b: Block) = when (b) {
    is TextBlock -> "Text"; is DividerBlock -> "Divider"; is ColumnsBlock -> "Two columns"; is TableBlock -> "Table"
    is ImageBlock -> "Image"; is QrBlock -> "QR code"; is BarcodeBlock -> "Barcode"; is SpacerBlock -> "Spacer"
    is CommandBlock -> "Command"; is HeaderBlock -> "Header"; is FooterBlock -> "Footer"
}

fun blockSummary(b: Block): String = when (b) {
    is TextBlock -> b.text.lines().firstOrNull().orEmpty().ifBlank { "(empty)" }
    is DividerBlock -> b.char.repeat(12)
    is ColumnsBlock -> "${b.left}  …  ${b.right}"
    is TableBlock -> "${b.source}: " + b.columns.joinToString(" | ") { it.title }
    is ImageBlock -> if (b.source.startsWith("data:")) "Embedded image" else b.source
    is QrBlock -> b.data
    is BarcodeBlock -> "${b.type.label}: ${b.data}"
    is SpacerBlock -> "${b.lines} blank line(s)"
    is CommandBlock -> commandLabel(b.kind)
    is HeaderBlock -> "Business name, address, tax ID" + if (b.showLogo) " + logo" else ""
    is FooterBlock -> "Business footer message"
}.let { s -> if (b.showIf != null) "$s  (if ${b.showIf})" else s }

val newBlockChoices: List<Pair<String, () -> Block>> = listOf(
    "Header" to { HeaderBlock() }, "Text" to { TextBlock("Text") }, "Divider" to { DividerBlock() },
    "Two columns" to { ColumnsBlock("Label", "{{value}}") }, "Table" to { TableBlock() },
    "Image" to { ImageBlock() }, "QR code" to { QrBlock() }, "Barcode" to { BarcodeBlock() },
    "Spacer" to { SpacerBlock() }, "Footer" to { FooterBlock() }, "Command" to { CommandBlock() },
)

@Composable
fun BlockEditorDialog(initial: Block, onSave: (Block) -> Unit, onDismiss: () -> Unit, onPickImage: suspend (android.net.Uri) -> String?) {
    var b by remember { mutableStateOf(initial) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${blockTitle(initial).lowercase()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val x = b) {
                    is TextBlock -> {
                        Field("Text", x.text, { b = x.copy(text = it) }, singleLine = false, minLines = 2, hint = "Use {{variable}} or {{price|money}}")
                        ChipRow("Alignment", Al.entries, x.align, { titleCase(it.name) }) { b = x.copy(align = it) }
                        SwitchRow("Bold", x.bold) { b = x.copy(bold = it) }
                        SwitchRow("Underline", x.underline) { b = x.copy(underline = it) }
                        SwitchRow("Invert (white on black)", x.invert) { b = x.copy(invert = it) }
                        SwitchRow("Italic (printed as image)", x.italic) { b = x.copy(italic = it) }
                        SwitchRow("Small font B", x.fontB) { b = x.copy(fontB = it) }
                        Stepper("Width ×", x.widthMul, 1..8) { b = x.copy(widthMul = it) }
                        Stepper("Height ×", x.heightMul, 1..8) { b = x.copy(heightMul = it) }
                    }
                    is DividerBlock -> Field("Character", x.char, { b = x.copy(char = it.takeLast(1).ifEmpty { x.char }) }, hint = "Repeated across the paper, e.g. - = * .")
                    is ColumnsBlock -> {
                        Field("Left", x.left, { b = x.copy(left = it) })
                        Field("Right", x.right, { b = x.copy(right = it) })
                        SwitchRow("Bold", x.bold) { b = x.copy(bold = it) }
                    }
                    is TableBlock -> {
                        Field("List variable", x.source, { b = x.copy(source = it.trim()) }, hint = "Rows come from this list, e.g. items")
                        SwitchRow("Show header", x.showHeader) { b = x.copy(showHeader = it) }
                        x.columns.forEachIndexed { i, c ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Column ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                    IconButton({ b = x.copy(columns = x.columns.filterIndexed { j, _ -> j != i }) }, enabled = x.columns.size > 1) {
                                        Icon(Icons.Rounded.Delete, "Remove column ${i + 1}")
                                    }
                                }
                                Field("Title", c.title, { v -> b = x.copy(columns = x.columns.mapIndexed { j, o -> if (j == i) o.copy(title = v) else o }) })
                                Field("Field name", c.field, { v -> b = x.copy(columns = x.columns.mapIndexed { j, o -> if (j == i) o.copy(field = v.trim()) else o }) })
                                Stepper("Width (0 = flexible)", c.width, 0..40) { v -> b = x.copy(columns = x.columns.mapIndexed { j, o -> if (j == i) o.copy(width = v) else o }) }
                                ChipRow(null, Al.entries, c.align, { titleCase(it.name) }) { v -> b = x.copy(columns = x.columns.mapIndexed { j, o -> if (j == i) o.copy(align = v) else o }) }
                            }
                        }
                        OutlinedButton({ b = x.copy(columns = x.columns + Column("Col", "field", 8, Al.RIGHT)) }) { Text("Add column") }
                    }
                    is ImageBlock -> {
                        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                            if (uri != null) scope.launch { onPickImage(uri)?.let { src -> b = (b as? ImageBlock)?.copy(source = src) ?: b } }
                        }
                        ChipRow("Source", listOf("logo", "custom"), if (x.source == "logo") "logo" else "custom", { if (it == "logo") "Business logo" else "Pick image" }) {
                            if (it == "logo") b = x.copy(source = "logo") else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                        Text("Width ${x.widthPct}% of paper", style = MaterialTheme.typography.labelLarge)
                        Slider(x.widthPct.toFloat(), { b = x.copy(widthPct = it.toInt()) }, valueRange = 10f..100f)
                        Dropdown("Dithering", DitherMode.entries, x.dither, { it.label }) { b = x.copy(dither = it) }
                        ChipRow("Alignment", Al.entries, x.align, { titleCase(it.name) }) { b = x.copy(align = it) }
                    }
                    is QrBlock -> {
                        Field("Data", x.data, { b = x.copy(data = it) }, hint = "Use {{variable}} for dynamic values")
                        Stepper("Module size", x.size, 1..16) { b = x.copy(size = it) }
                        ChipRow("Alignment", Al.entries, x.align, { titleCase(it.name) }) { b = x.copy(align = it) }
                    }
                    is BarcodeBlock -> {
                        Field("Data", x.data, { b = x.copy(data = it) }, hint = "Use {{variable}} for dynamic values")
                        Dropdown("Type", BarcodeType.entries, x.type, { it.label }) { b = x.copy(type = it) }
                        Text("Height ${x.height} dots", style = MaterialTheme.typography.labelLarge)
                        Slider(x.height.toFloat(), { b = x.copy(height = it.toInt()) }, valueRange = 30f..200f)
                        ChipRow("Alignment", Al.entries, x.align, { titleCase(it.name) }) { b = x.copy(align = it) }
                        if (!x.data.contains("{{")) BarcodeMatrix.validate(x.type, x.data)?.let {
                            Text("⚠ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is SpacerBlock -> Stepper("Blank lines", x.lines, 1..10) { b = x.copy(lines = it) }
                    is CommandBlock -> {
                        ChipRow("Action", CommandKind.entries, x.kind, { commandLabel(it) }) { b = x.copy(kind = it) }
                        Text("Cut and cash drawer only act when the printer profile has them enabled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is HeaderBlock -> SwitchRow("Show logo", x.showLogo) { b = x.copy(showLogo = it) }
                    is FooterBlock -> Text("Prints the footer message from Settings → Business profile.", style = MaterialTheme.typography.bodyMedium)
                }
                // A condition is an expert feature; keep it out of the way unless someone asks for it or one is set.
                var advanced by remember { mutableStateOf(initial.showIf != null) }
                SwitchRow("Print only sometimes", advanced, supporting = "Advanced: skip this block when a condition is not met") {
                    advanced = it
                    if (!it) b = withShowIf(b, null)
                }
                if (advanced) Field(
                    "Print only if", b.showIf.orEmpty(),
                    { v -> b = withShowIf(b, v.ifBlank { null }) },
                    hint = "e.g. discount > 0   or   customer   or   !paid",
                )
            }
        },
        confirmButton = { TextButton({ onSave(b) }) { Text("Done") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

private fun withShowIf(b: Block, v: String?): Block = when (b) {
    is TextBlock -> b.copy(showIf = v); is DividerBlock -> b.copy(showIf = v); is ColumnsBlock -> b.copy(showIf = v)
    is TableBlock -> b.copy(showIf = v); is ImageBlock -> b.copy(showIf = v); is QrBlock -> b.copy(showIf = v)
    is BarcodeBlock -> b.copy(showIf = v); is SpacerBlock -> b.copy(showIf = v); is CommandBlock -> b.copy(showIf = v)
    is HeaderBlock -> b.copy(showIf = v); is FooterBlock -> b.copy(showIf = v)
}
