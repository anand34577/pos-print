package com.posprint.app.data.print

import com.posprint.app.data.PrinterRepository
import com.posprint.app.data.SettingsStore
import com.posprint.app.data.printer.DriverRegistry
import com.posprint.app.data.printer.PrintService
import com.posprint.app.data.printer.escpos.Align
import com.posprint.app.data.printer.escpos.EscPosBuilder
import com.posprint.app.data.printer.escpos.ReceiptCompiler
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.data.render.LabelRaster
import com.posprint.app.domain.model.PrintError
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterException
import com.posprint.app.domain.render.Dither
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.render.RBlock
import com.posprint.app.domain.render.RenderContext
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.Block
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TemplateKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates "template + data + printer -> bytes -> queue". The only place that knows all the pieces. */
@Singleton
class TemplatePrinter @Inject constructor(
    private val printers: PrinterRepository,
    private val settings: SettingsStore,
    private val drivers: DriverRegistry,
    private val assets: AndroidRenderAssets,
    private val print: PrintService,
) {
    suspend fun renderContext(cols: Int): RenderContext {
        val p = settings.profileNow()
        return RenderContext(cols, p.business(), p.formatter())
    }

    /** Printer a template should use: its assigned one, else the default. */
    suspend fun printerFor(t: Template?): Printer {
        t?.printerId?.let { id -> printers.get(id)?.let { return it } }
        return printers.observeDefault().first()
            ?: throw PrinterException(PrintError.NoPrinter("Add a printer in the Printers tab."))
    }

    suspend fun resolve(blocks: List<Block>, data: TemplateData, cols: Int): List<RBlock> =
        TemplateEngine.resolve(blocks, data, renderContext(cols))

    // ----- receipts -----

    fun compileReceipt(printer: Printer, blocks: List<RBlock>): ByteArray {
        if (printer.language.isLabel) throw PrinterException(PrintError.Other("${printer.name} is a label printer. Choose a receipt (ESC/POS) printer for receipts."))
        return ReceiptCompiler(printer, assets).compile(blocks)
    }

    suspend fun printBlocks(title: String, blocks: List<Block>, data: TemplateData, printer: Printer): Long {
        val resolved = resolve(blocks, data, printer.cols)
        return print.enqueue(printer, title, compileReceipt(printer, resolved))
    }

    /** Prints already-resolved blocks [copies] times (quick print, mini POS). */
    suspend fun printResolved(title: String, blocks: List<RBlock>, printer: Printer, copies: Int = 1): Long {
        val once = compileReceipt(printer, blocks)
        val all = java.io.ByteArrayOutputStream().also { o -> repeat(copies.coerceAtLeast(1)) { o.write(once) } }.toByteArray()
        return print.enqueue(printer, title, all)
    }

    /** Image / PDF pages as raster graphics on a receipt printer. */
    fun printRasters(title: String, pages: List<com.posprint.app.domain.render.MonoBitmap>, printer: Printer): Long {
        if (printer.language.isLabel) throw PrinterException(PrintError.Other("Choose a receipt (ESC/POS) printer to print images and PDFs."))
        val b = EscPosBuilder(printer.cols, printer.codePage.charset).init().align(Align.CENTER)
        pages.forEach { b.raster(it).feed(1) }
        b.align(Align.LEFT)
        if (printer.hasCutter) b.feed(2).cut() else b.feed(4)
        return print.enqueue(printer, title, b.build())
    }

    suspend fun printTemplate(t: Template, data: TemplateData, printer: Printer? = null): Long {
        val p = printer ?: printerFor(t)
        return when (t.kind) {
            TemplateKind.LABEL -> printLabels(t, listOf(data.vars), 1, p)
            else -> printBlocks(t.name, t.blocks, data, p)
        }
    }

    // ----- labels -----

    /** Bytes for one label per row in [rows], each printed [copies] times. */
    suspend fun compileLabels(printer: Printer, t: Template, rows: List<Map<String, String>>, copies: Int, serial: Serial? = null): ByteArray {
        val ctx = renderContext(printer.cols)
        val out = java.io.ByteArrayOutputStream()
        val label = t.label.copy(dpi = printer.dpi)
        val driver = drivers.labelDriver(printer.language)
        rows.forEachIndexed { i, row ->
            val vars = if (serial != null) row + ("serial" to serial.at(i)) else row
            val rowCopies = row["qty"]?.toIntOrNull()?.coerceAtLeast(1) ?: copies
            val els = LabelPlanner.resolve(t.elements, vars, ctx)
            if (driver != null) {
                out.write(driver.compile(label, els, rowCopies, assets))
            } else {
                // Receipt (ESC/POS) printer used for labels: print the whole label as one raster image.
                repeat(rowCopies) { out.write(labelAsRaster(printer, label, els)) }
            }
        }
        return out.toByteArray()
    }

    private fun labelAsRaster(printer: Printer, label: com.posprint.app.domain.template.LabelSpec, els: List<com.posprint.app.domain.template.LabelElement>): ByteArray {
        val bmp = LabelRaster.render(label, els, assets, 1f)
        var gray = AndroidRenderAssets.toGray(bmp)
        val maxW = printer.paperWidth.dots
        if (gray.width > maxW) {
            val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, maxW, (bmp.height * maxW.toFloat() / bmp.width).toInt(), true)
            gray = AndroidRenderAssets.toGray(scaled)
        }
        return EscPosBuilder(printer.cols).init().align(Align.CENTER)
            .raster(Dither.toMono(gray, DitherMode.THRESHOLD)).feed(if (printer.hasCutter) 3 else 4)
            .also { if (printer.hasCutter) it.cut() }.build()
    }

    suspend fun printLabels(t: Template, rows: List<Map<String, String>>, copies: Int, printer: Printer? = null, serial: Serial? = null): Long {
        val p = printer ?: printerFor(t)
        if (rows.isEmpty()) throw PrinterException(PrintError.Other("Nothing to print."))
        val title = if (rows.size > 1) "${t.name} x${rows.size}" else t.name
        return print.enqueue(p, title, compileLabels(p, t, rows, copies, serial))
    }

    /** Auto-incrementing serial: value for row i = start + i * step, zero-padded to [pad]. */
    data class Serial(val start: Long, val step: Long = 1, val pad: Int = 0, val prefix: String = "") {
        fun at(i: Int): String = prefix + (start + i * step).toString().padStart(pad, '0')
    }
}
