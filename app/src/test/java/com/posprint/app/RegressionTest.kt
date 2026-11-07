package com.posprint.app

import com.posprint.app.data.printer.escpos.EscPosBuilder
import com.posprint.app.data.printer.label.ZplDriver
import com.posprint.app.domain.io.TableParser
import com.posprint.app.domain.render.Formatter
import com.posprint.app.domain.render.GrayImage
import com.posprint.app.domain.render.LabelPlanner
import com.posprint.app.domain.render.PBitmap
import com.posprint.app.domain.render.PQr
import com.posprint.app.domain.render.RText
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.render.RenderContext
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TextBlock
import com.posprint.app.ui.pos.PosItem
import com.posprint.app.ui.pos.PosState
import com.posprint.app.ui.pos.parseAmount
import com.posprint.app.ui.pos.totals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One test per bug fixed in the review pass, so they stay fixed. */
class RegressionTest {
    private object NoAssets : RenderAssets {
        override fun image(source: String, maxWidthDots: Int) = null
        override fun text(text: String, align: Alignment, bold: Boolean, italic: Boolean, sizeMul: Int, widthDots: Int) = GrayImage(8, 2, ByteArray(16))
        override fun labelText(text: String, bold: Boolean, align: Alignment, fontMm: Double, widthDots: Int, heightDots: Int, rotation: Int, dpi: Int) =
            GrayImage(8, 2, ByteArray(16))
        override fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int) = null
    }

    private val now = LocalDateTime.of(2026, 3, 9, 14, 30)

    @Test fun invalidDatePatternFallsBackInsteadOfCrashing() {
        val ctx = RenderContext(32, formatter = Formatter(datePattern = "yyyy-MM-dd {bad", timePattern = "HH:mm"), now = now)
        val out = TemplateEngine.resolve(listOf(TextBlock("On {{date}} at {{time}}")), TemplateData(), ctx)
        assertEquals("On 2026-03-09 at 14:30", (out.single() as RText).text)
    }

    @Test fun labelDateUsesSameSafeFormatting() {
        val ctx = RenderContext(32, formatter = Formatter(datePattern = "bad'", timePattern = "HH:mm"), now = now)
        val els = LabelPlanner.resolve(listOf(LQr("q", 0.0, 0.0, 10.0, 10.0, 0, "{{date}}")), emptyMap(), ctx)
        assertEquals("2026-03-09", (els.single() as LQr).data)
    }

    @Test fun nonAsciiLabelQrIsDrawnAsImage() {
        val spec = LabelSpec(50.0, 30.0)
        val ascii = LabelPlanner.plan(spec, listOf(LQr("q", 2.0, 2.0, 12.0, 12.0, 0, "hello")), NoAssets)
        val hindi = LabelPlanner.plan(spec, listOf(LQr("q", 2.0, 2.0, 12.0, 12.0, 0, "नमस्ते ₹50")), NoAssets)
        assertTrue(ascii.single() is PQr)
        assertTrue(hindi.single() is PBitmap)
    }

    @Test fun zplEscapesCaretAndTildeInFieldData() {
        val spec = LabelSpec(50.0, 30.0)
        val zpl = String(
            ZplDriver().compile(spec, listOf(LBarcode("b", 2.0, 2.0, 40.0, 12.0, 0, "A^B~C", BarcodeType.CODE128, false)), 1, NoAssets),
            Charsets.ISO_8859_1,
        )
        assertTrue(zpl.contains("^FH\\^FDA\\5EB\\7EC^FS"))
        assertFalse(zpl.contains("A^B"))
    }

    @Test fun escPosBarcodeLengthNeverOverflowsItsByte() {
        val out = EscPosBuilder(32).barcode("X".repeat(400), BarcodeType.CODE128).build()
        // GS k m n d1..dn: n is the byte right after "GS k 73"
        val i = (0 until out.size - 2).first { out[it] == 0x1D.toByte() && out[it + 1] == 0x6B.toByte() }
        val n = out[i + 3].toInt() and 0xFF
        assertEquals(255, n)
        assertEquals(i + 4 + n, out.size)
    }

    @Test fun xlsxReadsTheFirstSheetEvenWhenZippedOutOfOrder() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(name: String, body: String) { z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray()); z.closeEntry() }
            put("xl/worksheets/sheet2.xml", "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>wrong</t></is></c></row></sheetData></worksheet>")
            put("xl/worksheets/sheet1.xml", "<worksheet><sheetData><row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>sku</t></is></c></row>" +
                "<row r=\"2\"><c r=\"A2\"><v>42</v></c></row></sheetData></worksheet>")
        }
        val t = TableParser.xlsx(bos.toByteArray().inputStream())
        assertEquals(listOf("sku"), t.headers)
        assertEquals("42", t.rows.single().single())
    }

    @Test fun rowsTextKeepsMultiWordNamesWhenParsed() {
        val fields = listOf("name", "qty", "total")
        val rows = TemplateEngine.parseRows("Blueberry muffin, 1, 2.75\nTea,2,3", fields)
        assertEquals("Blueberry muffin", rows[0]["name"])
        assertEquals(2, rows.size)
    }

    @Test fun amountsAcceptDecimalComma() {
        assertEquals(BigDecimal("3.50"), parseAmount(" 3,50 "))
        val s = PosState(items = listOf(PosItem(1, "A", BigDecimal("10"), 1)), discount = "1,5", taxPct = "0", paid = "20,00")
        assertEquals(BigDecimal("8.50"), totals(s).total)
        assertEquals(BigDecimal("11.50"), totals(s).change)
    }

    @Test fun formatterToleratesOutOfRangeDecimals() {
        assertEquals("1.000000", Formatter(decimals = 40, thousands = false).number("1"))
    }

    @Test fun receiptMathFillsLineTotalsSubtotalTotalAndChange() {
        val data = TemplateData(
            vars = mapOf("discount" to "1.50", "tax" to "1", "paid" to "20"),
            lists = mapOf("items" to listOf(mapOf("name" to "Tea", "qty" to "2", "price" to "3,50"), mapOf("name" to "Cake", "qty" to "1", "price" to "4"))),
        )
        val (out, calc) = com.posprint.app.domain.render.ReceiptMath.autofill(data, listOf("subtotal", "total", "change"))
        assertEquals("7", out.lists["items"]!![0]["total"])
        assertEquals("11", out.vars["subtotal"])
        assertEquals("10.5", out.vars["total"])
        assertEquals("9.5", out.vars["change"])
        assertEquals(setOf("subtotal", "total", "change"), calc)
    }

    @Test fun receiptMathLeavesTemplatesWithoutItemsAlone() {
        val data = TemplateData(vars = mapOf("total" to "5"))
        val (out, calc) = com.posprint.app.domain.render.ReceiptMath.autofill(data, listOf("total"))
        assertEquals("5", out.vars["total"]); assertTrue(calc.isEmpty())
    }

    @Test fun codeNamesBecomePlainWords() {
        assertEquals("Receipt no.", com.posprint.app.ui.common.humanize("receipt_no"))
        assertEquals("Customer address", com.posprint.app.ui.common.humanize("customer_address"))
        assertEquals("Business tax id", com.posprint.app.ui.common.humanize("business.taxId"))
    }
}
