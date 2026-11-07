package com.posprint.app.data.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.common.BitMatrix
import com.posprint.app.domain.render.BarcodeMatrix
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

/** The single label drawing routine: designer preview and print text both come from here. */
object LabelRaster {

    fun textBox(text: String, bold: Boolean, align: Alignment, fontMm: Double, w: Int, h: Int, dpi: Int): Bitmap {
        val px = (fontMm * dpi / 25.4).toFloat().coerceAtLeast(6f)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = px
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val la = when (align) {
            Alignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            Alignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, w.coerceAtLeast(1)).setAlignment(la).setLineSpacing(0f, 0.95f).build()
        c.clipRect(0, 0, w, h)
        layout.draw(c)
        return bmp
    }

    /** Draws the label as it will print. [scale] is pixels per dot. */
    fun render(spec: LabelSpec, elements: List<LabelElement>, assets: AndroidRenderAssets, scale: Float = 1f): Bitmap {
        val dpi = spec.dpi
        val wd = LabelPlanner.dots(spec.widthMm, dpi).coerceAtLeast(1)
        val hd = LabelPlanner.dots(spec.heightMm, dpi).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap((wd * scale).toInt().coerceAtLeast(1), (hd * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for (e in elements) {
            val x = LabelPlanner.dots(e.xMm, dpi)
            val y = LabelPlanner.dots(e.yMm, dpi)
            val w = LabelPlanner.dots(e.wMm, dpi).coerceAtLeast(1)
            val h = LabelPlanner.dots(e.hMm, dpi).coerceAtLeast(1)
            val rot = ((e.rotation % 360) + 360) % 360
            val sideways = rot == 90 || rot == 270
            val cw = if (sideways) h else w
            val ch = if (sideways) w else h
            when (e) {
                is LText -> {
                    val g = assets.labelText(e.text, e.bold, e.align, e.fontMm, cw, ch, rot, dpi)
                    c.drawBitmap(grayToBitmap(g), x.toFloat(), y.toFloat(), null)
                }
                is LImage -> assets.labelImage(e.source, cw, ch, rot)?.let { c.drawBitmap(grayToBitmap(it), x.toFloat(), y.toFloat(), null) }
                is LLine -> {
                    val t = LabelPlanner.dots(e.thicknessMm, dpi).coerceAtLeast(1)
                    if (sideways) c.drawRect(x.toFloat(), y.toFloat(), (x + t).toFloat(), (y + h).toFloat(), black)
                    else c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + t).toFloat(), black)
                }
                is LBox -> {
                    val t = LabelPlanner.dots(e.thicknessMm, dpi).coerceAtLeast(1).toFloat()
                    val p = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = t }
                    c.drawRect(x + t / 2, y + t / 2, x + w - t / 2, y + h - t / 2, p)
                }
                is LQr -> if (BarcodeMatrix.validateQr(e.data) == null) drawCode(c, BarcodeMatrix.qr(e.data), x, y, w, h, rot, true, black)
                    else drawInvalid(c, x, y, w, h)
                is LBarcode -> if (BarcodeMatrix.validate(e.type, e.data) == null) {
                    val m = BarcodeMatrix.oneD(e.type, e.data)
                    if (e.type == BarcodeType.PDF417) {
                        // Same stretched geometry the printer path uses (LabelPlanner.pdfMono), so preview matches print.
                        val mono = com.posprint.app.domain.render.MonoOps.rotate(LabelPlanner.pdfMono(m, cw, ch), rot)
                        c.drawBitmap(monoToBitmap(mono), x.toFloat(), y.toFloat(), null)
                    } else {
                        drawCode(c, m, x, y, w, h, rot, false, black, if (e.humanReadable) e.data else null)
                    }
                } else drawInvalid(c, x, y, w, h)
            }
        }
        return bmp
    }

    private fun drawInvalid(c: Canvas, x: Int, y: Int, w: Int, h: Int) {
        val p = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f, 4f), 0f) }
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
        c.drawLine(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }

    private fun drawCode(c: Canvas, m: BitMatrix, x: Int, y: Int, w: Int, h: Int, rot: Int, square2d: Boolean, paint: Paint, human: String? = null) {
        val sideways = rot == 90 || rot == 270
        val cw = if (sideways) h else w
        val ch = if (sideways) w else h
        val textH = if (human != null) 24 else 0
        val bar = (ch - textH).coerceAtLeast(4)
        c.save()
        c.translate(x.toFloat(), y.toFloat())
        when (rot) { 90 -> { c.translate(w.toFloat(), 0f); c.rotate(90f) }; 180 -> { c.translate(w.toFloat(), h.toFloat()); c.rotate(180f) }; 270 -> { c.translate(0f, h.toFloat()); c.rotate(270f) } }
        if (square2d) {
            val cell = maxOf(1, minOf(cw, ch) / m.width)
            val ox = (cw - cell * m.width) / 2
            val oy = (ch - cell * m.height) / 2
            for (yy in 0 until m.height) for (xx in 0 until m.width) if (m.get(xx, yy)) {
                c.drawRect((ox + xx * cell).toFloat(), (oy + yy * cell).toFloat(), (ox + (xx + 1) * cell).toFloat(), (oy + (yy + 1) * cell).toFloat(), paint)
            }
        } else {
            val narrow = maxOf(1, cw / m.width)
            val ox = (cw - narrow * m.width) / 2
            for (xx in 0 until m.width) if (m.get(xx, 0)) c.drawRect((ox + xx * narrow).toFloat(), 0f, (ox + (xx + 1) * narrow).toFloat(), bar.toFloat(), paint)
            if (human != null) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 20f; textAlign = Paint.Align.CENTER }
                c.drawText(human, cw / 2f, ch - 4f, tp)
            }
        }
        c.restore()
    }

    private fun monoToBitmap(m: com.posprint.app.domain.render.MonoBitmap): Bitmap {
        val px = IntArray(m.width * m.height) { i ->
            if (com.posprint.app.domain.render.MonoOps.get(m, i % m.width, i / m.width)) Color.BLACK else Color.TRANSPARENT
        }
        return Bitmap.createBitmap(px, m.width.coerceAtLeast(1), m.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    }

    fun grayToBitmap(g: com.posprint.app.domain.render.GrayImage): Bitmap {
        val px = IntArray(g.pixels.size) { val v = g.pixels[it].toInt() and 0xFF; Color.rgb(v, v, v) }
        return Bitmap.createBitmap(px, g.width, g.height, Bitmap.Config.ARGB_8888)
    }
}
