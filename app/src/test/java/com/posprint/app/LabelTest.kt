package com.posprint.app

import com.posprint.app.data.printer.label.CpclDriver
import com.posprint.app.data.printer.label.EplDriver
import com.posprint.app.data.printer.label.TsplDriver
import com.posprint.app.data.printer.label.ZplDriver
import com.posprint.app.domain.io.TableParser
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.render.BarcodeMatrix
import com.posprint.app.domain.render.GrayImage
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.render.MonoBitmap
import com.posprint.app.domain.render.MonoOps
import com.posprint.app.domain.render.PBarcode
import com.posprint.app.domain.render.PBitmap
import com.posprint.app.domain.render.PQr
import com.posprint.app.domain.render.PRect
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LabelTest {
    private val spec = LabelSpec(50.0, 30.0, MediaType.GAP, 3.0, 203)
    private val printer = Printer(name = "L", connection = ConnectionType.USB, address = "1:1")

    private object Assets : RenderAssets {
        override fun image(source: String, maxWidthDots: Int) = null
        override fun text(text: String, align: Alignment, bold: Boolean, italic: Boolean, sizeMul: Int, widthDots: Int) = GrayImage(8, 2, ByteArray(16) { 0 })
        override fun labelText(text: String, bold: Boolean, align: Alignment, fontMm: Double, widthDots: Int, heightDots: Int, rotation: Int, dpi: Int) =
            GrayImage(16, 8, ByteArray(128) { 0 })
        override fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int) = null
    }

    private val els = listOf(
        LBarcode("b", 2.0, 2.0, 40.0, 12.0, 0, "ABC123", BarcodeType.CODE128, true),
        LQr("q", 2.0, 16.0, 12.0, 12.0, 0, "hello"),
        LText("t", 16.0, 18.0, 30.0, 6.0, 0, "Name"),
    )

    private fun str(b: ByteArray) = String(b, Charsets.ISO_8859_1)

    @Test fun mmToDots() {
        assertEquals(400, LabelPlanner.dots(50.0, 203))
        assertEquals(203, LabelPlanner.dots(25.4, 203))
    }

    @Test fun plannerEmitsNativeBarcodeQrAndTextBitmap() {
        val p = LabelPlanner.plan(spec, els, Assets)
        assertTrue(p.any { it is PBarcode }); assertTrue(p.any { it is PQr }); assertTrue(p.any { it is PBitmap })
        val bc = p.filterIsInstance<PBarcode>().single()
        assertTrue(bc.narrow >= 1)
    }

    @Test fun rotatedBarcodeBecomesBitmap() {
        val rotated = (els[0] as LBarcode).copy(rotation = 90, wMm = 12.0, hMm = 40.0)
        val p = LabelPlanner.plan(spec, listOf(rotated), Assets)
        assertTrue(p.single() is PBitmap)
        val bmp = (p.single() as PBitmap).mono
        assertEquals(LabelPlanner.dots(12.0, 203), bmp.width)
        assertEquals(LabelPlanner.dots(40.0, 203), bmp.height)
    }

    @Test fun tsplOutput() {
        val s = str(TsplDriver().compile(spec, els, 3, Assets))
        assertTrue(s.startsWith("SIZE 50 mm,30 mm\r\nGAP 3 mm,0 mm\r\n"))
        assertTrue(s.contains("BARCODE ") && s.contains("\"128\"") && s.contains("\"ABC123\""))
        assertTrue(s.contains("QRCODE ") && s.contains("BITMAP "))
        assertTrue(s.endsWith("PRINT 1,3\r\n"))
    }

    @Test fun zplOutput() {
        val s = str(ZplDriver().compile(spec, els, 2, Assets))
        assertTrue(s.startsWith("^XA^CI28^MNY^PW400^LL240"))
        assertTrue(s.contains("^BCN,") && s.contains("^FDABC123^FS") && s.contains("^BQN,2,") && s.contains("^GFA,"))
        assertTrue(s.trimEnd().endsWith("^PQ2^XZ"))
    }

    @Test fun eplOutput() {
        val s = str(EplDriver().compile(spec, els, 1, Assets))
        assertTrue(s.contains("N\nq400\nQ240,24\n") && s.contains(",\"ABC123\"") && s.contains("\nP1\n"))
    }

    @Test fun cpclOutput() {
        val s = str(CpclDriver().compile(spec, els, 4, Assets))
        assertTrue(s.startsWith("! 0 203 203 240 4\r\nPAGE-WIDTH 400\r\nGAP-SENSE"))
        assertTrue(s.contains("BARCODE 128 ") && s.contains("B QR ") && s.contains("EG ") && s.endsWith("FORM\r\nPRINT\r\n"))
    }

    @Test fun testLabelsNeedNoPlatformText() {
        listOf(TsplDriver(), ZplDriver(), EplDriver(), CpclDriver()).forEach { d ->
            assertTrue(d.language.isLabel); assertTrue(d.testPage(printer).isNotEmpty())
        }
    }

    @Test fun bitmapPolarityTsplInvertsZplKeeps() {
        val p = listOf(LText("t", 0.0, 0.0, 2.0, 1.0))
        val tspl = TsplDriver().compile(spec, p, 1, Assets)
        val zpl = str(ZplDriver().compile(spec, p, 1, Assets))
        assertTrue(zpl.contains("^GFA,")) // black text -> set bits (FF) in ZPL hex
        assertTrue(zpl.contains("FF"))
        assertTrue(tspl.isNotEmpty())
    }

    @Test fun validatesBarcodes() {
        assertNull(BarcodeMatrix.validate(BarcodeType.CODE128, "Hello 123"))
        assertNull(BarcodeMatrix.validate(BarcodeType.EAN13, "5901234123457"))
        assertNotNull(BarcodeMatrix.validate(BarcodeType.EAN13, "123"))
        assertNotNull(BarcodeMatrix.validate(BarcodeType.ITF, "123"))
        assertNotNull(BarcodeMatrix.validate(BarcodeType.CODE128, ""))
    }

    @Test fun monoRotate90() {
        val m = MonoBitmap(8, 2, byteArrayOf(0x80.toByte(), 0x00)) // pixel (0,0) black
        val r = MonoOps.rotate(m, 90)
        assertEquals(2, r.width); assertEquals(8, r.height)
        assertTrue(MonoOps.get(r, 1, 0)) // (x,y)=(0,0) -> (H-1-y, x) = (1,0)
    }

    @Test fun csvHandlesQuotesAndSemicolons() {
        val t = TableParser.csv("name;price\n\"Tea, green\";4.5\n\"He said \"\"hi\"\"\";1\n")
        assertEquals(listOf("name", "price"), t.headers)
        assertEquals("Tea, green", t.rows[0][0])
        assertEquals("He said \"hi\"", t.rows[1][0])
    }

    @Test fun xlsxReadsSharedStringsAndNumbers() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("xl/sharedStrings.xml")); z.write("<sst><si><t>sku</t></si><si><t>qty</t></si><si><t>A&amp;B</t></si></sst>".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            z.write(("<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>" +
                "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>5.0</v></c></row></sheetData></worksheet>").toByteArray()); z.closeEntry()
        }
        val t = TableParser.xlsx(bos.toByteArray().inputStream())
        assertEquals(listOf("sku", "qty"), t.headers)
        assertEquals(mapOf("sku" to "A&B", "qty" to "5"), t.records().single())
    }
}
