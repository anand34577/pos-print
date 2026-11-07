package com.posprint.app.domain.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class TemplateKind { RECEIPT, LABEL, DOCUMENT }
enum class Alignment { LEFT, CENTER, RIGHT }
enum class BarcodeType(val label: String) {
    CODE128("Code 128"), CODE39("Code 39"), EAN13("EAN-13"), UPCA("UPC-A"), ITF("ITF"), PDF417("PDF417"),
}
enum class DitherMode(val label: String) { THRESHOLD("Threshold"), FLOYD_STEINBERG("Floyd-Steinberg"), ORDERED("Ordered") }
enum class MediaType(val label: String) { GAP("Gap"), BLACK_MARK("Black mark"), CONTINUOUS("Continuous") }
enum class CommandKind { CUT, DRAWER, BEEP }

// ---------- Receipt / document blocks ----------

@Serializable
sealed interface Block { val showIf: String? }

@Serializable @SerialName("text")
data class TextBlock(
    val text: String = "",
    val align: Alignment = Alignment.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val invert: Boolean = false,
    val italic: Boolean = false,
    val widthMul: Int = 1,
    val heightMul: Int = 1,
    val fontB: Boolean = false,
    override val showIf: String? = null,
) : Block

@Serializable @SerialName("divider")
data class DividerBlock(val char: String = "-", override val showIf: String? = null) : Block

@Serializable @SerialName("columns")
data class ColumnsBlock(
    val left: String = "",
    val right: String = "",
    val bold: Boolean = false,
    override val showIf: String? = null,
) : Block

@Serializable
data class Column(val title: String, val field: String, val width: Int = 0, val align: Alignment = Alignment.LEFT)

@Serializable @SerialName("table")
data class TableBlock(
    val source: String = "items",
    val columns: List<Column> = listOf(
        Column("Item", "name"),
        Column("Qty", "qty", 4, Alignment.RIGHT),
        Column("Total", "total", 9, Alignment.RIGHT),
    ),
    val showHeader: Boolean = true,
    override val showIf: String? = null,
) : Block

/** [source] is "logo" (business logo), a data:image base64 URI, or a content URI. */
@Serializable @SerialName("image")
data class ImageBlock(
    val source: String = "logo",
    val widthPct: Int = 60,
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
    val align: Alignment = Alignment.CENTER,
    override val showIf: String? = null,
) : Block

@Serializable @SerialName("qr")
data class QrBlock(
    val data: String = "{{qr}}",
    val size: Int = 6,
    val align: Alignment = Alignment.CENTER,
    override val showIf: String? = null,
) : Block

@Serializable @SerialName("barcode")
data class BarcodeBlock(
    val data: String = "{{barcode}}",
    val type: BarcodeType = BarcodeType.CODE128,
    val height: Int = 80,
    val align: Alignment = Alignment.CENTER,
    override val showIf: String? = null,
) : Block

@Serializable @SerialName("spacer")
data class SpacerBlock(val lines: Int = 1, override val showIf: String? = null) : Block

@Serializable @SerialName("command")
data class CommandBlock(val kind: CommandKind = CommandKind.DRAWER, override val showIf: String? = null) : Block

/** Business name, address, tax id from the business profile (plus logo). */
@Serializable @SerialName("header")
data class HeaderBlock(val showLogo: Boolean = true, override val showIf: String? = null) : Block

/** Business footer message. */
@Serializable @SerialName("footer")
data class FooterBlock(override val showIf: String? = null) : Block

// ---------- Label elements ----------

@Serializable
data class LabelSpec(
    val widthMm: Double = 50.0,
    val heightMm: Double = 30.0,
    val media: MediaType = MediaType.GAP,
    val gapMm: Double = 3.0,
    val dpi: Int = 203,
)

@Serializable
sealed interface LabelElement {
    val id: String
    val xMm: Double
    val yMm: Double
    val wMm: Double
    val hMm: Double
    /** 0, 90, 180 or 270 degrees clockwise. */
    val rotation: Int
    fun moved(x: Double, y: Double, w: Double = wMm, h: Double = hMm, rot: Int = rotation): LabelElement
}

@Serializable @SerialName("ltext")
data class LText(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val text: String = "{{product_name}}",
    val bold: Boolean = false,
    val align: Alignment = Alignment.LEFT,
    /** Font height in mm. */
    val fontMm: Double = 4.0,
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

@Serializable @SerialName("lbarcode")
data class LBarcode(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val data: String = "{{sku}}",
    val type: BarcodeType = BarcodeType.CODE128,
    val humanReadable: Boolean = true,
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

@Serializable @SerialName("lqr")
data class LQr(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val data: String = "{{sku}}",
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

@Serializable @SerialName("limage")
data class LImage(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val source: String = "logo",
    val dither: DitherMode = DitherMode.FLOYD_STEINBERG,
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

/** A line: horizontal along [wMm], or vertical when rotation is 90/270. Thickness in mm. */
@Serializable @SerialName("lline")
data class LLine(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val thicknessMm: Double = 0.4,
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

@Serializable @SerialName("lbox")
data class LBox(
    override val id: String,
    override val xMm: Double, override val yMm: Double, override val wMm: Double, override val hMm: Double,
    override val rotation: Int = 0,
    val thicknessMm: Double = 0.4,
) : LabelElement {
    override fun moved(x: Double, y: Double, w: Double, h: Double, rot: Int) = copy(xMm = x, yMm = y, wMm = w, hMm = h, rotation = rot)
}

// ---------- Template ----------

@Serializable
data class TemplateData(
    val vars: Map<String, String> = emptyMap(),
    val lists: Map<String, List<Map<String, String>>> = emptyMap(),
)

@Serializable
data class Template(
    val id: Long = 0,
    val name: String,
    val kind: TemplateKind,
    val printerId: Long? = null,
    val blocks: List<Block> = emptyList(),
    val label: LabelSpec = LabelSpec(),
    val elements: List<LabelElement> = emptyList(),
    /** Sample values used for the live preview and as defaults when printing. */
    val sample: TemplateData = TemplateData(),
    val builtIn: Boolean = false,
    /** Document paper for DOCUMENT kind: A4, A5 or LETTER. */
    val paper: String = "A4",
)

object TemplateJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_t" // must not clash with fields named "type" (barcodes)
        prettyPrint = true
    }

    fun encode(t: Template): String = json.encodeToString(Template.serializer(), t)
    fun decode(s: String): Template = json.decodeFromString(Template.serializer(), s)
}
