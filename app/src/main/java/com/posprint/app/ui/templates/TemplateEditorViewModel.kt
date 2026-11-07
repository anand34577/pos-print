package com.posprint.app.ui.templates

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.RBlock
import com.posprint.app.domain.template.Block
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
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

data class PreviewState(val blocks: List<RBlock> = emptyList(), val cols: Int = 32)

const val DOC_COLS = 76

@HiltViewModel
class TemplateEditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: TemplateRepository,
    printerRepo: PrinterRepository,
    private val tp: TemplatePrinter,
    val assets: AndroidRenderAssets,
    @com.posprint.app.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {

    private val id: Long = handle["id"] ?: 0L
    /** Just created from "New template": leaving without ever saving discards it instead of leaving a stray blank. */
    private val fresh: Boolean = handle["fresh"] ?: false
    private var everSaved = false

    private val _template = MutableStateFlow<Template?>(null)
    val template: StateFlow<Template?> = _template.asStateFlow()
    private val saved = MutableStateFlow<Template?>(null)

    val dirty: StateFlow<Boolean> = combine(_template, saved) { t, s -> t != null && t != s }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val printers: StateFlow<List<Printer>> =
        printerRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val preview: StateFlow<PreviewState> = combine(_template, printers) { t, p -> t to p }
        .flatMapLatest { (t, plist) ->
            flow {
                if (t == null || t.kind == TemplateKind.LABEL) { emit(PreviewState()); return@flow }
                val cols = if (t.kind == TemplateKind.DOCUMENT) DOC_COLS
                else (plist.firstOrNull { it.id == t.printerId } ?: plist.firstOrNull { it.isDefault } ?: plist.firstOrNull())?.cols ?: 32
                emit(PreviewState(tp.resolve(t.blocks, t.sample, cols), cols))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewState())

    init {
        viewModelScope.launch {
            repo.get(id)?.let { saved.value = it; _template.value = it }
        }
    }

    fun edit(change: (Template) -> Template) = _template.update { it?.let(change) }

    fun editBlocks(change: (List<Block>) -> List<Block>) = edit { it.copy(blocks = change(it.blocks)) }

    fun move(index: Int, delta: Int) = editBlocks { l ->
        val to = index + delta
        if (to !in l.indices) l else l.toMutableList().also { it.add(to, it.removeAt(index)) }
    }

    fun remove(index: Int) = editBlocks { l -> l.toMutableList().also { it.removeAt(index) } }
    fun replace(index: Int, b: Block) = editBlocks { l -> l.toMutableList().also { it[index] = b } }
    fun add(b: Block) = editBlocks { it + b }

    fun setVar(name: String, value: String) = edit { it.copy(sample = it.sample.copy(vars = it.sample.vars + (name to value))) }
    fun setList(source: String, rows: List<Map<String, String>>) =
        edit { it.copy(sample = it.sample.copy(lists = it.sample.lists + (source to rows))) }

    fun save(onDone: (Template) -> Unit = {}) {
        val t = _template.value ?: return
        viewModelScope.launch {
            repo.save(t)
            saved.value = t
            everSaved = true
            onDone(t)
        }
    }

    /** Called when leaving without saving. Runs on the app scope so it survives this ViewModel being cleared. */
    fun discardIfFresh() {
        if (fresh && !everSaved) appScope.launch { repo.delete(id) }
    }

    suspend fun pickImage(uri: android.net.Uri): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { assets.toDataUri(uri) }
}
