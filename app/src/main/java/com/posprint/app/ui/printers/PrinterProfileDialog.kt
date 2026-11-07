package com.posprint.app.ui.printers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import com.posprint.app.ui.common.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.posprint.app.domain.model.CodePage
import com.posprint.app.domain.model.PaperWidth
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.Hint
import com.posprint.app.ui.common.OptionalIntField
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.common.Stepper
import com.posprint.app.ui.common.SwitchRow
import kotlin.math.roundToInt

/** Printer profile: language, paper, DPI, code page, cutter, drawer, density, margins. */
@Composable
fun PrinterProfileDialog(printer: Printer, onSave: (Printer) -> Unit, onDismiss: () -> Unit) {
    var p by remember { mutableStateOf(printer) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Printer profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Name", p.name, { p = p.copy(name = it) }, isError = p.name.isBlank())
                Dropdown("Command language", PrinterLanguage.entries, p.language, { it.label }) { p = p.copy(language = it) }
                if (!p.language.isLabel) {
                    ChipRow("Paper width", PaperWidth.entries, p.paperWidth, { "${it.mm} mm" }) { p = p.copy(paperWidth = it) }
                }
                ChipRow("Resolution", listOf(150, 203, 300, 600), p.dpi, { "$it dpi" }) { p = p.copy(dpi = it) }
                if (!p.language.isLabel) {
                    Dropdown("Code page", CodePage.entries, p.codePage, { it.label }) { p = p.copy(codePage = it) }
                    Hint("Text outside the code page (Arabic, Urdu, Hindi, CJK, italics) is printed as an image automatically.")
                    SwitchRow("Auto-cutter", p.hasCutter) { p = p.copy(hasCutter = it) }
                    SwitchRow("Cash drawer", p.hasDrawer) { p = p.copy(hasDrawer = it) }
                    SectionTitle("Fine tuning")
                    OptionalIntField(
                        "Characters per line", p.charsOverride, 16..96,
                        { v -> p = p.copy(charsOverride = v) },
                        hint = "Leave blank for the ${p.paperWidth.mm} mm default (${p.paperWidth.charsPerLine})",
                    )
                    val marginMm = (p.marginDots * 25.4 / p.dpi).roundToInt()
                    Stepper("Left margin", marginMm, 0..15, valueText = "$marginMm mm") { mm ->
                        p = p.copy(marginDots = (mm * p.dpi / 25.4).roundToInt())
                    }
                    SwitchRow("Set print density", p.density != null, supporting = "Some clones ignore this") { p = p.copy(density = if (it) 4 else null) }
                    p.density?.let { d -> Stepper("Density (0 light – 8 dark)", d, 0..8) { p = p.copy(density = it) } }
                    SwitchRow("Set line spacing", p.lineSpacing != null) { p = p.copy(lineSpacing = if (it) 30 else null) }
                    p.lineSpacing?.let { s -> Stepper("Line spacing (dots)", s, 0..80, step = 2) { p = p.copy(lineSpacing = it) } }
                }
            }
        },
        confirmButton = { TextButton({ onSave(p.copy(name = p.name.trim())) }, enabled = p.name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
