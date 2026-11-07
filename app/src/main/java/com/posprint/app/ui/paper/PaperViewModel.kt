package com.posprint.app.ui.paper

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.DocumentLayout
import com.posprint.app.data.print.DocumentPrinter
import com.posprint.app.data.print.PaperOptions
import com.posprint.app.data.print.PaperSize
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PageView(val pageCount: Int = 0, val image: ImageBitmap? = null)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PaperViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repo: TemplateRepository,
    private val docs: DocumentPrinter,
    settings: SettingsStore,
) : ViewModel() {
    private val id: Long = handle["id"] ?: 0L

    private val _template = MutableStateFlow<Template?>(null)
    val template: StateFlow<Template?> = _template.asStateFlow()
    private val _data = MutableStateFlow(TemplateData())
    val data: StateFlow<TemplateData> = _data.asStateFlow()
    private val _options = MutableStateFlow(PaperOptions())
    val options: StateFlow<PaperOptions> = _options.asStateFlow()
    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var layout: DocumentLayout? = null
    private var used: Set<String> = emptySet()
    private val _calculated = MutableStateFlow<Set<String>>(emptySet())
    val calculated: StateFlow<Set<String>> = _calculated.asStateFlow()

    private fun recalc() {
        val (d, c) = com.posprint.app.domain.render.ReceiptMath.autofill(_data.value, used)
        _data.value = d
        _calculated.value = c
    }

    val view: StateFlow<PageView> = combine(_template, _data, _options, _page) { t, d, o, p -> arrayOf(t, d, o, p) }
        .debounce(80)
        .mapLatest { a ->
            val t = a[0] as Template? ?: return@mapLatest PageView()
            val l = docs.layout(t, a[1] as TemplateData, a[2] as PaperOptions)
            layout = l
            val idx = (a[3] as Int).coerceIn(0, l.pageCount - 1)
            PageView(l.pageCount, l.renderBitmap(idx, 1.4f).asImageBitmap())
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageView())

    init {
        viewModelScope.launch {
            val t = repo.get(id) ?: return@launch
            val ui = settings.ui.first()
            used = com.posprint.app.domain.render.TemplateEngine.variablesUsed(t.blocks).toSet()
            _template.value = t
            _data.value = t.sample
            recalc()
            _options.value = PaperOptions(PaperSize.of(t.paper), false, ui.paperMarginMm, ui.paperScalePct)
        }
    }

    fun setVar(k: String, v: String) { _data.update { it.copy(vars = it.vars + (k to v)) }; recalc() }
    fun setList(s: String, rows: List<Map<String, String>>) { _data.update { it.copy(lists = it.lists + (s to rows)) }; recalc() }
    fun options(change: (PaperOptions) -> PaperOptions) { _options.update(change); _page.value = 0 }
    fun page(i: Int) { _page.value = i }
    fun clearMessage() { _message.value = null }

    suspend fun print(activityContext: Context) {
        val t = _template.value ?: return
        try { docs.print(activityContext, t, _data.value, _options.value) } catch (e: Exception) { _message.value = "Could not open the print dialog: ${e.message}" }
    }

    fun export(uri: Uri) {
        val t = _template.value ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val l = docs.layout(t, _data.value, _options.value)
                    context.contentResolver.openOutputStream(uri)!!.use { docs.writePdf(l, it) }
                }.isSuccess
            }
            _message.value = if (ok) "PDF saved." else "Could not save the PDF."
        }
    }
}
