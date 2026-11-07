package com.posprint.app.ui.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.common.BitMatrix
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.render.BarcodeMatrix
import com.posprint.app.domain.render.RBarcode
import com.posprint.app.domain.render.RBlock
import com.posprint.app.domain.render.RCommand
import com.posprint.app.domain.render.RImage
import com.posprint.app.domain.render.RQr
import com.posprint.app.domain.render.RSpacer
import com.posprint.app.domain.render.RText
import com.posprint.app.domain.template.Alignment as Al
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.CommandKind

private val Paper = Color(0xFFFFFEFA)
private val Ink = Color(0xFF1A1A1A)

/**
 * Shows exactly what the printer receives: the same resolved blocks, on a paper-coloured sheet
 * sized to [cols] monospace characters. Used by editors and the print screens.
 */
@Composable
fun ReceiptPreview(blocks: List<RBlock>, cols: Int, assets: AndroidRenderAssets, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val paperWidth = if (maxWidth > 380.dp) 380.dp else maxWidth
        val density = LocalDensity.current
        // A monospace glyph is ~0.6 em wide, so this size makes exactly `cols` characters span the paper.
        val base = with(density) { ((paperWidth - 24.dp).toPx() / (cols * 0.6f)).toSp() }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.width(paperWidth)
                    .background(Paper, RoundedCornerShape(4.dp))
                    .border(1.dp, Color(0x22000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .semantics { contentDescription = "Print preview" },
            ) {
                if (blocks.isEmpty()) Text("Nothing to print yet", color = Ink.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
                blocks.forEach { b -> Block(b, base, assets) }
            }
        }
    }
}

@Composable
private fun Block(b: RBlock, base: androidx.compose.ui.unit.TextUnit, assets: AndroidRenderAssets) {
    when (b) {
        is RText -> {
            val mul = maxOf(b.widthMul, 1)
            val style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = base * mul,
                lineHeight = base * 1.25f * maxOf(b.heightMul, 1),
                fontWeight = if (b.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (b.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (b.underline) TextDecoration.Underline else null,
                color = if (b.invert) Paper else Ink,
                textAlign = when (b.align) { Al.LEFT -> TextAlign.Start; Al.CENTER -> TextAlign.Center; Al.RIGHT -> TextAlign.End },
            )
            Text(
                b.text.ifEmpty { " " }, style = style, softWrap = false,
                modifier = Modifier.fillMaxWidth().then(if (b.invert) Modifier.background(Ink) else Modifier),
            )
        }
        is RImage -> {
            val bmp = remember(b.source) { assets.loadBitmap(b.source) }
            if (bmp != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = b.align.box()) {
                    Image(
                        bmp.asImageBitmap(), "Image", Modifier.fillMaxWidth(b.widthPct / 100f).padding(vertical = 4.dp),
                    )
                }
            }
        }
        is RQr -> Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = b.align.box()) {
            val m = remember(b.data) { runCatching { BarcodeMatrix.qr(b.data) }.getOrNull() }
            if (m != null) CodeCanvas(m, Modifier.height((40 + b.size * 8).dp).aspectSquare()) else Invalid("QR")
        }
        is RBarcode -> Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalAlignment = when (b.align) {
            Al.LEFT -> Alignment.Start; Al.CENTER -> Alignment.CenterHorizontally; Al.RIGHT -> Alignment.End
        }) {
            val err = remember(b.type, b.data) { BarcodeMatrix.validate(b.type, b.data) }
            if (err != null) Invalid(err) else {
                val m = remember(b.type, b.data) { BarcodeMatrix.oneD(b.type, b.data) }
                val tall = b.type == BarcodeType.PDF417
                CodeCanvas(m, Modifier.fillMaxWidth(if (tall) 0.8f else 0.7f).height((b.height * 0.6f).dp), stretchRows = !tall)
                if (!tall) Text(b.data, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = base), color = Ink)
            }
        }
        is RSpacer -> androidx.compose.foundation.layout.Spacer(Modifier.height(with(LocalDensity.current) { (base * 1.25f * b.lines).toDp() }))
        is RCommand -> Text(
            when (b.kind) { CommandKind.CUT -> "- - cut - -"; CommandKind.DRAWER -> "[ cash drawer opens ]"; CommandKind.BEEP -> "[ beep ]" },
            style = MaterialTheme.typography.labelSmall, color = Ink.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), textAlign = TextAlign.Center,
        )
    }
}

private fun Al.box() = when (this) { Al.LEFT -> Alignment.CenterStart; Al.CENTER -> Alignment.Center; Al.RIGHT -> Alignment.CenterEnd }
private fun Modifier.aspectSquare() = this.aspectRatio(1f)

@Composable
private fun Invalid(msg: String) =
    Text("⚠ $msg", color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(4.dp))

@Composable
fun CodeCanvas(m: BitMatrix, modifier: Modifier = Modifier, stretchRows: Boolean = true) {
    Canvas(modifier) {
        val cw = size.width / m.width
        val ch = if (stretchRows && m.height == 1) size.height else size.height / m.height
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) {
            drawRect(Ink, Offset(x * cw, y * ch), Size(cw + 0.5f, ch + 0.5f))
        }
    }
}
