package com.posprint.app

import com.posprint.app.data.StarterTemplates
import com.posprint.app.data.printer.escpos.ReceiptCompiler
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.render.BusinessInfo
import com.posprint.app.domain.render.Dither
import com.posprint.app.domain.render.Expr
import com.posprint.app.domain.render.Formatter
import com.posprint.app.domain.render.GrayImage
import com.posprint.app.domain.render.RText
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.render.RenderContext
import com.posprint.app.domain.render.TemplateEngine
import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.ColumnsBlock
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TemplateJson
import com.posprint.app.domain.template.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TemplateEngineTest {
    private val ctx = RenderContext(32, BusinessInfo(name = "Shop"), Formatter("$"), LocalDateTime.of(2026, 1, 2, 3, 4))
    private fun texts(r: List<com.posprint.app.domain.render.RBlock>) = r.filterIsInstance<RText>().map { it.text }

    @Test fun substitutesVariablesAndFilters() {
        val out = TemplateEngine.resolve(listOf(TextBlock("Hi {{name|upper}} {{amt|money}}")), TemplateData(mapOf("name" to "ann", "amt" to "1234.5")), ctx)
        assertEquals(listOf("Hi ANN $1,234.50"), texts(out))
    }

    @Test fun conditionalsHideLines() {
        val blocks = listOf(ColumnsBlock("Discount", "{{d}}", showIf = "d > 0"))
        assertTrue(TemplateEngine.resolve(blocks, TemplateData(mapOf("d" to "0")), ctx).isEmpty())
        assertEquals(1, TemplateEngine.resolve(blocks, TemplateData(mapOf("d" to "1.5")), ctx).size)
    }

    @Test fun exprSupportsTruthyNotAndLogic() {
        val v = mapOf("a" to "x", "b" to "", "n" to "0")
        val l: (String) -> String? = { v[it] }
        assertTrue(Expr.eval("a", l)); assertFalse(Expr.eval("b", l)); assertFalse(Expr.eval("n", l))
        assertTrue(Expr.eval("!b", l)); assertTrue(Expr.eval("a == \"x\" && !b", l)); assertTrue(Expr.eval("b || a", l))
        assertTrue(Expr.eval("{{a}} != 'y'", l))
    }

    @Test fun wrapsLongTextToPaperWidth() {
        val out = TemplateEngine.resolve(listOf(TextBlock("one two three four five six seven eight nine ten")), TemplateData(), ctx)
        assertTrue(texts(out).size > 1)
        assertTrue(texts(out).all { it.length <= 32 })
    }

    @Test fun starterReceiptRendersWithinPaperWidth() {
        val t = StarterTemplates.all().first { it.name == "Sales receipt" }
        val out = TemplateEngine.resolve(t.blocks, t.sample, ctx.copy(cols = 32))
        assertTrue(out.filterIsInstance<RText>().filter { it.widthMul == 1 }.all { it.text.length <= 32 })
        assertTrue(texts(out).any { it.contains("Cappuccino") })
        assertTrue(texts(out).any { it.startsWith("Discount") })
    }

    @Test fun tableColumnsAlign() {
        val t = StarterTemplates.all().first { it.name == "Sales receipt" }
        val rows = TemplateEngine.resolve(t.blocks, t.sample, ctx.copy(cols = 48)).let(::texts)
        val row = rows.first { it.startsWith("Cappuccino") }
        assertEquals(48, row.length)          // right-aligned total column reaches the paper edge
        assertEquals(48, rows.first { it.startsWith("Item") }.length)
        assertTrue(row.endsWith("7.00"))
    }

    @Test fun tenStarterTemplatesSurviveJsonRoundTrip() {
        val all = StarterTemplates.all()
        assertEquals(10, all.size)
        all.forEach { assertEquals(it, TemplateJson.decode(TemplateJson.encode(it))) }
    }

    @Test fun ditherProducesBlackAndWhite() {
        val img = GrayImage(8, 2, ByteArray(16) { if (it < 8) 0 else 255.toByte() })
        val m = Dither.toMono(img, DitherMode.THRESHOLD)
        assertEquals(0xFF, m.data[0].toInt() and 0xFF)
        assertEquals(0x00, m.data[1].toInt() and 0xFF)
    }

    private object FakeAssets : RenderAssets {
        var textCalls = 0
        override fun image(source: String, maxWidthDots: Int) = null
        override fun text(text: String, align: Alignment, bold: Boolean, italic: Boolean, sizeMul: Int, widthDots: Int): GrayImage {
            textCalls++
            return GrayImage(widthDots, 4, ByteArray(widthDots * 4) { 255.toByte() })
        }
        override fun labelText(text: String, bold: Boolean, align: Alignment, fontMm: Double, widthDots: Int, heightDots: Int, rotation: Int, dpi: Int) = text(text, align, bold, false, 1, widthDots)
        override fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int) = null
    }

    @Test fun nonLatinTextFallsBackToBitmap() {
        val printer = Printer(name = "p", connection = ConnectionType.USB, address = "1:2")
        val before = FakeAssets.textCalls
        ReceiptCompiler(printer, FakeAssets).compile(listOf(RText("مرحبا"), RText("hello")))
        assertEquals(before + 1, FakeAssets.textCalls) // only the Arabic line is rasterised
    }
}
