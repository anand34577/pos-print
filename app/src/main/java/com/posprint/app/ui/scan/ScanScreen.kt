package com.posprint.app.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QrCodeScanner
import com.posprint.app.ui.common.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.posprint.app.ui.common.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.posprint.app.data.TemplateRepository
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.ui.common.Dropdown
import com.posprint.app.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(repo: TemplateRepository) : ViewModel() {
    val labels: StateFlow<List<Template>> = repo.observeAll().map { l -> l.filter { it.kind == TemplateKind.LABEL } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Point the camera at a barcode or QR code, pick a label template, and print it with the scanned value. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(onBack: () -> Unit, onPrint: (templateId: Long, value: String) -> Unit, vm: ScanViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val labels by vm.labels.collectAsStateWithLifecycle()
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Template?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Scan to print") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!granted) {
                EmptyState(
                    Icons.Rounded.QrCodeScanner, "Camera access needed",
                    if (denied) "Camera permission was denied. If Android no longer asks, allow it in app settings."
                    else "Allow the camera to scan barcodes and QR codes. Nothing is recorded or uploaded.",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                        // After "Don't ask again" the system dialog never shows, so the only way back is app settings.
                        if (denied) OutlinedButton({
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text("Open app settings") }
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val r = result
                    if (r == null) {
                        CameraPreview { result = it }
                        Viewfinder()
                    } else {
                        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.height(48.dp).width(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(12.dp))
                            Text("Scanned", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (labels.isEmpty()) Text("Create a label template first (Templates tab).") else {
                            val sel = chosen?.takeIf { c -> labels.any { it.id == c.id } } ?: labels.first()
                            Dropdown("Label template", labels, sel, { it.name }) { chosen = it }
                            val r = result
                            if (r == null) Text("Point the camera at a barcode or QR code.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton({ result = null }, Modifier.weight(1f).height(52.dp)) { Text("Scan again") }
                                Button({ onPrint(sel.id, r) }, Modifier.weight(1f).height(52.dp)) { Text("Use this code") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dims the camera outside a centred window so people know where to aim. */
@Composable
private fun Viewfinder() {
    val accent = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val w = size.width * 0.78f
        val h = minOf(size.height * 0.45f, w * 0.75f)
        val tl = Offset((size.width - w) / 2, (size.height - h) / 2)
        val r = CornerRadius(24.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.Transparent, tl, Size(w, h), r, blendMode = BlendMode.Clear)
        drawRoundRect(accent, tl, Size(w, h), r, style = Stroke(3.dp.toPx()))
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val reader = remember {
        MultiFormatReader().apply {
            setHints(mapOf(
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.ITF, BarcodeFormat.PDF_417, BarcodeFormat.DATA_MATRIX,
                ),
            ))
        }
    }
    var done by remember { mutableStateOf(false) }
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(owner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(executor) { image -> analyze(image, reader)?.let { text -> if (!done) { done = true; view.post { onResult(text) } } } }
            runCatching {
                provider.unbindAll()
                preview.surfaceProvider = view.surfaceProvider
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() }; executor.shutdown() }
    }
    AndroidView({ view }, Modifier.fillMaxSize())
}

private fun decode(reader: MultiFormatReader, source: LuminanceSource): String? =
    try { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text } catch (e: Exception) { null } finally { reader.reset() }

private fun analyze(image: ImageProxy, reader: MultiFormatReader): String? = image.use {
    val plane = it.planes[0]
    val stride = plane.rowStride
    val w = it.width
    val h = it.height
    val data = ByteArray(plane.buffer.remaining()).also { b -> plane.buffer.get(b) }
    decode(reader, PlanarYUVLuminanceSource(data, stride, h, 0, 0, w, h, false)) ?: run {
        // The sensor frame is landscape, so a barcode held level in portrait arrives vertical. zxing's 1D readers
        // only scan rows and this source cannot rotate itself, so try again on a copy turned 90 degrees.
        val rotated = ByteArray(w * h)
        for (y in 0 until h) {
            val row = y * stride
            for (x in 0 until w) rotated[x * h + (h - 1 - y)] = data[row + x]
        }
        decode(reader, PlanarYUVLuminanceSource(rotated, h, w, 0, 0, h, w, false))
    }
}
