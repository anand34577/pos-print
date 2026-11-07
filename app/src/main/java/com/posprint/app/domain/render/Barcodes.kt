package com.posprint.app.domain.render

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.oned.Code39Writer
import com.google.zxing.oned.EAN13Writer
import com.google.zxing.oned.ITFWriter
import com.google.zxing.oned.UPCAWriter
import com.google.zxing.pdf417.PDF417Writer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.posprint.app.domain.template.BarcodeType

/** zxing is pure Java, so barcode geometry can be shared by previews, label planning and unit tests. */
object BarcodeMatrix {

    fun oneD(type: BarcodeType, data: String): BitMatrix = when (type) {
        BarcodeType.CODE128 -> Code128Writer().encode(data, BarcodeFormat.CODE_128, 1, 1)
        BarcodeType.CODE39 -> Code39Writer().encode(data.uppercase(), BarcodeFormat.CODE_39, 1, 1)
        BarcodeType.EAN13 -> EAN13Writer().encode(data, BarcodeFormat.EAN_13, 1, 1)
        BarcodeType.UPCA -> UPCAWriter().encode(data, BarcodeFormat.UPC_A, 1, 1)
        BarcodeType.ITF -> ITFWriter().encode(data, BarcodeFormat.ITF, 1, 1)
        BarcodeType.PDF417 -> PDF417Writer().encode(data, BarcodeFormat.PDF_417, 1, 1)
    }

    fun qr(data: String): BitMatrix =
        QRCodeWriter().encode(data, BarcodeFormat.QR_CODE, 0, 0, mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 0,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ))

    /** Null if [data] is valid for [type], otherwise a message fit to show the user. */
    fun validate(type: BarcodeType, data: String): String? = when {
        data.isBlank() -> "No data"
        else -> try { oneD(type, data); null } catch (e: Exception) { explain(type, e) }
    }

    fun validateQr(data: String): String? = if (data.isBlank()) "No data" else try { qr(data); null } catch (e: Exception) { e.message ?: "Cannot encode" }

    private fun explain(type: BarcodeType, e: Exception) = when (type) {
        BarcodeType.EAN13 -> "EAN-13 needs 12 or 13 digits"
        BarcodeType.UPCA -> "UPC-A needs 11 or 12 digits"
        BarcodeType.ITF -> "ITF needs an even number of digits"
        BarcodeType.CODE39 -> "Code 39 allows A-Z, 0-9 and - . \$ / + % space"
        else -> e.message ?: "Cannot encode"
    }

    fun moduleCount(type: BarcodeType, data: String): Int = oneD(type, data).width

    /** Scales a code matrix into a [cw] x [ch] 1-bit bitmap, centred, with integer module sizes. */
    fun toMono(m: BitMatrix, cw: Int, ch: Int, square: Boolean): MonoBitmap {
        val sx = maxOf(1, cw / m.width)
        val sy = if (square) minOf(sx, maxOf(1, ch / m.height)) else ch.coerceAtLeast(1)
        val sxx = if (square) minOf(sx, sy) else sx
        val ow = m.width * sxx
        val oh = if (square) m.height * sy else sy
        val offX = ((cw - ow) / 2).coerceAtLeast(0)
        val offY = if (square) ((ch - oh) / 2).coerceAtLeast(0) else 0
        val rb = (cw + 7) / 8
        val data = ByteArray(rb * ch)
        for (y in 0 until minOf(oh, ch - offY)) for (x in 0 until minOf(ow, cw - offX)) {
            if (m.get(x / sxx, if (square) y / sy else 0)) {
                val px = offX + x
                val idx = (offY + y) * rb + px / 8
                data[idx] = (data[idx].toInt() or (0x80 shr (px % 8))).toByte()
            }
        }
        return MonoBitmap(cw, ch, data)
    }
}

object MonoOps {
    fun get(m: MonoBitmap, x: Int, y: Int) = (m.data[y * m.rowBytes + x / 8].toInt() and (0x80 shr (x % 8))) != 0

    /** Rotates clockwise by 0/90/180/270 degrees. */
    fun rotate(m: MonoBitmap, deg: Int): MonoBitmap {
        val d = ((deg % 360) + 360) % 360
        if (d == 0) return m
        val nw = if (d == 180) m.width else m.height
        val nh = if (d == 180) m.height else m.width
        val rb = (nw + 7) / 8
        val out = ByteArray(rb * nh)
        for (y in 0 until m.height) for (x in 0 until m.width) if (get(m, x, y)) {
            val (nx, ny) = when (d) {
                90 -> (m.height - 1 - y) to x
                180 -> (m.width - 1 - x) to (m.height - 1 - y)
                else -> y to (m.width - 1 - x)
            }
            out[ny * rb + nx / 8] = (out[ny * rb + nx / 8].toInt() or (0x80 shr (nx % 8))).toByte()
        }
        return MonoBitmap(nw, nh, out)
    }

    /** Inverts bits (some label languages use 0 = black). Padding bits are left as 1 (white in that polarity). */
    fun inverted(m: MonoBitmap): ByteArray = ByteArray(m.data.size) { (m.data[it].toInt().inv() and 0xFF).toByte() }
}
