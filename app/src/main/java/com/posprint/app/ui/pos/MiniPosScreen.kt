package com.posprint.app.ui.pos

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import com.posprint.app.ui.common.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.render.Formatter
import com.posprint.app.domain.render.RBlock
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.SwitchRow
import com.posprint.app.ui.common.isWideScreen
import com.posprint.app.ui.components.JobRow
import com.posprint.app.ui.preview.ReceiptPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

data class PosItem(val id: Int, val name: String, val price: BigDecimal, val qty: Int)

data class Totals(val subtotal: BigDecimal, val discount: BigDecimal, val tax: BigDecimal, val total: BigDecimal, val change: BigDecimal)

data class PosState(
    val items: List<PosItem> = emptyList(),
    val percentDiscount: Boolean = false,
    val discount: String = "",
    val taxPct: String = "0",
    val paid: String = "",
    val openDrawer: Boolean = false,
    val error: String? = null,
    val formatter: Formatter = Formatter(),
    val nextId: Int = 1,
)

/** Money typed by a person: accepts "3.50" and the decimal comma many keyboards produce ("3,50"). */
fun parseAmount(s: String): BigDecimal? = s.trim().replace(',', '.').toBigDecimalOrNull()

fun totals(s: PosState): Totals {
    val dec = s.formatter.decimals.coerceIn(0, 6)
    val sub = s.items.fold(BigDecimal.ZERO) { a, i -> a + i.price * BigDecimal(i.qty) }
    val dv = parseAmount(s.discount) ?: BigDecimal.ZERO
    val disc = (if (s.percentDiscount) sub * dv / BigDecimal(100) else dv).setScale(dec, RoundingMode.HALF_UP).min(sub).max(BigDecimal.ZERO)
    val taxable = sub - disc
    val tax = (taxable * (parseAmount(s.taxPct) ?: BigDecimal.ZERO) / BigDecimal(100)).setScale(dec, RoundingMode.HALF_UP)
    val total = (taxable + tax).setScale(dec, RoundingMode.HALF_UP)
    val paid = parseAmount(s.paid) ?: BigDecimal.ZERO
    return Totals(sub.setScale(dec, RoundingMode.HALF_UP), disc, tax, total, (paid - total).max(BigDecimal.ZERO).setScale(dec, RoundingMode.HALF_UP))
}

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MiniPosViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val templates: TemplateRepository,
    private val printers: PrinterRepository,
    private val tp: TemplatePrinter,
    private val print: PrintService,
    val assets: AndroidRenderAssets,
) : ViewModel() {
    private val _s = MutableStateFlow(PosState())
    val state: StateFlow<PosState> = _s.asStateFlow()
    private val jobId = MutableStateFlow<Long?>(null)
    val job: StateFlow<PrintJob?> = combine(jobId, print.jobs) { id, jobs -> jobs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val p = settings.profileNow()
            _s.update { it.copy(formatter = p.formatter(), taxPct = p.defaultTaxPct) }
        }
    }

    private suspend fun template(): Template? =
        templates.observeAll().first().let { l -> l.firstOrNull { it.kind == TemplateKind.RECEIPT && it.name == "Sales receipt" } ?: l.firstOrNull { it.kind == TemplateKind.RECEIPT } }

    private fun data(s: PosState, receiptNo: String): TemplateData {
        val t = totals(s)
        val paid = parseAmount(s.paid) ?: BigDecimal.ZERO
        return TemplateData(
            vars = mapOf(
                "receipt_no" to receiptNo, "subtotal" to t.subtotal.toPlainString(), "discount" to t.discount.toPlainString(),
                "tax" to t.tax.toPlainString(), "total" to t.total.toPlainString(),
                "paid" to if (paid.signum() > 0) paid.toPlainString() else "", "change" to t.change.toPlainString(),
                "open_drawer" to if (s.openDrawer) "1" else "",
            ),
            lists = mapOf("items" to s.items.map { mapOf("name" to it.name, "qty" to it.qty.toString(), "price" to it.price.toPlainString(), "total" to (it.price * BigDecimal(it.qty)).toPlainString()) }),
        )
    }

    val preview: StateFlow<List<RBlock>> = combine(_s, printers.observeDefault()) { s, p -> s to p }.flatMapLatest { (s, p) ->
        flow {
            val t = template() ?: return@flow
            emit(tp.resolve(t.blocks, data(s, "…"), p?.cols ?: 32))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val cols: StateFlow<Int> = printers.observeDefault().map { p -> p?.cols ?: 32 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 32)

    fun update(change: (PosState) -> PosState) = _s.update(change)
    fun add(name: String, price: String, qty: Int) {
        val p = parseAmount(price)?.takeIf { it.signum() >= 0 } ?: return
        _s.update { it.copy(items = it.items + PosItem(it.nextId, name.ifBlank { "Item" }, p, qty.coerceAtLeast(1)), nextId = it.nextId + 1) }
    }
    fun qty(id: Int, delta: Int) = _s.update { s -> s.copy(items = s.items.mapNotNull { if (it.id == id) it.copy(qty = it.qty + delta).takeIf { n -> n.qty > 0 } else it }) }
    fun remove(id: Int) = _s.update { s -> s.copy(items = s.items.filterNot { it.id == id }) }
    fun clear() { _s.update { it.copy(items = emptyList(), discount = "", paid = "", openDrawer = false, error = null) }; jobId.value = null }
    fun retry() { jobId.value?.let(print::retry) }
    fun cancel() { jobId.value?.let(print::cancel) }

    fun printReceipt() {
        val s = _s.value
        if (s.items.isEmpty()) { _s.update { it.copy(error = "Add at least one item.") }; return }
        viewModelScope.launch {
            try {
                val t = template() ?: throw PrinterException(PrintError.Other("No receipt template found. Create one in Templates."))
                val no = settings.nextReceiptNumber().toString()
                jobId.value = tp.printTemplate(t, data(s, no))
                _s.update { it.copy(error = null) }
            } catch (e: PrinterException) { _s.update { it.copy(error = e.error.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPosScreen(onBack: () -> Unit, vm: MiniPosViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val cols by vm.cols.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    val t = totals(s)
    val f = s.formatter

    Scaffold(topBar = {
        TopAppBar(title = { Text("Mini POS") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = { TextButton(vm::clear, enabled = s.items.isNotEmpty()) { Text("New sale") } })
    }) { padding ->
        val main: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (s.items.isEmpty()) Text("No items yet. Tap Add item to start a sale.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                s.items.forEach { i ->
                    Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(i.name, style = MaterialTheme.typography.titleSmall)
                                Text("${f.money(i.price.toPlainString())} × ${i.qty} = ${f.money((i.price * BigDecimal(i.qty)).toPlainString())}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton({ vm.qty(i.id, -1) }) { Icon(Icons.Rounded.Remove, "Decrease ${i.name}") }
                            Text(i.qty.toString(), style = MaterialTheme.typography.titleMedium)
                            IconButton({ vm.qty(i.id, 1) }) { Icon(Icons.Rounded.Add, "Increase ${i.name}") }
                            IconButton({ vm.remove(i.id) }) { Icon(Icons.Rounded.Delete, "Remove ${i.name}") }
                        }
                    }
                }
                OutlinedButton({ adding = true }, Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("Add item")
                }
                ChipRow("Discount", listOf(false, true), s.percentDiscount, { if (it) "Percent %" else "Amount" }) { p -> vm.update { it.copy(percentDiscount = p) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Discount", s.discount, { v -> vm.update { it.copy(discount = v) } }, Modifier.weight(1f), number = true)
                    Field("Tax %", s.taxPct, { v -> vm.update { it.copy(taxPct = v) } }, Modifier.weight(1f), number = true)
                }
                Card(Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TotalRow("Subtotal", f.money(t.subtotal.toPlainString()))
                        if (t.discount.signum() > 0) TotalRow("Discount", "-" + f.money(t.discount.toPlainString()))
                        if (t.tax.signum() > 0) TotalRow("Tax", f.money(t.tax.toPlainString()))
                        TotalRow("Total", f.money(t.total.toPlainString()), big = true)
                    }
                }
                Field("Amount paid (optional)", s.paid, { v -> vm.update { it.copy(paid = v) } }, number = true,
                    hint = if (t.change.signum() > 0) "Change: ${f.money(t.change.toPlainString())}" else null)
                SwitchRow("Open cash drawer", s.openDrawer) { d -> vm.update { it.copy(openDrawer = d) } }
                Button(vm::printReceipt, Modifier.fillMaxWidth().height(56.dp), enabled = s.items.isNotEmpty()) {
                    Icon(Icons.Rounded.Print, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text("Print receipt · ${f.money(t.total.toPlainString())}")
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                job?.let { j ->
                    JobRow(j, onRetry = vm::retry, onCancel = vm::cancel)
                    if (j.status == com.posprint.app.domain.model.JobStatus.DONE) {
                        com.posprint.app.ui.common.FilledTonalButton(vm::clear, Modifier.fillMaxWidth().height(52.dp)) { Text("Start a new sale") }
                    }
                }
            }
        }
        val side: @Composable () -> Unit = { Column(Modifier.padding(16.dp)) { ReceiptPreview(preview, cols, vm.assets) } }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { main() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { side() }
            }
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { main(); side() }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        var qty by remember { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Name", name, { name = it })
                    Field("Price", price, { price = it }, number = true, isError = price.isNotEmpty() && parseAmount(price) == null)
                    Field("Quantity", qty, { qty = it.filter(Char::isDigit).take(4) }, number = true)
                    val line = parseAmount(price)?.let { p -> p * BigDecimal(qty.toIntOrNull()?.coerceAtLeast(1) ?: 1) }
                    if (line != null) Text("Line total ${f.money(line.toPlainString())}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton({ vm.add(name, price, qty.toIntOrNull() ?: 1); adding = false }, enabled = parseAmount(price) != null) { Text("Add") } },
            dismissButton = { TextButton({ adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TotalRow(label: String, value: String, big: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
        Text(value, style = if (big) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
    }
}
