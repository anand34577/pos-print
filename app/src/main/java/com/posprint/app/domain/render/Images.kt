package com.posprint.app.domain.render

import com.posprint.app.domain.template.DitherMode

/** 8-bit grayscale, 255 = white. Alpha must already be composited over white. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init { require(pixels.size == width * height) }
    fun at(x: Int, y: Int) = pixels[y * width + x].toInt() and 0xFF
}

/** 1-bit image, rows padded to whole bytes, bit set = black. */
class MonoBitmap(val width: Int, val height: Int, val data: ByteArray) {
    val rowBytes: Int get() = (width + 7) / 8
}

object Dither {
    private val BAYER4 = arrayOf(
        intArrayOf(0, 8, 2, 10), intArrayOf(12, 4, 14, 6), intArrayOf(3, 11, 1, 9), intArrayOf(15, 7, 13, 5),
    )

    fun toMono(img: GrayImage, mode: DitherMode, threshold: Int = 128): MonoBitmap {
        val w = img.width
        val h = img.height
        val black = Array(h) { BooleanArray(w) }
        when (mode) {
            DitherMode.THRESHOLD -> for (y in 0 until h) for (x in 0 until w) black[y][x] = img.at(x, y) < threshold
            DitherMode.ORDERED -> for (y in 0 until h) for (x in 0 until w) {
                val t = (BAYER4[y and 3][x and 3] * 16 + 8) * threshold / 128
                black[y][x] = img.at(x, y) < t
            }
            DitherMode.FLOYD_STEINBERG -> {
                val buf = FloatArray(w * h) { (img.pixels[it].toInt() and 0xFF).toFloat() }
                for (y in 0 until h) for (x in 0 until w) {
                    val old = buf[y * w + x]
                    val new = if (old < threshold) 0f else 255f
                    black[y][x] = new == 0f
                    val err = old - new
                    if (x + 1 < w) buf[y * w + x + 1] += err * 7 / 16
                    if (y + 1 < h) {
                        if (x > 0) buf[(y + 1) * w + x - 1] += err * 3 / 16
                        buf[(y + 1) * w + x] += err * 5 / 16
                        if (x + 1 < w) buf[(y + 1) * w + x + 1] += err * 1 / 16
                    }
                }
            }
        }
        val rb = (w + 7) / 8
        val out = ByteArray(rb * h)
        for (y in 0 until h) for (x in 0 until w) if (black[y][x]) {
            out[y * rb + x / 8] = (out[y * rb + x / 8].toInt() or (0x80 shr (x % 8))).toByte()
        }
        return MonoBitmap(w, h, out)
    }
}

/** Platform services the pure renderers need: images and bitmap text. Faked in unit tests. */
interface RenderAssets {
    /** "logo", a data: URI or a content URI, scaled to at most [maxWidthDots] wide. Null if unavailable. */
    fun image(source: String, maxWidthDots: Int): GrayImage?

    /** Text rendered to a bitmap for scripts the printer cannot print natively. */
    fun text(text: String, align: com.posprint.app.domain.template.Alignment, bold: Boolean, italic: Boolean,
             sizeMul: Int, widthDots: Int): GrayImage

    /** Label text at exact size: [fontMm] tall, within [widthDots] x [heightDots], rotated by [rotation]. */
    fun labelText(text: String, bold: Boolean, align: com.posprint.app.domain.template.Alignment, fontMm: Double,
                  widthDots: Int, heightDots: Int, rotation: Int, dpi: Int): GrayImage

    /** Image fitted into the box and rotated. */
    fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int): GrayImage?
}
