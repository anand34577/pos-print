package com.posprint.app.domain.render

import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LBox
import com.posprint.app.domain.template.LImage
import com.posprint.app.domain.template.LLine
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.LabelSpec
import kotlin.math.roundToInt

/** Language-neutral drawing primitives, in printer dots. Each label language only emits these. */
sealed interface LPrim
data class PBitmap(val x: Int, val y: Int, val mono: MonoBitmap) : LPrim
/** [filled] rectangles are lines/bars; otherwise an outline of [thickness] dots. */
data class PRect(val x: Int, val y: Int, val w: Int, val h: Int, val filled: Boolean, val thickness: Int) : LPrim
data class PBarcode(val x: Int, val y: Int, val type: BarcodeType, val data: String, val narrow: Int, val height: Int, val human: Boolean) : LPrim
data class PQr(val x: Int, val y: Int, val cell: Int, val data: String) : LPrim

interface LabelCompiler {
    fun compile(spec: LabelSpec, elements: List<LabelElement>, copies: Int, assets: RenderAssets): ByteArray
}

object LabelPlanner {
    private const val HUMAN_TEXT_DOTS = 24

    fun dots(mm: Double, dpi: Int) = (mm * dpi / 25.4).roundToInt()

    /** Elements whose text fields are already variable-substituted. */
    fun plan(spec: LabelSpec, elements: List<LabelElement>, assets: RenderAssets): List<LPrim> {
        val dpi = spec.dpi
        val out = mutableListOf<LPrim>()
        for (e in elements) {
            val x = dots(e.xMm, dpi)
            val y = dots(e.yMm, dpi)
            val w = dots(e.wMm, dpi).coerceAtLeast(1)
            val h = dots(e.hMm, dpi).coerceAtLeast(1)
            val rot = ((e.rotation % 360) + 360) % 360
            val sideways = rot == 90 || rot == 270
            val cw = if (sideways) h else w // content size before rotation
            val ch = if (sideways) w else h
            when (e) {
                is LText -> if (e.text.isNotBlank()) {
                    val img = assets.labelText(e.text, e.bold, e.align, e.fontMm, cw, ch, rot, dpi)
                    out += PBitmap(x, y, Dither.toMono(img, DitherMode.THRESHOLD))
                }
                is LImage -> assets.labelImage(e.source, cw, ch, rot)?.let { out += PBitmap(x, y, Dither.toMono(it, e.dither)) }
                is LLine -> {
                    val t = dots(e.thicknessMm, dpi).coerceAtLeast(1)
                    out += if (sideways) PRect(x, y, t, h, true, t) else PRect(x, y, w, t, true, t)
                }
                is LBox -> out += PRect(x, y, w, h, false, dots(e.thicknessMm, dpi).coerceAtLeast(1))
                is LQr -> if (e.data.isNotBlank() && BarcodeMatrix.validateQr(e.data) == null) {
                    val m = BarcodeMatrix.qr(e.data)
                    // Label languages are sent as single-byte text, so non-ASCII payloads are drawn as an image instead.
                    if (rot == 0 && e.data.all { it in ' '..'~' }) {
                        val cell = maxOf(1, minOf(w, h) / m.width)
                        val size = cell * m.width
                        out += PQr(x + (w - size) / 2, y + (h - size) / 2, cell, e.data)
                    } else {
                        out += PBitmap(x, y, MonoOps.rotate(BarcodeMatrix.toMono(m, cw, ch, square = true), rot))
                    }
                }
                is LBarcode -> if (BarcodeMatrix.validate(e.type, e.data) == null) {
                    val m = BarcodeMatrix.oneD(e.type, e.data)
                    if (rot == 0 && e.type != BarcodeType.PDF417) {
                        val narrow = maxOf(1, w / m.width)
                        val bar = (h - if (e.humanReadable) HUMAN_TEXT_DOTS else 0).coerceAtLeast(8)
                        out += PBarcode(x + (w - narrow * m.width) / 2, y, e.type, e.data, narrow, bar, e.humanReadable)
                    } else {
                        // Rotated barcodes (and PDF417) are printed as an image so geometry is identical in every language.
                        val isPdf = e.type == BarcodeType.PDF417
                        val mono = if (isPdf) pdfMono(m, cw, ch) else BarcodeMatrix.toMono(m, cw, ch, square = false)
                        out += PBitmap(x, y, MonoOps.rotate(mono, rot))
                    }
                }
            }
        }
        return out
    }

    fun pdfMono(m: com.google.zxing.common.BitMatrix, cw: Int, ch: Int): MonoBitmap {
        val sx = maxOf(1, cw / m.width)
        val sy = maxOf(1, ch / m.height)
        val rb = (cw + 7) / 8
        val data = ByteArray(rb * ch)
        for (y in 0 until minOf(m.height * sy, ch)) for (x in 0 until minOf(m.width * sx, cw)) {
            if (m.get(x / sx, y / sy)) data[y * rb + x / 8] = (data[y * rb + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
        }
        return MonoBitmap(cw, ch, data)
    }

    /** Replaces {{vars}} in element text fields. */
    fun resolve(elements: List<LabelElement>, vars: Map<String, String>, ctx: RenderContext = RenderContext(0)): List<LabelElement> {
        val lookup: (String) -> String? = { vars[it] ?: when (it) {
            "date" -> ctx.date()
            "time" -> ctx.time()
            "datetime" -> ctx.dateTime()
            else -> null
        } }
        return elements.map { e ->
            when (e) {
                is LText -> e.copy(text = TemplateEngine.substitute(e.text, lookup, ctx))
                is LBarcode -> e.copy(data = TemplateEngine.substitute(e.data, lookup, ctx))
                is LQr -> e.copy(data = TemplateEngine.substitute(e.data, lookup, ctx))
                is LImage -> e.copy(source = TemplateEngine.substitute(e.source, lookup, ctx))
                else -> e
            }
        }
    }

    private val VAR = Regex("\\{\\{\\s*([\\w.]+)\\s*(?:\\|\\s*\\w+)?\\s*\\}\\}")

    fun variablesUsed(elements: List<LabelElement>): List<String> {
        val names = linkedSetOf<String>()
        fun scan(s: String) = VAR.findAll(s).forEach { names += it.groupValues[1] }
        elements.forEach { e ->
            when (e) {
                is LText -> scan(e.text); is LBarcode -> scan(e.data); is LQr -> scan(e.data); is LImage -> scan(e.source)
                else -> Unit
            }
        }
        return names.filterNot { it in TemplateEngine.BUILTIN || it == "serial" }
    }
}
