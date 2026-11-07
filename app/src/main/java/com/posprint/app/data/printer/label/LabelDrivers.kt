package com.posprint.app.data.printer.label

import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.model.PrinterStatus
import com.posprint.app.domain.printer.PrinterDriver
import com.posprint.app.domain.render.LBitHelpers.hex
import com.posprint.app.domain.render.LPrim
import com.posprint.app.domain.render.LabelCompiler
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.render.MonoOps
import com.posprint.app.domain.render.PBarcode
import com.posprint.app.domain.render.PBitmap
import com.posprint.app.domain.render.PQr
import com.posprint.app.domain.render.PRect
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LBox
import com.posprint.app.domain.template.LLine
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.MediaType
import java.io.ByteArrayOutputStream

/** Shared by all label languages: a printer-agnostic test label and "no status support". */
abstract class LabelDriver : PrinterDriver, LabelCompiler {
    override val statusRequest = ByteArray(0)
    override val statusReplySize = 0
    override fun parseStatus(reply: ByteArray): PrinterStatus? = null

    protected abstract fun emit(spec: LabelSpec, prims: List<LPrim>, copies: Int): ByteArray

    override fun compile(spec: LabelSpec, elements: List<LabelElement>, copies: Int, assets: RenderAssets): ByteArray =
        emit(spec, LabelPlanner.plan(spec, elements, assets), copies.coerceAtLeast(1))

    /** Test label uses only native primitives, so no platform text rendering is needed. */
    override fun testPage(printer: Printer): ByteArray {
        val spec = LabelSpec(50.0, 30.0, MediaType.GAP, 3.0, printer.dpi)
        val els = listOf(
            LBox("b", 1.0, 1.0, 48.0, 28.0, 0, thicknessMm = 0.4),
            LLine("l", 3.0, 15.0, 44.0, 0.4, 0, thicknessMm = 0.4),
            LBarcode("c", 4.0, 3.0, 42.0, 11.0, 0, "TEST-123", BarcodeType.CODE128, true),
            LQr("q", 4.0, 17.0, 11.0, 11.0, 0, "POS-PRINT-TEST"),
            LBox("b2", 20.0, 18.0, 26.0, 9.0, 0, thicknessMm = 0.3),
        )
        return emit(spec, plan(spec, els), 1)
    }

    private fun plan(spec: LabelSpec, els: List<LabelElement>) =
        LabelPlanner.plan(spec, els, NoAssets)

    private object NoAssets : RenderAssets {
        override fun image(source: String, maxWidthDots: Int) = null
        override fun text(text: String, align: com.posprint.app.domain.template.Alignment, bold: Boolean, italic: Boolean, sizeMul: Int, widthDots: Int) =
            error("no text in test label")
        override fun labelText(text: String, bold: Boolean, align: com.posprint.app.domain.template.Alignment, fontMm: Double, widthDots: Int, heightDots: Int, rotation: Int, dpi: Int) =
            error("no text in test label")
        override fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int) = null
    }

    protected fun ByteArrayOutputStream.ascii(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))
    protected fun dots(mm: Double, dpi: Int) = LabelPlanner.dots(mm, dpi)
}

/** TSC/TSPL (most Chinese label printers: Xprinter, Gprinter, Zjiang, Deli...). */
class TsplDriver : LabelDriver() {
    override val language = PrinterLanguage.TSPL
    override fun selfTest() = "SELFTEST\r\n".toByteArray()

    override fun emit(spec: LabelSpec, prims: List<LPrim>, copies: Int): ByteArray {
        val o = ByteArrayOutputStream()
        o.ascii("SIZE ${mm(spec.widthMm)} mm,${mm(spec.heightMm)} mm\r\n")
        o.ascii(when (spec.media) {
            MediaType.GAP -> "GAP ${mm(spec.gapMm)} mm,0 mm\r\n"
            MediaType.BLACK_MARK -> "BLINE ${mm(spec.gapMm)} mm,0 mm\r\n"
            MediaType.CONTINUOUS -> "GAP 0 mm,0 mm\r\n"
        })
        o.ascii("DIRECTION 1\r\nCLS\r\n")
        for (p in prims) when (p) {
            is PBitmap -> {
                o.ascii("BITMAP ${p.x},${p.y},${p.mono.rowBytes},${p.mono.height},0,")
                o.write(MonoOps.inverted(p.mono)) // TSPL: 0 = black
                o.ascii("\r\n")
            }
            is PRect -> o.ascii(
                if (p.filled) "BAR ${p.x},${p.y},${p.w},${p.h}\r\n"
                else "BOX ${p.x},${p.y},${p.x + p.w},${p.y + p.h},${p.thickness}\r\n",
            )
            is PBarcode -> o.ascii("BARCODE ${p.x},${p.y},\"${code(p.type)}\",${p.height},${if (p.human) 1 else 0},0,${p.narrow},${p.narrow * 2},\"${esc(p.data)}\"\r\n")
            is PQr -> o.ascii("QRCODE ${p.x},${p.y},M,${p.cell},A,0,\"${esc(p.data)}\"\r\n")
        }
        o.ascii("PRINT 1,$copies\r\n")
        return o.toByteArray()
    }

    private fun mm(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
    private fun esc(s: String) = s.replace("\"", "\\[\"]")
    private fun code(t: BarcodeType) = when (t) {
        BarcodeType.CODE128 -> "128"; BarcodeType.CODE39 -> "39"; BarcodeType.EAN13 -> "EAN13"
        BarcodeType.UPCA -> "UPCA"; BarcodeType.ITF -> "25"; BarcodeType.PDF417 -> "128"
    }
}

/** Zebra ZPL II. */
class ZplDriver : LabelDriver() {
    override val language = PrinterLanguage.ZPL
    override fun selfTest() = "~WC".toByteArray()

    override fun emit(spec: LabelSpec, prims: List<LPrim>, copies: Int): ByteArray {
        val o = ByteArrayOutputStream()
        val media = when (spec.media) { MediaType.GAP -> "Y"; MediaType.BLACK_MARK -> "M"; MediaType.CONTINUOUS -> "N" }
        o.ascii("^XA^CI28^MN$media^PW${dots(spec.widthMm, spec.dpi)}^LL${dots(spec.heightMm, spec.dpi)}^LH0,0\n")
        for (p in prims) when (p) {
            is PBitmap -> {
                val total = p.mono.rowBytes * p.mono.height
                o.ascii("^FO${p.x},${p.y}^GFA,$total,$total,${p.mono.rowBytes},${hex(p.mono.data)}^FS\n")
            }
            is PRect -> o.ascii(
                if (p.filled) "^FO${p.x},${p.y}^GB${p.w},${p.h},${minOf(p.w, p.h)}^FS\n"
                else "^FO${p.x},${p.y}^GB${p.w},${p.h},${p.thickness}^FS\n",
            )
            is PBarcode -> {
                val h = if (p.human) "Y" else "N"
                val cmd = when (p.type) {
                    BarcodeType.CODE128, BarcodeType.PDF417 -> "^BCN,${p.height},$h,N,N"
                    BarcodeType.CODE39 -> "^B3N,N,${p.height},$h,N"
                    BarcodeType.EAN13 -> "^BEN,${p.height},$h,N"
                    BarcodeType.UPCA -> "^BUN,${p.height},$h,N,Y"
                    BarcodeType.ITF -> "^B2N,${p.height},$h,N,N"
                }
                o.ascii("^FO${p.x},${p.y}^BY${p.narrow},3,${p.height}$cmd^FH\\^FD${fd(p.data)}^FS\n")
            }
            is PQr -> o.ascii("^FO${p.x},${p.y}^BQN,2,${p.cell.coerceIn(1, 10)}^FH\\^FDMA,${fd(p.data)}^FS\n")
        }
        o.ascii("^PQ$copies^XZ\n")
        return o.toByteArray()
    }

    /** ^ and ~ start ZPL commands; with ^FH they (and the escape char itself) are sent as hex. */
    private fun fd(s: String) = s.replace("\\", "\\5C").replace("^", "\\5E").replace("~", "\\7E")
}

/** Zebra EPL2 (LP/TLP 2824 and clones). Bitmap polarity: 0 = black. */
class EplDriver : LabelDriver() {
    override val language = PrinterLanguage.EPL
    override fun selfTest() = "\nU\n".toByteArray()

    override fun emit(spec: LabelSpec, prims: List<LPrim>, copies: Int): ByteArray {
        val o = ByteArrayOutputStream()
        val gap = if (spec.media == MediaType.CONTINUOUS) 0 else dots(spec.gapMm, spec.dpi)
        o.ascii("\nN\nq${dots(spec.widthMm, spec.dpi)}\nQ${dots(spec.heightMm, spec.dpi)},$gap\n")
        for (p in prims) when (p) {
            is PBitmap -> {
                o.ascii("GW${p.x},${p.y},${p.mono.rowBytes},${p.mono.height},")
                o.write(MonoOps.inverted(p.mono))
                o.ascii("\n")
            }
            is PRect -> o.ascii(
                if (p.filled) "LO${p.x},${p.y},${p.w},${p.h}\n" else "X${p.x},${p.y},${p.thickness},${p.x + p.w},${p.y + p.h}\n",
            )
            is PBarcode -> {
                val t = when (p.type) {
                    BarcodeType.CODE128, BarcodeType.PDF417 -> "1"; BarcodeType.CODE39 -> "3"
                    BarcodeType.EAN13 -> "E30"; BarcodeType.UPCA -> "UA0"; BarcodeType.ITF -> "2"
                }
                o.ascii("B${p.x},${p.y},0,$t,${p.narrow},${p.narrow * 2},${p.height},${if (p.human) "B" else "N"},\"${p.data.replace("\"", "")}\"\n")
            }
            is PQr -> o.ascii("b${p.x},${p.y},Q,m2,s${p.cell},eM,iA,\"${p.data.replace("\"", "")}\"\n")
        }
        o.ascii("P$copies\n")
        return o.toByteArray()
    }
}

/** Zebra/Honeywell CPCL (mobile label printers). */
class CpclDriver : LabelDriver() {
    override val language = PrinterLanguage.CPCL
    override fun selfTest() = ByteArray(0)

    override fun emit(spec: LabelSpec, prims: List<LPrim>, copies: Int): ByteArray {
        val o = ByteArrayOutputStream()
        val h = dots(spec.heightMm, spec.dpi)
        o.ascii("! 0 ${spec.dpi} ${spec.dpi} $h $copies\r\nPAGE-WIDTH ${dots(spec.widthMm, spec.dpi)}\r\n")
        when (spec.media) {
            MediaType.GAP -> o.ascii("GAP-SENSE\r\n")
            MediaType.BLACK_MARK -> o.ascii("BAR-SENSE\r\n")
            MediaType.CONTINUOUS -> Unit
        }
        for (p in prims) when (p) {
            is PBitmap -> o.ascii("EG ${p.mono.rowBytes} ${p.mono.height} ${p.x} ${p.y} ${hex(p.mono.data)}\r\n")
            is PRect -> o.ascii(
                if (p.filled) {
                    val horizontal = p.w >= p.h
                    if (horizontal) "LINE ${p.x} ${p.y + p.h / 2} ${p.x + p.w} ${p.y + p.h / 2} ${p.h}\r\n"
                    else "LINE ${p.x + p.w / 2} ${p.y} ${p.x + p.w / 2} ${p.y + p.h} ${p.w}\r\n"
                } else "BOX ${p.x} ${p.y} ${p.x + p.w} ${p.y + p.h} ${p.thickness}\r\n",
            )
            is PBarcode -> {
                val t = when (p.type) {
                    BarcodeType.CODE128, BarcodeType.PDF417 -> "128"; BarcodeType.CODE39 -> "39"
                    BarcodeType.EAN13 -> "EAN13"; BarcodeType.UPCA -> "UPCA"; BarcodeType.ITF -> "I2OF5"
                }
                if (p.human) o.ascii("BARCODE-TEXT 7 0 5\r\n")
                o.ascii("BARCODE $t ${p.narrow} 1 ${p.height} ${p.x} ${p.y} ${p.data}\r\n")
                if (p.human) o.ascii("BARCODE-TEXT OFF\r\n")
            }
            is PQr -> o.ascii("B QR ${p.x} ${p.y} M 2 U ${p.cell.coerceIn(1, 32)}\r\nMA,${p.data}\r\nENDQR\r\n")
        }
        o.ascii("FORM\r\nPRINT\r\n")
        return o.toByteArray()
    }
}
