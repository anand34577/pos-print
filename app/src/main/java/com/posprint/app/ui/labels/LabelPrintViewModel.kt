package com.posprint.app.ui.labels

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.data.render.LabelRaster
import com.posprint.app.domain.io.Table
import com.posprint.app.domain.io.TableParser
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.template.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LabelPrintState(
    val template: Template? = null,
    val batch: Boolean = false,
    val vars: Map<String, String> = emptyMap(),
    val table: Table? = null,
    /** template variable -> column header ("" = not mapped) */
    val mapping: Map<String, String> = emptyMap(),
    val copies: Int = 1,
    val serialOn: Boolean = false,
    val serialStart: String = "1",
    val serialStep: String = "1",
    val serialPad: Int = 0,
    val serialPrefix: String = "",
    val previewRow: Int = 0,
    val printerId: Long? = null,
    val error: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class LabelPrintViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: TemplateRepository,
    printerRepo: PrinterRepository,
    private val tp: TemplatePrinter,
    private val print: PrintService,
    val assets: AndroidRenderAssets,
) : ViewModel() {
    private val id: Long = handle["id"] ?: 0L
    private val scanned: String = handle["scan"] ?: ""

    private val _s = MutableStateFlow(LabelPrintState())
    val state: StateFlow<LabelPrintState> = _s.asStateFlow()
    private val jobId = MutableStateFlow<Long?>(null)

    val printers: StateFlow<List<Printer>> =
        printerRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val job: StateFlow<PrintJob?> = combine(jobId, print.jobs) { id, jobs -> jobs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Variables the label uses. */
    val variables: StateFlow<List<String>> = _s.mapLatest { s -> s.template?.let { LabelPlanner.variablesUsed(it.elements) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val preview: StateFlow<ImageBitmap?> = _s.mapLatest { s ->
        val t = s.template ?: return@mapLatest null
        val rows = rowsFor(s)
        val vars = rows.getOrNull(s.previewRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0))) ?: s.vars
        val withSerial = if (s.serialOn) vars + ("serial" to serial(s).at(s.previewRow)) else vars
        val els = LabelPlanner.resolve(t.elements, withSerial, tp.renderContext(32))
        LabelRaster.render(t.label, els, assets, 2f).asImageBitmap()
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            val t = repo.get(id) ?: return@launch
            val used = LabelPlanner.variablesUsed(t.elements)
            var vars = t.sample.vars
            if (scanned.isNotBlank()) {
                val target = used.firstOrNull { Regex("sku|barcode|code|upc|ean|id", RegexOption.IGNORE_CASE).containsMatchIn(it) } ?: used.firstOrNull()
                if (target != null) vars = vars + (target to scanned)
            }
            _s.update { it.copy(template = t, vars = vars, printerId = t.printerId) }
        }
    }

    private fun serial(s: LabelPrintState) = TemplatePrinter.Serial(
        s.serialStart.toLongOrNull() ?: 1, s.serialStep.toLongOrNull() ?: 1, s.serialPad, s.serialPrefix,
    )

    private fun rowsFor(s: LabelPrintState): List<Map<String, String>> {
        if (!s.batch || s.table == null) return listOf(s.vars)
        val qtyCol = s.table.headers.firstOrNull { it.equals("qty", true) || it.equals("quantity", true) || it.equals("copies", true) }
        return s.table.records().map { rec ->
            val row = s.mapping.mapNotNull { (v, col) -> if (col.isBlank()) null else v to (rec[col] ?: "") }.toMap().toMutableMap()
            // unmapped variables fall back to the fixed values typed in single mode
            s.vars.forEach { (k, v) -> row.putIfAbsent(k, v) }
            if (qtyCol != null) rec[qtyCol]?.trim()?.takeIf { it.toIntOrNull() != null }?.let { row["qty"] = it }
            row
        }
    }

    fun update(change: (LabelPrintState) -> LabelPrintState) = _s.update(change)
    fun setVar(k: String, v: String) = _s.update { it.copy(vars = it.vars + (k to v)) }
    fun map(variable: String, column: String) = _s.update { it.copy(mapping = it.mapping + (variable to column)) }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val table = withContext(Dispatchers.IO) {
                runCatching {
                    val isXlsx = (context.contentResolver.getType(uri).orEmpty() + uri.toString()).contains("sheet", true) || uri.toString().endsWith(".xlsx", true)
                    context.contentResolver.openInputStream(uri)!!.use { s -> if (isXlsx) TableParser.xlsx(s) else TableParser.csv(s.bufferedReader().readText()) }
                }.getOrNull()
            }
            if (table == null || table.headers.isEmpty()) {
                _s.update { it.copy(error = "Could not read that file. Use a .csv or .xlsx with a header row.") }
                return@launch
            }
            val used = LabelPlanner.variablesUsed(_s.value.template?.elements.orEmpty())
            val auto = used.associateWith { v -> table.headers.firstOrNull { it.equals(v, true) } ?: "" }
            _s.update { it.copy(table = table, mapping = auto, batch = true, previewRow = 0, error = null) }
        }
    }

    fun retry() { jobId.value?.let(print::retry) }
    fun cancel() { jobId.value?.let(print::cancel) }

    fun print() {
        val s = _s.value
        val t = s.template ?: return
        viewModelScope.launch {
            _s.update { it.copy(error = null) }
            try {
                val printer = s.printerId?.let { pid -> printers.value.firstOrNull { it.id == pid } }
                jobId.value = tp.printLabels(t, rowsFor(s), s.copies, printer, if (s.serialOn) serial(s) else null)
            } catch (e: PrinterException) {
                _s.update { it.copy(error = e.error.message) }
            }
        }
    }

    fun rowCount(s: LabelPrintState) = rowsFor(s).size
    fun totalLabels(s: LabelPrintState) = rowsFor(s).sumOf { it["qty"]?.toIntOrNull()?.coerceAtLeast(1) ?: s.copies }
}
