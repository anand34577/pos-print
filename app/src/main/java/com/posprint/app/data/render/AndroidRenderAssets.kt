package com.posprint.app.data.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Base64
import com.posprint.app.data.SettingsStore
import com.posprint.app.domain.render.GrayImage
import com.posprint.app.domain.render.RenderAssets
import com.posprint.app.domain.template.Alignment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Android-backed images and bitmap text. Text layout uses StaticLayout, so Arabic shaping, RTL and CJK just work. */
@Singleton
class AndroidRenderAssets @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) : RenderAssets {

    fun loadBitmap(source: String): Bitmap? = runCatching {
        when {
            source == "logo" -> settings.logoFile.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            source.startsWith("data:") -> Base64.decode(source.substringAfter("base64,"), Base64.DEFAULT)
                .let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            else -> context.contentResolver.openInputStream(Uri.parse(source))?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** Reads a picked image, downsizes it (max 576 px wide) and returns a portable data: URI for templates. */
    fun toDataUri(uri: Uri): String? = runCatching {
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val s = minOf(1f, 576f / bmp.width)
        val scaled = if (s < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true) else bmp
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    override fun image(source: String, maxWidthDots: Int): GrayImage? {
        val bmp = loadBitmap(source) ?: return null
        val scale = maxWidthDots.toFloat() / bmp.width
        val scaled = Bitmap.createScaledBitmap(bmp, maxWidthDots, (bmp.height * scale).toInt().coerceAtLeast(1), true)
        return toGray(scaled)
    }

    override fun text(text: String, align: Alignment, bold: Boolean, italic: Boolean, sizeMul: Int, widthDots: Int): GrayImage {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 24f * sizeMul.coerceAtLeast(1)
            typeface = Typeface.create(Typeface.SANS_SERIF, when {
                bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL
            })
        }
        val layoutAlign = when (align) {
            Alignment.LEFT -> Layout.Alignment.ALIGN_NORMAL
            Alignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            Alignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthDots).setAlignment(layoutAlign).build()
        val bmp = Bitmap.createBitmap(widthDots, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        layout.draw(c)
        return toGray(bmp)
    }

    override fun labelText(text: String, bold: Boolean, align: Alignment, fontMm: Double, widthDots: Int, heightDots: Int, rotation: Int, dpi: Int): GrayImage {
        val bmp = LabelRaster.textBox(text, bold, align, fontMm, widthDots, heightDots, dpi)
        return toGray(rotate(bmp, rotation))
    }

    override fun labelImage(source: String, widthDots: Int, heightDots: Int, rotation: Int): GrayImage? {
        val src = loadBitmap(source) ?: return null
        val box = Bitmap.createBitmap(widthDots.coerceAtLeast(1), heightDots.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(box)
        c.drawColor(Color.WHITE)
        val s = minOf(box.width.toFloat() / src.width, box.height.toFloat() / src.height)
        val w = (src.width * s).toInt().coerceAtLeast(1)
        val h = (src.height * s).toInt().coerceAtLeast(1)
        c.drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), (box.width - w) / 2f, (box.height - h) / 2f, null)
        return toGray(rotate(box, rotation))
    }

    private fun rotate(b: Bitmap, deg: Int): Bitmap =
        if (deg % 360 == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)

    companion object {
        fun toGray(bmp: Bitmap): GrayImage {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val out = ByteArray(px.size)
            for (i in px.indices) {
                val p = px[i]
                val a = Color.alpha(p) / 255f
                val g = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
                out[i] = (g * a + 255f * (1 - a)).toInt().coerceIn(0, 255).toByte()
            }
            return GrayImage(bmp.width, bmp.height, out)
        }
    }
}
