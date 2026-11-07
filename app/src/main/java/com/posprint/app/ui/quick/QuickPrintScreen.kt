package com.posprint.app.ui.quick

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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.render.RText
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.Alignment
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.common.Field
import com.posprint.app.ui.common.Stepper
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickState(
    val text: String = "",
    val align: Alignment = Alignment.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val size: Int = 1,
    val copies: Int = 1,
    val printerId: Long? = null,
    val error: String? = null,
)

@HiltViewModel
class QuickPrintViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: PrinterRepository,
    private val tp: TemplatePrinter,
    private val print: PrintService,
    val assets: AndroidRenderAssets,
) : ViewModel() {
    private val _s = MutableStateFlow(QuickState(text = handle["text"] ?: ""))
    val state: StateFlow<QuickState> = _s.asStateFlow()
    private val jobId = MutableStateFlow<Long?>(null)

    val printers: StateFlow<List<Printer>> = repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val job: StateFlow<PrintJob?> = combine(jobId, print.jobs) { id, jobs -> jobs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun activePrinter(s: QuickState): Printer? = printers.value.firstOrNull { it.id == s.printerId } ?: printers.value.firstOrNull { it.isDefault } ?: printers.value.firstOrNull()

    val cols: Int get() = activePrinter(_s.value)?.cols ?: 32

    fun blocks(s: QuickState = _s.value): List<RText> {
        if (s.text.isBlank()) return emptyList() // lets the preview show its "nothing to print" hint
        val width = (cols / s.size).coerceAtLeast(1)
        return s.text.split("\n").flatMap { TemplateEngine.wrap(it, width) }
            .map { RText(it, s.align, s.bold, s.underline, widthMul = s.size, heightMul = s.size) }
    }

    fun update(change: (QuickState) -> QuickState) = _s.update(change)
    fun retry() { jobId.value?.let(print::retry) }
    fun cancel() { jobId.value?.let(print::cancel) }

    fun print() {
        val s = _s.value
        if (s.text.isBlank()) { _s.update { it.copy(error = "Type something to print.") }; return }
        viewModelScope.launch {
            try {
                val p = activePrinter(s) ?: repo.observeDefault().first() ?: throw PrinterException(com.posprint.app.domain.model.PrintError.NoPrinter())
                jobId.value = tp.printResolved("Quick print", blocks(s), p, s.copies)
                _s.update { it.copy(error = null) }
            } catch (e: PrinterException) { _s.update { it.copy(error = e.error.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPrintScreen(onBack: () -> Unit, vm: QuickPrintViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val printers by vm.printers.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Quick print") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { padding ->
        val form: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Text to print", s.text, { v -> vm.update { it.copy(text = v) } }, singleLine = false, minLines = 5, hint = "Type or paste. Any language works.")
                ChipRow("Alignment", Alignment.entries, s.align, { com.posprint.app.ui.common.titleCase(it.name) }) { a -> vm.update { it.copy(align = a) } }
                SwitchRow("Bold", s.bold) { b -> vm.update { it.copy(bold = b) } }
                SwitchRow("Underline", s.underline) { b -> vm.update { it.copy(underline = b) } }
                Stepper("Text size ×", s.size, 1..4) { z -> vm.update { it.copy(size = z) } }
                Stepper("Copies", s.copies, 1..20) { c -> vm.update { it.copy(copies = c) } }
                if (printers.size > 1) {
                    Dropdown("Print on", printers, printers.firstOrNull { it.id == s.printerId } ?: printers.firstOrNull { it.isDefault } ?: printers.first(),{ it.name }) { p -> vm.update { it.copy(printerId = p.id) } }
                }
                Button({ vm.print() }, Modifier.fillMaxWidth().height(56.dp), enabled = s.text.isNotBlank()) {
                    Icon(Icons.Rounded.Print, null)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Text(if (s.copies > 1) "Print ${s.copies} copies" else "Print")
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                job?.let { JobRow(it, onRetry = vm::retry, onCancel = vm::cancel) }
            }
        }
        val preview: @Composable () -> Unit = {
            Column(Modifier.padding(16.dp)) {
                com.posprint.app.ui.common.SectionTitle("Preview", Modifier.padding(bottom = 8.dp))
                ReceiptPreview(vm.blocks(s), vm.cols, vm.assets)
            }
        }
        if (isWideScreen()) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { form() }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { preview() }
            }
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) { form(); preview() }
    }
}
