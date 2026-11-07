package com.posprint.app.domain.model

enum class ConnectionType(val label: String) {
    BLUETOOTH("Bluetooth"),
    USB("USB"),
}

/** Command language a printer speaks. */
enum class PrinterLanguage(val label: String, val isLabel: Boolean) {
    ESC_POS("ESC/POS (receipt)", false),
    TSPL("TSPL (label)", true),
    ZPL("ZPL (label)", true),
    EPL("EPL (label)", true),
    CPCL("CPCL (label)", true),
}

enum class PaperWidth(val mm: Int, val dots: Int, val charsPerLine: Int) {
    MM58(58, 384, 32),
    MM80(80, 576, 48),
}

/** Code pages selectable on ESC/POS printers. Text outside the page falls back to bitmap rendering. */
enum class CodePage(val escN: Int, val charset: String, val label: String) {
    CP437(0, "IBM437", "CP437 (USA)"),
    CP850(2, "IBM850", "CP850 (Western Europe)"),
    CP858(19, "IBM00858", "CP858 (Euro)"),
    WPC1252(16, "windows-1252", "WPC1252 (Latin)"),
    CP866(17, "IBM866", "CP866 (Cyrillic)"),
}

/** A saved printer plus its profile. */
@kotlinx.serialization.Serializable
data class Printer(
    val id: Long = 0,
    val name: String,
    val connection: ConnectionType,
    /** Bluetooth MAC, or "vendorId:productId" for USB. */
    val address: String,
    val language: PrinterLanguage = PrinterLanguage.ESC_POS,
    val paperWidth: PaperWidth = PaperWidth.MM58,
    val hasCutter: Boolean = false,
    val hasDrawer: Boolean = false,
    val isDefault: Boolean = false,
    val codePage: CodePage = CodePage.CP437,
    val dpi: Int = 203,
    /** Override characters per line when a printer's real width differs from the preset. */
    val charsOverride: Int? = null,
    /** Print density 0..8, null = leave printer default. */
    val density: Int? = null,
    /** Extra line spacing in dots, null = printer default. */
    val lineSpacing: Int? = null,
    /** Left margin in dots. */
    val marginDots: Int = 0,
) {
    val cols: Int get() = charsOverride ?: paperWidth.charsPerLine
}

/** A device found while scanning, before it is saved as a [Printer]. */
data class DiscoveredDevice(
    val name: String,
    val address: String,
    val connection: ConnectionType,
    val paired: Boolean = false,
    val likelyPrinter: Boolean = false,
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }
