package com.posprint.app.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrintTemplateViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: TemplateRepository,
    printerRepo: PrinterRepository,
    private val tp: TemplatePrinter,
    private val print: PrintService,
    val assets: AndroidRenderAssets,
    private val settings: com.posprint.app.data.SettingsStore,
) : ViewModel() {
    private val id: Long = handle["id"] ?: 0L
    private var used: Set<String> = emptySet()
    private val _calculated = MutableStateFlow<Set<String>>(emptySet())
    /** Values worked out automatically (subtotal, total, change); shown read-only. */
    val calculated: StateFlow<Set<String>> = _calculated.asStateFlow()

    private val _template = MutableStateFlow<Template?>(null)
    val template: StateFlow<Template?> = _template.asStateFlow()
    private val _data = MutableStateFlow(TemplateData())
    val data: StateFlow<TemplateData> = _data.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val jobId = MutableStateFlow<Long?>(null)
    private val chosen = MutableStateFlow<Long?>(null)
    val chosenPrinterId: Long? get() = chosen.value

    val printers: StateFlow<List<Printer>> =
        printerRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val job: StateFlow<PrintJob?> = combine(jobId, print.jobs) { id, jobs -> jobs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val preview: StateFlow<PreviewState> = combine(_template, _data, printers, chosen) { t, d, p, c -> arrayOf(t, d, p, c) }
        .flatMapLatest { a ->
            flow {
                val t = a[0] as Template? ?: return@flow
                @Suppress("UNCHECKED_CAST") val plist = a[2] as List<Printer>
                val cols = ((plist.firstOrNull { it.id == (a[3] as Long?) } ?: plist.firstOrNull { it.id == t.printerId }
                    ?: plist.firstOrNull { it.isDefault } ?: plist.firstOrNull())?.cols) ?: 32
                emit(PreviewState(tp.resolve(t.blocks, a[1] as TemplateData, cols), cols))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewState())

    init {
        viewModelScope.launch {
            val t = repo.get(id) ?: return@launch
            used = com.posprint.app.domain.render.TemplateEngine.variablesUsed(t.blocks).toSet()
            var d = t.sample
            // The receipt number counts up by itself; people should not have to remember the last one.
            if ("receipt_no" in used) d = d.copy(vars = d.vars + ("receipt_no" to settings.peekReceiptNumber().toString()))
            _template.value = t
            _data.value = d
            recalc()
        }
    }

    private fun recalc() {
        val (d, c) = com.posprint.app.domain.render.ReceiptMath.autofill(_data.value, used)
        _data.value = d
        _calculated.value = c
    }

    fun choosePrinter(id: Long?) { chosen.value = id }
    fun setVar(k: String, v: String) { _data.update { it.copy(vars = it.vars + (k to v)) }; recalc() }
    fun setList(source: String, rows: List<Map<String, String>>) { _data.update { it.copy(lists = it.lists + (source to rows)) }; recalc() }
    fun retry() { jobId.value?.let(print::retry) }
    fun cancel() { jobId.value?.let(print::cancel) }

    fun print() {
        val t = _template.value ?: return
        viewModelScope.launch {
            _error.value = null
            try {
                val printer = chosen.value?.let { c -> printers.value.firstOrNull { it.id == c } }
                jobId.value = tp.printTemplate(t, _data.value, printer)
                if ("receipt_no" in used && _data.value.vars["receipt_no"] == settings.peekReceiptNumber().toString()) {
                    settings.nextReceiptNumber()
                    setVar("receipt_no", settings.peekReceiptNumber().toString()) // ready for the next customer
                }
            } catch (e: PrinterException) {
                _error.value = e.error.message
            }
        }
    }
}
