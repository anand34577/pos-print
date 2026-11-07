package com.posprint.app.domain.printer

import com.posprint.app.domain.model.Printer
import com.posprint.app.domain.model.PrinterLanguage
import com.posprint.app.domain.model.PrinterStatus

/**
 * Turns intent ("print a test page") into bytes for one printer language.
 * Pure Kotlin: no Android types, so every driver is unit-testable on the JVM.
 * Add a language by implementing this and registering it in [DriverRegistry].
 */
interface PrinterDriver {
    val language: PrinterLanguage

    fun testPage(printer: Printer): ByteArray

    /** The printer's own built-in self-test sheet, where the language supports one. */
    fun selfTest(): ByteArray

    /** Bytes that ask the printer for its status. Empty if the language has no such query. */
    val statusRequest: ByteArray

    /** How many reply bytes [statusRequest] produces. */
    val statusReplySize: Int

    /** Null when [reply] is not a valid status reply (many cheap clones never answer). */
    fun parseStatus(reply: ByteArray): PrinterStatus?
}
