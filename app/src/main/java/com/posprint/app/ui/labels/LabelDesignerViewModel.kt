package com.posprint.app.ui.labels

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.TemplateRepository
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.data.render.LabelRaster
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LBox
import com.posprint.app.domain.template.LImage
import com.posprint.app.domain.template.LLine
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

val LABEL_PRESETS = listOf(
    "40 × 20 mm" to (40.0 to 20.0), "50 × 30 mm" to (50.0 to 30.0), "60 × 40 mm" to (60.0 to 40.0),
    "70 × 40 mm" to (70.0 to 40.0), "100 × 50 mm" to (100.0 to 50.0), "100 × 100 mm" to (100.0 to 100.0),
    "100 × 150 mm (shipping)" to (100.0 to 150.0),
)

@HiltViewModel
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LabelDesignerViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repo: TemplateRepository,
    printerRepo: PrinterRepository,
    private val tp: TemplatePrinter,
    val assets: AndroidRenderAssets,
    @com.posprint.app.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    private val id: Long = handle["id"] ?: 0L
    /** Created by "New label": leaving without ever saving deletes it, so blanks do not pile up in Templates. */
    private val fresh: Boolean = handle["fresh"] ?: false
    private var everSaved = false

    private val _template = MutableStateFlow<Template?>(null)
    val template: StateFlow<Template?> = _template.asStateFlow()
    private val saved = MutableStateFlow<Template?>(null)
    val dirty: StateFlow<Boolean> = combine(_template, saved) { t, s -> t != null && t != s }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()
    private val _snap = MutableStateFlow(true)
    val snap: StateFlow<Boolean> = _snap.asStateFlow()

    val printers: StateFlow<List<Printer>> =
        printerRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The label as it prints, drawn by the same code the printer path uses. */
    val preview: StateFlow<ImageBitmap?> = _template.debounce(40).mapLatest { t ->
        if (t == null) null else {
            val ctx = tp.renderContext(32)
            val els = LabelPlanner.resolve(t.elements, t.sample.vars, ctx)
            LabelRaster.render(t.label, els, assets, 2f).asImageBitmap()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init { viewModelScope.launch { repo.get(id)?.let { saved.value = it; _template.value = it } } }

    fun select(id: String?) { _selected.value = id }
    fun setSnap(on: Boolean) { _snap.value = on }
    fun edit(change: (Template) -> Template) = _template.update { it?.let(change) }
    fun setSpec(change: (LabelSpec) -> LabelSpec) = edit { it.copy(label = change(it.label)) }

    fun update(el: LabelElement) = edit { t -> t.copy(elements = t.elements.map { if (it.id == el.id) el else it }) }
    fun remove(id: String) { edit { t -> t.copy(elements = t.elements.filterNot { it.id == id }) }; if (_selected.value == id) _selected.value = null }
    fun duplicate(id: String) {
        val t = _template.value ?: return
        val src = t.elements.firstOrNull { it.id == id } ?: return
        val copy = src.moved(src.xMm + 2, src.yMm + 2).withId(newId())
        edit { it.copy(elements = it.elements + copy) }
        _selected.value = copy.id
    }

    fun rotate(id: String) {
        val el = _template.value?.elements?.firstOrNull { it.id == id } ?: return
        // The box is the occupied area, so a quarter turn swaps its width and height.
        update(el.moved(el.xMm, el.yMm, el.hMm, el.wMm, (el.rotation + 90) % 360))
    }

    fun bringToFront(id: String) = edit { t ->
        val e = t.elements.firstOrNull { it.id == id } ?: return@edit t
        t.copy(elements = t.elements.filterNot { it.id == id } + e)
    }

    fun add(kind: String) {
        val t = _template.value ?: return
        val w = t.label.widthMm
        val h = t.label.heightMm
        val i = newId()
        val el: LabelElement = when (kind) {
            "text" -> LText(i, 2.0, 2.0, minOf(30.0, w - 4), 6.0, 0, "Text", false, Alignment.LEFT, 4.0)
            "barcode" -> LBarcode(i, 2.0, 2.0, w - 4, minOf(12.0, h - 4), 0, "{{sku}}", BarcodeType.CODE128, true)
            "qr" -> LQr(i, 2.0, 2.0, minOf(12.0, h - 4), minOf(12.0, h - 4), 0, "{{sku}}")
            "image" -> LImage(i, 2.0, 2.0, 15.0, 15.0, 0, "logo")
            "line" -> LLine(i, 2.0, h / 2, w - 4, 0.4, 0, 0.4)
            else -> LBox(i, 1.0, 1.0, w - 2, h - 2, 0, 0.4)
        }
        edit { it.copy(elements = it.elements + el) }
        _selected.value = i
    }

    suspend fun pickImage(uri: Uri): String? = kotlinx.coroutines.withContext(Dispatchers.IO) { assets.toDataUri(uri) }

    fun save(onDone: () -> Unit = {}) {
        val t = _template.value ?: return
        viewModelScope.launch { repo.save(t); saved.value = t; everSaved = true; onDone() }
    }

    fun discardIfFresh() {
        if (fresh && !everSaved) appScope.launch { repo.delete(id) }
    }

    private fun newId() = UUID.randomUUID().toString().take(8)

    private fun LabelElement.withId(newId: String): LabelElement = when (this) {
        is LText -> copy(id = newId); is LBarcode -> copy(id = newId); is LQr -> copy(id = newId)
        is LImage -> copy(id = newId); is LLine -> copy(id = newId); is LBox -> copy(id = newId)
    }
}
