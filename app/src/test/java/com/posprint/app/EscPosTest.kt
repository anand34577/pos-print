package com.posprint.app

import com.posprint.app.data.printer.escpos.Align
import com.posprint.app.data.printer.escpos.EscPosBuilder
import com.posprint.app.data.printer.escpos.EscPosDriver
import com.posprint.app.domain.model.ConnectionType
import com.posprint.app.domain.model.PaperWidth
import com.posprint.app.domain.model.Printer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EscPosTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private val printer = Printer(name = "XP-58", connection = ConnectionType.BLUETOOTH, address = "AA:BB")

    @Test fun initAlignBoldAreCorrect() {
        val out = EscPosBuilder(32).init().align(Align.CENTER).bold(true).build()
        assertArrayEquals(bytes(0x1B, 0x40, 0x1B, 0x61, 1, 0x1B, 0x45, 1), out)
    }

    @Test fun sizePacksWidthAndHeightNibbles() {
        assertArrayEquals(bytes(0x1D, 0x21, 0x11), EscPosBuilder(32).size(2, 2).build())
        assertArrayEquals(bytes(0x1D, 0x21, 0x00), EscPosBuilder(32).size(0, 0).build()) // clamped to 1x1
    }

    @Test fun columnsFillExactlyOneLine() {
        val out = String(EscPosBuilder(32).columns("Coffee x2", "6.00").build(), Charsets.ISO_8859_1)
        assertEquals(32 + 1, out.length) // 32 chars + LF
        assertTrue(out.startsWith("Coffee x2"))
        assertTrue(out.endsWith("6.00\n"))
    }

    @Test fun columnsTruncateLongLeftTextAndKeepOneSpace() {
        val out = String(EscPosBuilder(16).columns("A very long item name", "10.00").build(), Charsets.ISO_8859_1)
        assertEquals(17, out.length)
        assertTrue(out.endsWith(" 10.00\n") || out.substring(0, 16).endsWith("10.00"))
    }

    @Test fun qrStoresLengthPlusThree() {
        val out = EscPosBuilder(32).qr("HELLO").build()
        // Find the store command: GS ( k pL pH 31 50 30
        val i = out.indices.first { out[it] == 0x31.toByte() && out[it + 1] == 0x50.toByte() }
        assertEquals(8, out[i - 2].toInt()) // pL = 5 chars + 3
        assertEquals(0, out[i - 1].toInt())
        assertEquals("HELLO", String(out, i + 3, 5))
    }

    @Test fun partialCutCommand() {
        assertArrayEquals(bytes(0x1D, 0x56, 66, 3), EscPosBuilder(32).cut(partial = true).build())
    }

    @Test fun testPageOnlyCutsWhenPrinterHasCutter() {
        val driver = EscPosDriver { LocalDateTime.of(2026, 1, 1, 12, 0) }
        val cut = bytes(0x1D, 0x56, 66, 3)
        fun contains(hay: ByteArray, needle: ByteArray) =
            hay.indices.any { i -> i + needle.size <= hay.size && needle.indices.all { hay[i + it] == needle[it] } }
        assertTrue(!contains(driver.testPage(printer), cut))
        assertTrue(contains(driver.testPage(printer.copy(hasCutter = true)), cut))
    }

    @Test fun testPageRulerMatchesPaperWidth() {
        val driver = EscPosDriver { LocalDateTime.of(2026, 1, 1, 12, 0) }
        for (w in PaperWidth.entries) {
            val text = String(driver.testPage(printer.copy(paperWidth = w)), Charsets.ISO_8859_1)
            val ruler = "1234567890".repeat(w.charsPerLine / 10 + 1).take(w.charsPerLine)
            assertTrue("ruler for ${w.mm}mm", text.contains(ruler + "\n"))
        }
    }

    @Test fun parsesHealthyStatus() {
        val s = EscPosDriver().parseStatus(bytes(0x12, 0x12))
        assertNotNull(s)
        assertNull(s!!.toError())
    }

    @Test fun parsesCoverOpenAndPaperOut() {
        val driver = EscPosDriver()
        assertTrue(driver.parseStatus(bytes(0x16, 0x12))!!.coverOpen)
        assertTrue(driver.parseStatus(bytes(0x12, 0x72))!!.paperOut)
    }

    @Test fun ignoresGarbageReplies() {
        val driver = EscPosDriver()
        assertNull(driver.parseStatus(bytes(0xFF, 0x00)))
        assertNull(driver.parseStatus(bytes(0x12)))
    }
}
