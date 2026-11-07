package com.posprint.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.posprint.app.domain.render.TemplateEngine

/** Tablet / landscape layouts switch to two panes at this width. */
@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= 840

/** "CENTER" -> "Center"; for enum names shown to people. */
fun titleCase(s: String) = s.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    number: Boolean = false,
    hint: String? = null,
    minLines: Int = 1,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** "receipt_no" -> "Receipt no.", "business.taxId" -> "Business tax id": code names shown as plain words. */
fun humanize(name: String): String {
    val words = name.replace(Regex("([a-z])([A-Z])"), "$1 $2").split('_', '.', ' ').filter { it.isNotBlank() }
        .map { w -> if (w.equals("no", true)) "no." else if (w.equals("qty", true)) "quantity" else w.lowercase() }
    return words.joinToString(" ").replaceFirstChar { it.uppercase() }.ifBlank { name }
}

private val NUMERIC = setOf("qty", "quantity", "price", "total", "amount", "subtotal", "tax", "discount")

/**
 * The items of a receipt or invoice as a simple list: one card per item with labelled boxes, buttons to add and
 * remove. Replaces typing "name, qty, total" lines, which non-technical users found confusing. When the table has a
 * quantity and a total, people type the unit price and the line total is worked out for them.
 */
@Composable
fun ItemsEditor(source: String, fields: List<String>, rows: List<Map<String, String>>, onRows: (List<Map<String, String>>) -> Unit) {
    val autoTotal = "total" in fields && "qty" in fields
    val editable = if (autoTotal) (fields - "total").let { if ("price" in it) it else it + "price" } else fields
    fun withTotal(r: Map<String, String>): Map<String, String> {
        if (!autoTotal) return r
        val q = r["qty"]?.replace(',', '.')?.toBigDecimalOrNull() ?: return r
        val p = r["price"]?.replace(',', '.')?.toBigDecimalOrNull() ?: return r
        return r + ("total" to (q * p).stripTrailingZeros().toPlainString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldLabel(humanize(source))
        if (rows.isEmpty()) Hint("No ${humanize(source).lowercase()} yet.")
        rows.forEachIndexed { i, row ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Item ${i + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (autoTotal) row["total"]?.takeIf { it.isNotBlank() }?.let {
                            Text("Line total $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.material3.IconButton({ onRows(rows.filterIndexed { j, _ -> j != i }) }) {
                            Icon(Icons.Rounded.Close, "Remove item ${i + 1}")
                        }
                    }
                    val text = editable.filterNot { it in NUMERIC }
                    val nums = editable.filter { it in NUMERIC }
                    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        text.forEach { f -> Field(humanize(f), row[f].orEmpty(), { v -> onRows(rows.mapIndexed { j, r -> if (j == i) withTotal(r + (f to v)) else r }) }) }
                        if (nums.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            nums.forEach { f ->
                                Field(humanize(f), row[f].orEmpty(), { v -> onRows(rows.mapIndexed { j, r -> if (j == i) withTotal(r + (f to v)) else r }) },
                                    Modifier.weight(1f), number = true)
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            { onRows(rows + (fields.associateWith { "" } + ("qty" to "1").takeIf { "qty" in fields }.let { listOfNotNull(it) }.toMap())) },
            Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.small,
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add item")
        }
    }
}

private fun fmtNumber(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(java.util.Locale.US, v)

/**
 * Decimal input that only reports values inside [range]. Out-of-range or half-typed values are flagged instead of
 * being clamped on every keystroke (clamping made it impossible to type e.g. "75" into a field whose minimum is 10).
 */
@Composable
fun DecimalField(
    label: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(fmtNumber(value)) }
    var focused by remember { mutableStateOf(false) }
    // External changes (dragging on the canvas) update the text; our own typing never gets overwritten mid-way.
    LaunchedEffect(value, focused) { if (!focused && text.replace(',', '.').toDoubleOrNull() != value) text = fmtNumber(value) }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    val bad = parsed == null || parsed !in range
    OutlinedTextField(
        value = text,
        onValueChange = { s -> text = s; s.replace(',', '.').toDoubleOrNull()?.takeIf { it in range }?.let(onChange) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        isError = bad,
        supportingText = if (bad) ({ Text("${fmtNumber(range.start)}–${fmtNumber(range.endInclusive)}") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
    )
}

/** Whole-number input; blank means "not set" (null). Values outside [range] are flagged and not reported. */
@Composable
fun OptionalIntField(label: String, value: Int?, range: IntRange, onChange: (Int?) -> Unit, hint: String? = null) {
    var text by remember { mutableStateOf(value?.toString().orEmpty()) }
    val parsed = text.toIntOrNull()
    val bad = text.isNotEmpty() && (parsed == null || parsed !in range)
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s.filter(Char::isDigit).take(4)
            val v = text.toIntOrNull()
            if (text.isEmpty()) onChange(null) else if (v != null && v in range) onChange(v)
        },
        label = { Text(label) },
        singleLine = true,
        isError = bad,
        supportingText = { Text(if (bad) "Use ${range.first}–${range.last}, or leave blank" else hint.orEmpty()) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipRow(label: String? = null, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (label != null) FieldLabel(label)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                val on = selected == o
                FilterChip(
                    selected = on,
                    onClick = { onSelect(o) },
                    label = { Text(text(o)) },
                    leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.width(FilterChipDefaults.IconSize)) }) else null,
                )
            }
        }
    }
}

/** Label + switch; the whole row is the touch target, which matters on a busy shop counter. */
@Composable
fun SwitchRow(label: String, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    valueText: String = value.toString(),
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton({ onChange((value - step).coerceIn(range)) }, enabled = value > range.first, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Rounded.Remove, "Decrease $label")
        }
        Text(valueText, Modifier.widthIn(min = 44.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        FilledTonalIconButton({ onChange((value + step).coerceIn(range)) }, enabled = value < range.last, shape = MaterialTheme.shapes.small) {
            Icon(Icons.Rounded.Add, "Increase $label")
        }
    }
}

@Composable
fun <T> Dropdown(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        FieldLabel(label)
        Box {
            OutlinedButton(
                { open = true },
                Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(start = 16.dp, end = 8.dp),
            ) {
                Text(text(selected), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(text(o)) },
                        onClick = { open = false; onSelect(o) },
                        trailingIcon = if (o == selected) ({ Icon(Icons.Rounded.Check, null) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
fun FieldLabel(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)

/** A quiet card that groups related form controls. */
@Composable
fun FormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

/** Short muted explanatory text under a control. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.padding(top = h.dp))

@Composable
fun FullWidthColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }

/** Read-only totals the app worked out (subtotal, total, change), so nobody has to calculate them. */
@Composable
fun CalculatedSummary(values: List<Pair<String, String>>) {
    if (values.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEach { (name, v) ->
                val big = name == "total"
                Row {
                    Text(humanize(name), Modifier.weight(1f), style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                    Text(v.ifBlank { "0" }, style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
                }
            }
            Text("Worked out automatically from the items, discount and tax.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f))
        }
    }
}
