package com.posprint.app.data.print

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.posprint.app.data.render.AndroidRenderAssets
import com.posprint.app.domain.render.RenderContext
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import java.io.FileOutputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class PaperOptions(
    val size: PaperSize = PaperSize.A4,
    val landscape: Boolean = false,
    val marginMm: Int = 12,
    val scalePct: Int = 100,
)

/** Normal-paper output: on-screen preview, PDF export, and the Android Print Framework (Wi-Fi / USB office printers). */
@Singleton
class DocumentPrinter @Inject constructor(
    private val tp: TemplatePrinter,
    private val assets: AndroidRenderAssets,
) {
    private fun mmToPt(mm: Int) = mm * 72f / 25.4f

    private fun layout(t: Template, data: TemplateData, rc: RenderContext, wPt: Float, hPt: Float, o: PaperOptions): DocumentLayout {
        val margin = mmToPt(o.marginMm)
        val font = 10f * o.scalePct / 100f
        val cols = DocumentLayout.colsFor(wPt, margin, font)
        val blocks = TemplateEngine.resolve(t.blocks, data, rc.copy(cols = cols))
        return DocumentLayout.build(blocks, wPt, hPt, margin, font, assets)
    }

    suspend fun layout(t: Template, data: TemplateData, o: PaperOptions): DocumentLayout {
        val rc = tp.renderContext(0)
        val (w, h) = if (o.landscape) o.size.hPt to o.size.wPt else o.size.wPt to o.size.hPt
        return layout(t, data, rc, w, h, o)
    }

    /** Writes the document as a PDF. */
    fun writePdf(layout: DocumentLayout, out: OutputStream) {
        val doc = PdfDocument()
        try {
            for (i in 0 until layout.pageCount) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(layout.wPt.toInt(), layout.hPt.toInt(), i + 1).create())
                layout.draw(page.canvas, i)
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally { doc.close() }
    }

    /** Opens the system print dialog (preview, page range, orientation, paper size, printer choice). */
    suspend fun print(activityContext: Context, t: Template, data: TemplateData, o: PaperOptions) {
        val rc = tp.renderContext(0)
        val adapter = Adapter(t, data, rc, o)
        val mm = when (o.size) {
            PaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PaperSize.A5 -> PrintAttributes.MediaSize.ISO_A5
            PaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
        }.let { if (o.landscape) it.asLandscape() else it.asPortrait() }
        val attrs = PrintAttributes.Builder().setMediaSize(mm).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
        (activityContext.getSystemService(Context.PRINT_SERVICE) as PrintManager).print(t.name, adapter, attrs)
    }

    private inner class Adapter(
        val t: Template, val data: TemplateData, val rc: RenderContext, val o: PaperOptions,
    ) : PrintDocumentAdapter() {
        private var current: DocumentLayout? = null

        override fun onLayout(old: PrintAttributes?, new: PrintAttributes, signal: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (signal?.isCanceled == true) { cb.onLayoutCancelled(); return }
            val ms = new.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
            val w = ms.widthMils / 1000f * 72f
            val h = ms.heightMils / 1000f * 72f
            current = layout(t, data, rc, w, h, o)
            cb.onLayoutFinished(
                PrintDocumentInfo.Builder("${t.name}.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(current!!.pageCount).build(),
                old?.mediaSize != new.mediaSize || current == null,
            )
        }

        override fun onWrite(pages: Array<out PageRange>, dest: ParcelFileDescriptor, signal: CancellationSignal?, cb: WriteResultCallback) {
            val lay = current ?: run { cb.onWriteFailed("Nothing to print"); return }
            val doc = PdfDocument()
            try {
                var n = 0
                for (i in 0 until lay.pageCount) {
                    if (pages.none { i >= it.start && i <= it.end }) continue
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(lay.wPt.toInt(), lay.hPt.toInt(), n++).create())
                    lay.draw(page.canvas, i)
                    doc.finishPage(page)
                }
                FileOutputStream(dest.fileDescriptor).use { doc.writeTo(it) }
                cb.onWriteFinished(pages as Array<PageRange>)
            } catch (e: Exception) {
                cb.onWriteFailed(e.message)
            } finally { doc.close() }
        }
    }
}
