package com.posprint.app.ui.labels

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.LabelSpec
import kotlin.math.roundToInt

private const val GRID_MM = 1.0
private const val MIN_MM = 1.0

/**
 * Drag to move, drag the corner handle to resize, tap to select. The picture comes from the real
 * label renderer; the overlay only shows boxes and handles.
 */
@Composable
fun LabelCanvas(
    spec: LabelSpec,
    elements: List<LabelElement>,
    bitmap: ImageBitmap?,
    selectedId: String?,
    snap: Boolean,
    onSelect: (String?) -> Unit,
    onChange: (LabelElement) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val availW = maxWidth
        // Fit the label in the available width, never taller than 360 dp.
        val ratio = (spec.heightMm / spec.widthMm).toFloat()
        val wDp = minOf(availW, (360.dp / ratio.coerceAtLeast(0.01f)))
        val pxPerMm = with(density) { wDp.toPx() } / spec.widthMm.toFloat()
        val wPx = (spec.widthMm * pxPerMm).toFloat()
        val hPx = (spec.heightMm * pxPerMm).toFloat()
        val handlePx = with(density) { 14.dp.toPx() }
        val touchSlop = with(density) { 10.dp.toPx() }

        val els by rememberUpdatedState(elements)
        val sel by rememberUpdatedState(selectedId)
        val snapOn by rememberUpdatedState(snap)
        val primary = MaterialTheme.colorScheme.primary

        fun snapMm(v: Double) = if (snapOn) (v / GRID_MM).roundToInt() * GRID_MM else (v * 10).roundToInt() / 10.0
        fun hit(p: Offset): LabelElement? = els.lastOrNull {
            p.x >= it.xMm * pxPerMm - touchSlop && p.x <= (it.xMm + it.wMm) * pxPerMm + touchSlop &&
                p.y >= it.yMm * pxPerMm - touchSlop && p.y <= (it.yMm + it.hMm) * pxPerMm + touchSlop
        }
        fun nearHandle(e: LabelElement, p: Offset): Boolean {
            val hx = ((e.xMm + e.wMm) * pxPerMm).toFloat()
            val hy = ((e.yMm + e.hMm) * pxPerMm).toFloat()
            return kotlin.math.abs(p.x - hx) < handlePx * 1.6f && kotlin.math.abs(p.y - hy) < handlePx * 1.6f
        }

        val hDp = with(density) { hPx.toDp() }
        val widthDp = with(density) { wPx.toDp() }
        Canvas(
            Modifier.size(widthDp, hDp)
                .background(Color.White)
                .border(1.dp, Color(0x33000000))
                .semantics { contentDescription = "Label canvas ${spec.widthMm} by ${spec.heightMm} millimetres" }
                .pointerInput(pxPerMm, spec) { detectTapGestures { onSelect(hit(it)?.id) } }
                .pointerInput(pxPerMm, spec) { // re-key: gestures must use the current scale and label bounds
                    var mode = 0 // 0 none, 1 move, 2 resize
                    var start: LabelElement? = null
                    var acc = Offset.Zero
                    detectDragGestures(
                        onDragStart = { p ->
                            acc = Offset.Zero
                            val s = els.firstOrNull { it.id == sel }
                            if (s != null && nearHandle(s, p)) { mode = 2; start = s }
                            else hit(p)?.let { onSelect(it.id); mode = 1; start = it } ?: run { mode = 0; start = null }
                        },
                        onDrag = { change, delta ->
                            val e = start ?: return@detectDragGestures
                            change.consume()
                            acc += delta
                            val dx = acc.x / pxPerMm
                            val dy = acc.y / pxPerMm
                            if (mode == 1) {
                                val nx = snapMm(e.xMm + dx).coerceIn(0.0, (spec.widthMm - e.wMm).coerceAtLeast(0.0))
                                val ny = snapMm(e.yMm + dy).coerceIn(0.0, (spec.heightMm - e.hMm).coerceAtLeast(0.0))
                                onChange(e.moved(nx, ny))
                            } else if (mode == 2) {
                                val nw = snapMm(e.wMm + dx).coerceIn(MIN_MM, spec.widthMm - e.xMm)
                                val nh = snapMm(e.hMm + dy).coerceIn(MIN_MM, spec.heightMm - e.yMm)
                                onChange(e.moved(e.xMm, e.yMm, nw, nh))
                            }
                        },
                        onDragEnd = { mode = 0; start = null },
                        onDragCancel = { mode = 0; start = null },
                    )
                },
        ) {
            bitmap?.let {
                drawImage(it, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), dstOffset = IntOffset.Zero, filterQuality = FilterQuality.None)
            }
            if (snapOn) {
                var gx = 0.0
                while (gx <= spec.widthMm) {
                    var gy = 0.0
                    while (gy <= spec.heightMm) {
                        drawCircle(Color(0x22000000), if (gx % 5.0 == 0.0 && gy % 5.0 == 0.0) 2.2f else 1f, Offset((gx * pxPerMm).toFloat(), (gy * pxPerMm).toFloat()))
                        gy += GRID_MM
                    }
                    gx += GRID_MM
                }
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            els.forEach { e ->
                val topLeft = Offset((e.xMm * pxPerMm).toFloat(), (e.yMm * pxPerMm).toFloat())
                val sz = Size((e.wMm * pxPerMm).toFloat(), (e.hMm * pxPerMm).toFloat())
                if (e.id == sel) {
                    drawRect(primary, topLeft, sz, style = Stroke(3f))
                    drawRect(primary, Offset(topLeft.x + sz.width - handlePx / 2, topLeft.y + sz.height - handlePx / 2), Size(handlePx, handlePx))
                } else {
                    drawRect(Color(0x55888888), topLeft, sz, style = Stroke(1.5f, pathEffect = dash))
                }
            }
        }
    }
}
