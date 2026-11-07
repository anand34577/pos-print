package com.posprint.app.data.print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
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
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeType

/** Page sizes in PostScript points (1/72 inch). */
enum class PaperSize(val label: String, val wPt: Float, val hPt: Float) {
    A4("A4", 595f, 842f), A5("A5", 420f, 595f), LETTER("Letter", 612f, 792f);

    companion object { fun of(name: String) = entries.firstOrNull { it.name.equals(name, true) } ?: A4 }
}

private sealed interface Op
private data class TextOp(val x: Float, val baseline: Float, val w: Float, val h: Float, val text: String, val pt: Float,
                          val bold: Boolean, val italic: Boolean, val underline: Boolean, val invert: Boolean) : Op
private data class BitmapOp(val x: Float, val y: Float, val w: Float, val h: Float, val bmp: Bitmap) : Op
private data class CodeOp(val x: Float, val y: Float, val w: Float, val h: Float, val m: BitMatrix, val square: Boolean, val caption: String?, val pt: Float) : Op

/** Paginated drawing instructions: the same resolved blocks as the thermal preview, laid out on a sheet. */
class DocumentLayout private constructor(val wPt: Float, val hPt: Float, private val pages: List<List<Op>>) {
    val pageCount: Int get() = pages.size

    fun draw(canvas: Canvas, index: Int) {
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        for (op in pages[index]) when (op) {
            is TextOp -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = op.pt
                    typeface = Typeface.create(Typeface.MONOSPACE, when { op.bold && op.italic -> Typeface.BOLD_ITALIC; op.bold -> Typeface.BOLD; op.italic -> Typeface.ITALIC; else -> Typeface.NORMAL })
                    isUnderlineText = op.underline
                    color = if (op.invert) Color.WHITE else Color.BLACK
                }
                if (op.invert) canvas.drawRect(op.x - 1, op.baseline - op.pt, op.x + op.w + 1, op.baseline + op.pt * 0.3f, ink)
                canvas.drawText(op.text, op.x, op.baseline, p)
            }
            is BitmapOp -> canvas.drawBitmap(op.bmp, null, android.graphics.RectF(op.x, op.y, op.x + op.w, op.y + op.h), Paint(Paint.FILTER_BITMAP_FLAG))
            is CodeOp -> {
                val cw = op.w / op.m.width
                val ch = if (op.square) op.h / op.m.height else op.h
                for (y in 0 until op.m.height) for (x in 0 until op.m.width) if (op.m.get(x, y)) {
                    canvas.drawRect(op.x + x * cw, op.y + y * ch, op.x + (x + 1) * cw + 0.3f, op.y + (y + 1) * ch + 0.3f, ink)
                }
                op.caption?.let {
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = op.pt; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
                    canvas.drawText(it, op.x + op.w / 2, op.y + op.h + op.pt, tp)
                }
            }
        }
    }

    fun renderBitmap(index: Int, scale: Float): Bitmap {
        val bmp = Bitmap.createBitmap((wPt * scale).toInt(), (hPt * scale).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        draw(c, index)
        return bmp
    }

    companion object {
        /** Characters per line that fit the page for a monospace font of [fontPt]. */
        fun colsFor(widthPt: Float, marginPt: Float, fontPt: Float) = ((widthPt - 2 * marginPt) / (0.6f * fontPt)).toInt().coerceAtLeast(20)

        fun build(blocks: List<RBlock>, wPt: Float, hPt: Float, marginPt: Float, fontPt: Float, assets: AndroidRenderAssets): DocumentLayout {
            val usableW = wPt - 2 * marginPt
            val bottom = hPt - marginPt
            val pages = mutableListOf<MutableList<Op>>()
            var ops = mutableListOf<Op>()
            var y = marginPt
            fun need(h: Float) {
                if (y + h > bottom && ops.isNotEmpty()) { pages += ops; ops = mutableListOf(); y = marginPt }
            }
            val measure = Paint().apply { typeface = Typeface.MONOSPACE }

            for (b in blocks) when (b) {
                is RText -> {
                    val pt = fontPt * b.widthMul.coerceAtLeast(1)
                    val lineH = pt * 1.2f * (b.heightMul.coerceAtLeast(1).toFloat() / b.widthMul.coerceAtLeast(1)).coerceAtLeast(1f)
                    need(lineH)
                    measure.textSize = pt
                    val tw = measure.measureText(b.text)
                    val x = marginPt + when (b.align) { Alignment.LEFT -> 0f; Alignment.CENTER -> (usableW - tw) / 2; Alignment.RIGHT -> usableW - tw }.coerceAtLeast(0f)
                    ops += TextOp(x, y + pt, tw, lineH, b.text, pt, b.bold, b.italic, b.underline, b.invert)
                    y += lineH
                }
                is RSpacer -> y += fontPt * 1.2f * b.lines
                is RImage -> assets.loadBitmap(b.source)?.let { bmp ->
                    val w = usableW * b.widthPct.coerceIn(5, 100) / 100f
                    val h = w * bmp.height / bmp.width
                    need(h)
                    val x = marginPt + when (b.align) { Alignment.LEFT -> 0f; Alignment.CENTER -> (usableW - w) / 2; Alignment.RIGHT -> usableW - w }
                    ops += BitmapOp(x, y, w, h, bmp)
                    y += h + 4
                }
                is RQr -> runCatching { BarcodeMatrix.qr(b.data) }.getOrNull()?.let { m ->
                    val s = minOf(usableW * 0.4f, 40f + b.size * 8f)
                    need(s)
                    ops += CodeOp(marginPt + align(b.align, usableW, s), y, s, s, m, true, null, fontPt)
                    y += s + 6
                }
                is RBarcode -> if (BarcodeMatrix.validate(b.type, b.data) == null) {
                    val m = BarcodeMatrix.oneD(b.type, b.data)
                    val w = usableW * if (b.type == BarcodeType.PDF417) 0.6f else 0.45f
                    val h = (b.height * 0.5f).coerceAtLeast(24f)
                    need(h + fontPt * 2)
                    ops += CodeOp(marginPt + align(b.align, usableW, w), y, w, h, m, b.type == BarcodeType.PDF417, if (b.type == BarcodeType.PDF417) null else b.data, fontPt)
                    y += h + fontPt * 2
                }
                is RCommand -> Unit // cut / drawer / beep mean nothing on paper
            }
            if (ops.isNotEmpty() || pages.isEmpty()) pages += ops
            return DocumentLayout(wPt, hPt, pages)
        }

        private fun align(a: Alignment, avail: Float, w: Float) = when (a) { Alignment.LEFT -> 0f; Alignment.CENTER -> (avail - w) / 2; Alignment.RIGHT -> avail - w }
    }
}
