package com.posprint.app.data.printer.escpos

import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.model.PrinterStatus
import com.posprint.app.domain.printer.PrinterDriver
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EscPosDriver(private val clock: () -> LocalDateTime = { LocalDateTime.now() }) : PrinterDriver {

    override val language = PrinterLanguage.ESC_POS

    override fun testPage(printer: Printer): ByteArray {
        val cols = printer.cols
        val b = EscPosBuilder(cols, printer.codePage.charset).init().codePage(printer.codePage.escN)
        b.align(Align.CENTER).bold(true).size(2, 2).line("POS PRINT").size(1, 1).bold(false)
        b.line("Test print")
        b.line(clock().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        b.feed(1).align(Align.LEFT).divider()
        b.line(printer.name.take(cols))
        b.columns("Connection", printer.connection.label)
        b.columns("Paper", "${printer.paperWidth.mm} mm")
        b.columns("Columns", cols.toString())
        b.divider()
        // Ruler: if the last digit is cut off or wraps, the paper width setting is wrong.
        b.line("1234567890".repeat(cols / 10 + 1).take(cols))
        b.divider()
        b.bold(true).line("Bold").bold(false)
        b.underline(true).line("Underline").underline(false)
        b.invert(true).line(" Inverted ").invert(false)
        b.size(2, 1).line("Wide").size(1, 2).line("Tall").size(1, 1)
        b.align(Align.LEFT).line("Left")
        b.align(Align.CENTER).line("Center")
        b.align(Align.RIGHT).line("Right")
        b.align(Align.LEFT).divider()
        b.columns("Item", "Price")
        b.columns("Coffee x2", "6.00")
        b.columns("Total", "6.00")
        b.divider().feed(1)
        b.align(Align.CENTER).qr("POS-PRINT-TEST", moduleSize = 6).feed(1)
        b.barcode128("123456789012").feed(1)
        b.line("Printer is ready.")
        b.align(Align.LEFT)
        if (printer.hasCutter) b.feed(2).cut() else b.feed(4)
        return b.build()
    }

    // GS ( A: execute test print, roll paper, status sheet. Epson-compatible; some clones only
    // support the physical button (hold Feed while powering on).
    override fun selfTest() = byteArrayOf(0x1D, 0x28, 0x41, 0x02, 0x00, 0x00, 0x02)

    // DLE EOT 2 (offline cause) then DLE EOT 4 (paper roll sensor)
    override val statusRequest = byteArrayOf(0x10, 0x04, 0x02, 0x10, 0x04, 0x04)
    override val statusReplySize = 2

    override fun parseStatus(reply: ByteArray): PrinterStatus? {
        if (reply.size < 2) return null
        val offline = reply[0].toInt() and 0xFF
        val paper = reply[1].toInt() and 0xFF
        // Bits 0,1,4,7 of a real reply are fixed at 0,1,1,0. Anything else is noise from a clone.
        if (offline and 0x93 != 0x12 || paper and 0x93 != 0x12) return null
        return PrinterStatus(
            coverOpen = offline and 0x04 != 0,
            errorFlag = offline and 0x40 != 0,
            paperOut = paper and 0x60 != 0 || offline and 0x20 != 0,
            paperNearEnd = paper and 0x0C != 0,
        )
    }
}
