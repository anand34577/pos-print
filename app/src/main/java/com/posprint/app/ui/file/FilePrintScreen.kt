package com.posprint.app.ui.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.PrintJob
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.render.Dither
import com.posprint.app.domain.render.MonoBitmap
import com.posprint.app.domain.render.MonoOps
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.ui.common.ChipRow
import com.posprint.app.ui.common.SectionTitle
import com.posprint.app.ui.components.JobRow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class FileState(
    val isPdf: Boolean = false,
    val pageCount: Int = 1,
    val selected: Set<Int> = setOf(0),
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    val threshold: Int = 128,
    val previewPage: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class FilePrintViewModel @Inject constructor(
    handle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val printers: PrinterRepository,
    private val tp: TemplatePrinter,
    private val print: PrintService,
) : ViewModel() {
    private val uri: Uri = Uri.parse(handle["uri"] ?: "")
    private val mime: String = handle["mime"] ?: ""

    private val _s = MutableStateFlow(FileState(isPdf = mime.contains("pdf")))
    val state: StateFlow<FileState> = _s.asStateFlow()
    private val jobId = MutableStateFlow<Long?>(null)
    val job: StateFlow<PrintJob?> = combine(jobId, print.jobs) { id, jobs -> jobs.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private val lock = Mutex()
    private var dots = 384

    init {
        viewModelScope.launch {
            dots = printers.observeDefault().first()?.paperWidth?.dots ?: 384
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (_s.value.isPdf) {
                        pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        renderer = PdfRenderer(pfd!!)
                        _s.update { it.copy(pageCount = renderer!!.pageCount) }
                    }
                }.isSuccess
            }
            _s.update { it.copy(loading = false, error = if (ok) null else "Could not open this file. It may be damaged or password-protected.") }
        }
    }

    /** Page rendered to paper width in grayscale-on-white. */
    private suspend fun pageBitmap(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                if (_s.value.isPdf) {
                    renderer!!.openPage(index).use { p ->
                        val bmp = Bitmap.createBitmap(dots, (dots * p.height.toFloat() / p.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bmp
                    }
                } else {
                    val src = image ?: decodeImage().also { image = it }
                    val bmp = Bitmap.createBitmap(dots, (dots * src.height.toFloat() / src.width).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    Canvas(bmp).apply { drawColor(Color.WHITE); drawBitmap(Bitmap.createScaledBitmap(src, bmp.width, bmp.height, true), 0f, 0f, null) }
                    bmp
                }
            }.getOrNull()
        }
    }

    /** The picked image, decoded once: downsampled (a 50 MP photo would exhaust memory) and turned upright per EXIF. */
    private var image: Bitmap? = null

    private fun decodeImage(): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        // Keep at least twice the paper width so dithering still has detail to work with.
        while (bounds.outWidth / (sample * 2) >= dots * 2 && bounds.outHeight / (sample * 2) >= dots) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }!!
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)!!.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        }.getOrDefault(0)
        return if (degrees == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }

    private suspend fun mono(index: Int, s: FileState): MonoBitmap? =
        pageBitmap(index)?.let { Dither.toMono(AndroidRenderAssets.toGray(it), s.dither, s.threshold) }

    val preview: StateFlow<androidx.compose.ui.graphics.ImageBitmap?> = _s.mapLatest { s ->
        if (s.loading || s.error != null) return@mapLatest null
        mono(s.previewPage.coerceIn(0, s.pageCount - 1), s)?.let { m ->
            val px = IntArray(m.width * m.height) { i -> if (MonoOps.get(m, i % m.width, i / m.width)) Color.BLACK else Color.WHITE }
            Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(change: (FileState) -> FileState) = _s.update(change)
    fun toggle(i: Int) = _s.update { s -> if (i in s.selected) s.copy(selected = s.selected - i, previewPage = i) else s.copy(selected = s.selected + i, previewPage = i) }
    fun retry() { jobId.value?.let(print::retry) }
    fun cancel() { jobId.value?.let(print::cancel) }

    fun print() {
        val s = _s.value
        if (s.selected.isEmpty()) { _s.update { it.copy(error = "Select at least one page.") }; return }
        viewModelScope.launch {
            try {
                val p = printers.observeDefault().first() ?: throw PrinterException(PrintError.NoPrinter())
                val pages = s.selected.sorted().mapNotNull { mono(it, s) }
                jobId.value = tp.printRasters(if (s.isPdf) "PDF (${pages.size} page${if (pages.size > 1) "s" else ""})" else "Image", pages, p)
                _s.update { it.copy(error = null) }
            } catch (e: PrinterException) { _s.update { it.copy(error = e.error.message) } }
        }
    }

    override fun onCleared() { runCatching { renderer?.close() }; runCatching { pfd?.close() } }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FilePrintScreen(onBack: () -> Unit, vm: FilePrintViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val job by vm.job.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(if (s.isPdf) "Print PDF" else "Print image") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.loading) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            preview?.let {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Image(it, "Preview of the printed page", Modifier.fillMaxWidth(0.8f).background(ComposeColor.White).border(1.dp, ComposeColor(0x33000000)), contentScale = ContentScale.FillWidth)
                }
            }
            if (s.isPdf && s.pageCount > 1) {
                SectionTitle("Pages")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(s.pageCount) { i -> FilterChip(i in s.selected, { vm.toggle(i) }, label = { Text("${i + 1}") }) }
                }
            }
            ChipRow("Dithering", DitherMode.entries, s.dither, { it.label }) { d -> vm.update { it.copy(dither = d) } }
            Text("Threshold ${s.threshold}", style = MaterialTheme.typography.labelLarge)
            Slider(s.threshold.toFloat(), { v -> vm.update { it.copy(threshold = v.toInt()) } }, valueRange = 40f..220f)
            Button({ vm.print() }, Modifier.fillMaxWidth().height(56.dp), enabled = !s.loading && s.error == null && s.selected.isNotEmpty()) {
                Icon(Icons.Rounded.Print, null)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                Text(if (s.isPdf && s.selected.size > 1) "Print ${s.selected.size} pages" else "Print")
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            job?.let { JobRow(it, onRetry = vm::retry, onCancel = vm::cancel) }
        }
    }
}
