package com.posprint.app

import com.posprint.app.data.BackupFile
import com.posprint.app.data.StarterTemplates
import com.posprint.app.data.print.DocumentLayout
import com.posprint.app.data.print.TemplatePrinter
import com.posprint.app.data.settings.BusinessProfile
import com.posprint.app.data.settings.UiPrefs
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.render.Formatter
import com.posprint.app.domain.template.TemplateJson
import com.posprint.app.ui.pos.PosItem
import com.posprint.app.ui.pos.PosState
import com.posprint.app.ui.pos.totals
import com.posprint.app.ui.settings.parseHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AppLogicTest {
    @Test fun hexParsing() {
        assertArrayEquals(byteArrayOf(0x1B, 0x40), parseHex("1B 40"))
        assertArrayEquals(byteArrayOf(0x1D, 0x56), parseHex("0x1D,0x56"))
        assertArrayEquals(byteArrayOf(0x1B, 0x40), parseHex("1b40"))
        assertNull(parseHex("1B 4")); assertNull(parseHex("ZZ")); assertNull(parseHex(""))
    }

    @Test fun posTotalsWithPercentDiscountAndTax() {
        val s = PosState(
            items = listOf(PosItem(1, "A", BigDecimal("10.00"), 2), PosItem(2, "B", BigDecimal("5.50"), 1)),
            percentDiscount = true, discount = "10", taxPct = "8", paid = "50",
        )
        val t = totals(s)
        assertEquals(BigDecimal("25.50"), t.subtotal)
        assertEquals(BigDecimal("2.55"), t.discount)
        assertEquals(BigDecimal("1.84"), t.tax)        // (25.50 - 2.55) * 8% = 1.836
        assertEquals(BigDecimal("24.79"), t.total)
        assertEquals(BigDecimal("25.21"), t.change)
    }

    @Test fun posDiscountNeverExceedsSubtotal() {
        val s = PosState(items = listOf(PosItem(1, "A", BigDecimal("3.00"), 1)), discount = "99")
        assertEquals(BigDecimal("0.00"), totals(s).total)
    }

    @Test fun serialNumbers() {
        val s = TemplatePrinter.Serial(start = 7, step = 5, pad = 4, prefix = "SN-")
        assertEquals("SN-0007", s.at(0)); assertEquals("SN-0012", s.at(1)); assertEquals("SN-0027", s.at(4))
    }

    @Test fun moneyFormatting() {
        assertEquals("$1,234.50", Formatter("$").money("1234.5"))
        assertEquals("1,234.5 €", Formatter("€", decimals = 1, currencyAfter = true).money("1234.5"))
        assertEquals("abc", Formatter("$").money("abc")) // non-numbers pass through
    }

    @Test fun documentColumnsGrowWithPageWidth() {
        val a4 = DocumentLayout.colsFor(595f, 34f, 10f)
        val a5 = DocumentLayout.colsFor(420f, 34f, 10f)
        assertTrue(a4 > a5); assertEquals(87, a4)
    }

    @Test fun backupFileRoundTrips() {
        val file = BackupFile(
            createdAt = 1L,
            printers = listOf(Printer(1, "XP-58", ConnectionType.BLUETOOTH, "AA:BB", PrinterLanguage.TSPL, hasCutter = true, density = 5)),
            templates = StarterTemplates.all().take(3),
            profile = BusinessProfile(name = "Shop", currency = "₹"),
            ui = UiPrefs(paperMarginMm = 9),
            logoPngBase64 = "AAAA",
        )
        val json = TemplateJson.json.encodeToString(BackupFile.serializer(), file)
        assertEquals(file, TemplateJson.json.decodeFromString(BackupFile.serializer(), json))
    }

    @Test fun unknownFieldsInTemplateFilesAreIgnored() {
        val t = StarterTemplates.all().first()
        val json = TemplateJson.encode(t).replaceFirst("{", "{\"futureField\": 1,")
        assertEquals(t, TemplateJson.decode(json))
    }
}
