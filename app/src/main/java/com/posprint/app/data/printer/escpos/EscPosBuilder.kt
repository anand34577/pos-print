package com.posprint.app.data.printer.escpos

import com.posprint.app.domain.render.MonoBitmap
import com.posprint.app.domain.template.BarcodeType
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

enum class Align(val n: Int) { LEFT(0), CENTER(1), RIGHT(2) }

enum class QrLevel(val n: Int) { L(48), M(49), Q(50), H(51) }

/**
 * Fluent ESC/POS byte builder. Pure JVM so it can be unit-tested without a device.
 * Text is encoded with [charsetName] (matching the code page selected on the printer);
 * unmappable characters print as '?', so callers use [canEncode] to route such text to bitmap rendering.
 */
class EscPosBuilder(val charsPerLine: Int, charsetName: String = "IBM437") {
    private val out = ByteArrayOutputStream()
    private val charset: Charset =
        runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.ISO_8859_1)

    fun build(): ByteArray = out.toByteArray()

    fun canEncode(s: String): Boolean = charset.newEncoder().canEncode(s)

    fun raw(vararg bytes: Int) = apply { bytes.forEach { out.write(it and 0xFF) } }
    fun raw(bytes: ByteArray) = apply { out.write(bytes, 0, bytes.size) }

    fun init() = raw(ESC, 0x40)
    fun align(a: Align) = raw(ESC, 0x61, a.n)
    fun bold(on: Boolean) = raw(ESC, 0x45, if (on) 1 else 0)
    fun underline(on: Boolean) = raw(ESC, 0x2D, if (on) 1 else 0)
    fun invert(on: Boolean) = raw(GS, 0x42, if (on) 1 else 0)
    fun fontB(on: Boolean) = raw(ESC, 0x4D, if (on) 1 else 0)

    /** Character magnification, 1..8 each. */
    fun size(width: Int = 1, height: Int = 1): EscPosBuilder {
        val w = (width.coerceIn(1, 8) - 1) shl 4
        val h = height.coerceIn(1, 8) - 1
        return raw(GS, 0x21, w or h)
    }

    fun lineSpacing(dots: Int?) = if (dots == null) raw(ESC, 0x32) else raw(ESC, 0x33, dots.coerceIn(0, 255))
    fun codePage(n: Int) = raw(ESC, 0x74, n)
    fun leftMargin(dots: Int) = raw(GS, 0x4C, dots and 0xFF, (dots shr 8) and 0xFF)

    /** Epson-style print density (GS ( K). Best effort: some clones ignore it. */
    fun density(n: Int) = raw(GS, 0x28, 0x4B, 2, 0, 0x31, n.coerceIn(0, 8))

    fun text(s: String) = apply { raw(s.toByteArray(charset)) }
    fun line(s: String = "") = text(s).raw(LF)
    fun feed(lines: Int = 1) = raw(ESC, 0x64, lines.coerceIn(0, 255))

    fun divider(ch: Char = '-') = line(ch.toString().repeat(charsPerLine))

    /** Left text and right text on one line, padded to the paper width. */
    fun columns(left: String, right: String): EscPosBuilder {
        val r = right.take(charsPerLine)
        val room = (charsPerLine - r.length - 1).coerceAtLeast(0)
        return line(left.take(room).padEnd(charsPerLine - r.length) + r)
    }

    /** GS v 0 raster image, sent in bands so small printer buffers do not overflow. */
    fun raster(m: MonoBitmap, bandRows: Int = 128): EscPosBuilder {
        val wb = m.rowBytes
        var y = 0
        while (y < m.height) {
            val h = minOf(bandRows, m.height - y)
            raw(GS, 0x76, 0x30, 0, wb and 0xFF, wb shr 8, h and 0xFF, h shr 8)
            raw(m.data.copyOfRange(y * wb, (y + h) * wb))
            y += h
        }
        return this
    }

    fun qr(data: String, moduleSize: Int = 6, level: QrLevel = QrLevel.M): EscPosBuilder {
        val payload = data.toByteArray(Charsets.UTF_8)
        val len = payload.size + 3
        raw(GS, 0x28, 0x6B, 4, 0, 0x31, 0x41, 0x32, 0) // model 2
        raw(GS, 0x28, 0x6B, 3, 0, 0x31, 0x43, moduleSize.coerceIn(1, 16))
        raw(GS, 0x28, 0x6B, 3, 0, 0x31, 0x45, level.n)
        raw(GS, 0x28, 0x6B, len and 0xFF, len shr 8, 0x31, 0x50, 0x30).raw(payload)
        return raw(GS, 0x28, 0x6B, 3, 0, 0x31, 0x51, 0x30)
    }

    fun pdf417(data: String, columns: Int = 0, moduleWidth: Int = 3, rowHeight: Int = 3, ecc: Int = 3): EscPosBuilder {
        val payload = data.toByteArray(Charsets.UTF_8)
        val len = payload.size + 3
        raw(GS, 0x28, 0x6B, 3, 0, 0x30, 0x41, columns.coerceIn(0, 30))
        raw(GS, 0x28, 0x6B, 3, 0, 0x30, 0x42, 0)
        raw(GS, 0x28, 0x6B, 3, 0, 0x30, 0x43, moduleWidth.coerceIn(2, 8))
        raw(GS, 0x28, 0x6B, 3, 0, 0x30, 0x44, rowHeight.coerceIn(2, 8))
        raw(GS, 0x28, 0x6B, 4, 0, 0x30, 0x45, 0x30, 48 + ecc.coerceIn(0, 8))
        raw(GS, 0x28, 0x6B, len and 0xFF, len shr 8, 0x30, 0x50, 0x30).raw(payload)
        return raw(GS, 0x28, 0x6B, 3, 0, 0x30, 0x51, 0x30)
    }

    /** 1D barcode using GS k function B. Data must already be valid for [type]. */
    fun barcode(data: String, type: BarcodeType, height: Int = 80, width: Int = 2, textBelow: Boolean = true): EscPosBuilder {
        if (type == BarcodeType.PDF417) return pdf417(data)
        val (m, payload) = when (type) {
            BarcodeType.CODE128 -> 73 to ("{B$data")
            BarcodeType.CODE39 -> 69 to data.uppercase()
            BarcodeType.EAN13 -> 67 to data
            BarcodeType.UPCA -> 65 to data
            BarcodeType.ITF -> 70 to data
            BarcodeType.PDF417 -> error("handled above")
        }
        // GS k function B carries the length in one byte, so longer data would overflow into garbage.
        val bytes = payload.toByteArray(Charsets.US_ASCII).let { if (it.size > 255) it.copyOf(255) else it }
        raw(GS, 0x68, height.coerceIn(1, 255))
        raw(GS, 0x77, width.coerceIn(2, 6))
        raw(GS, 0x48, if (textBelow) 2 else 0)
        return raw(GS, 0x6B, m, bytes.size).raw(bytes)
    }

    fun barcode128(data: String, height: Int = 80, width: Int = 2, textBelow: Boolean = true) =
        barcode(data, BarcodeType.CODE128, height, width, textBelow)

    /** Cut after feeding past the cutter. Gate on the printer profile. */
    fun cut(partial: Boolean = true) = raw(GS, 0x56, if (partial) 66 else 65, 3)

    fun openDrawer(pin: Int = 0) = raw(ESC, 0x70, pin.coerceIn(0, 1), 25, 250)
    fun beep(times: Int = 2, units: Int = 3) = raw(ESC, 0x42, times.coerceIn(1, 9), units.coerceIn(1, 9))

    companion object {
        const val ESC = 0x1B
        const val GS = 0x1D
        const val LF = 0x0A
    }
}
