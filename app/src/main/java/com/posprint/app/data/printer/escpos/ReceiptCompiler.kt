package com.posprint.app.data.printer.escpos

import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.Dither
import com.posprint.app.domain.render.RBarcode
import com.posprint.app.domain.render.RBlock
import com.posprint.app.domain.render.RCommand
import com.posprint.app.domain.render.RImage
import com.posprint.app.domain.render.RQr
import com.posprint.app.domain.render.RSpacer
import com.posprint.app.domain.render.RText
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.CommandKind
import com.posprint.app.domain.template.DitherMode

/** Turns resolved blocks into ESC/POS bytes, using the printer's profile. */
class ReceiptCompiler(private val printer: Printer, private val assets: RenderAssets) {

    private fun Alignment.esc() = when (this) {
        Alignment.LEFT -> Align.LEFT; Alignment.CENTER -> Align.CENTER; Alignment.RIGHT -> Align.RIGHT
    }

    fun compile(blocks: List<RBlock>, finish: Boolean = true): ByteArray {
        val b = EscPosBuilder(printer.cols, printer.codePage.charset).init()
        b.codePage(printer.codePage.escN)
        if (printer.marginDots > 0) b.leftMargin(printer.marginDots)
        printer.density?.let { b.density(it) }
        printer.lineSpacing?.let { b.lineSpacing(it) }

        for (block in blocks) when (block) {
            is RText -> text(b, block)
            is RImage -> assets.image(block.source, imageWidthDots(block))?.let {
                b.align(block.align.esc()).raster(Dither.toMono(it, block.dither))
            }
            is RQr -> b.align(block.align.esc()).qr(block.data, block.size).feed(1)
            is RBarcode -> b.align(block.align.esc()).barcode(block.data, block.type, block.height).feed(1)
            is RSpacer -> b.feed(block.lines)
            is RCommand -> when (block.kind) {
                CommandKind.CUT -> if (printer.hasCutter) b.feed(2).cut()
                CommandKind.DRAWER -> if (printer.hasDrawer) b.openDrawer()
                CommandKind.BEEP -> b.beep()
            }
        }
        b.align(Align.LEFT)
        if (finish) if (printer.hasCutter) b.feed(2).cut() else b.feed(4)
        return b.build()
    }

    private fun imageWidthDots(i: RImage) = (printer.paperWidth.dots * i.widthPct.coerceIn(5, 100) / 100)

    private fun text(b: EscPosBuilder, t: RText) {
        if (t.italic || !b.canEncode(t.text)) {
            // Printer cannot render this natively (italic, Arabic, Hindi, CJK...): draw it as a bitmap.
            val img = assets.text(t.text.trimEnd(), t.align, t.bold, t.italic, maxOf(t.widthMul, t.heightMul), printer.paperWidth.dots)
            b.align(Align.LEFT).raster(Dither.toMono(img, DitherMode.THRESHOLD))
            return
        }
        b.align(t.align.esc()).bold(t.bold).underline(t.underline).invert(t.invert).fontB(t.fontB).size(t.widthMul, t.heightMul)
        b.line(t.text)
        b.bold(false).underline(false).invert(false).fontB(false).size(1, 1)
    }
}
