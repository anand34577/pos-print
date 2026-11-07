package com.posprint.app.domain.render

import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeBlock
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.Block
import com.posprint.app.domain.template.ColumnsBlock
import com.posprint.app.domain.template.CommandBlock
import com.posprint.app.domain.template.CommandKind
import com.posprint.app.domain.template.DitherMode
import com.posprint.app.domain.template.DividerBlock
import com.posprint.app.domain.template.FooterBlock
import com.posprint.app.domain.template.HeaderBlock
import com.posprint.app.domain.template.ImageBlock
import com.posprint.app.domain.template.QrBlock
import com.posprint.app.domain.template.SpacerBlock
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TextBlock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ---------- Resolved blocks: what both the preview and the printer consume ----------

sealed interface RBlock
data class RText(
    val text: String,
    val align: Alignment = Alignment.LEFT,
    val bold: Boolean = false,
    val underline: Boolean = false,
    val invert: Boolean = false,
    val italic: Boolean = false,
    val widthMul: Int = 1,
    val heightMul: Int = 1,
    val fontB: Boolean = false,
) : RBlock
data class RImage(val source: String, val widthPct: Int, val dither: DitherMode, val align: Alignment) : RBlock
data class RQr(val data: String, val size: Int, val align: Alignment) : RBlock
data class RBarcode(val data: String, val type: BarcodeType, val height: Int, val align: Alignment) : RBlock
data class RSpacer(val lines: Int) : RBlock
data class RCommand(val kind: CommandKind) : RBlock

data class BusinessInfo(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val taxId: String = "",
    val footer: String = "",
    val hasLogo: Boolean = false,
)

data class Formatter(
    val currency: String = "",
    val decimals: Int = 2,
    val thousands: Boolean = true,
    val datePattern: String = "yyyy-MM-dd",
    val timePattern: String = "HH:mm",
    val currencyAfter: Boolean = false,
) {
    fun number(raw: String): String {
        val v = raw.trim().toBigDecimalOrNull() ?: return raw
        val decimals = decimals.coerceIn(0, 6)
        val s = v.setScale(decimals, RoundingMode.HALF_UP)
        return if (thousands) java.text.DecimalFormat(if (decimals > 0) "#,##0.${"0".repeat(decimals)}" else "#,##0",
            java.text.DecimalFormatSymbols(java.util.Locale.US)).format(s) else s.toPlainString()
    }

    fun money(raw: String): String {
        val n = number(raw)
        if (n == raw) return raw
        return when {
            currency.isBlank() -> n
            currencyAfter -> "$n $currency"
            else -> "$currency$n"
        }
    }
}

data class RenderContext(
    val cols: Int,
    val business: BusinessInfo = BusinessInfo(),
    val formatter: Formatter = Formatter(),
    val now: LocalDateTime = LocalDateTime.now(),
) {
    fun date() = formatSafe(formatter.datePattern, "yyyy-MM-dd")
    fun time() = formatSafe(formatter.timePattern, "HH:mm")
    fun dateTime() = "${date()} ${time()}"

    /** A bad pattern (hand-edited or from an old backup) must never crash preview or printing. */
    private fun formatSafe(pattern: String, fallback: String): String =
        runCatching { now.format(DateTimeFormatter.ofPattern(pattern)) }.getOrElse { now.format(DateTimeFormatter.ofPattern(fallback)) }
}

object Expr {
    private val CMP = Regex("^(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+)$")

    private fun truthy(v: String?): Boolean {
        if (v == null || v.isBlank()) return false
        if (v.equals("false", true)) return false
        v.trim().toBigDecimalOrNull()?.let { return it.signum() != 0 }
        return true
    }

    private fun operand(token: String, lookup: (String) -> String?): String {
        val t = token.trim()
        if (t.length >= 2 && (t.first() == '"' && t.last() == '"' || t.first() == '\'' && t.last() == '\'')) return t.substring(1, t.length - 1)
        if (t.toBigDecimalOrNull() != null) return t
        return lookup(t) ?: ""
    }

    private fun atom(expr: String, lookup: (String) -> String?): Boolean {
        val e = expr.trim()
        if (e.startsWith("!")) return !atom(e.substring(1), lookup)
        val m = CMP.matchEntire(e)
        if (m != null) {
            val a = operand(m.groupValues[1], lookup)
            val b = operand(m.groupValues[3], lookup)
            val na = a.trim().toBigDecimalOrNull()
            val nb = b.trim().toBigDecimalOrNull()
            val c = if (na != null && nb != null) na.compareTo(nb) else a.compareTo(b)
            return when (m.groupValues[2]) {
                ">" -> c > 0; "<" -> c < 0; ">=" -> c >= 0; "<=" -> c <= 0; "==" -> c == 0; else -> c != 0
            }
        }
        return truthy(lookup(e))
    }

    /** Supports `x`, `!x`, `a > 0`, `a == "yes"`, and `&&` / `||`. Blank means always true. */
    fun eval(expr: String?, lookup: (String) -> String?): Boolean {
        if (expr.isNullOrBlank()) return true
        val clean = expr.replace("{{", "").replace("}}", "")
        return clean.split("||").any { part -> part.split("&&").all { atom(it, lookup) } }
    }
}

object TemplateEngine {
    private val VAR = Regex("\\{\\{\\s*([\\w.]+)\\s*(?:\\|\\s*(\\w+))?\\s*\\}\\}") // braces escaped: Android's ICU regex rejects a bare '}'

    /** Names of {{variables}} a block list uses, excluding built-ins and list fields. */
    fun variablesUsed(blocks: List<Block>): List<String> {
        val names = linkedSetOf<String>()
        fun scan(s: String?) { s?.let { VAR.findAll(it).forEach { m -> names += m.groupValues[1] } } }
        fun scanExpr(s: String?) { s?.replace("{{", "")?.replace("}}", "")?.split(Regex("[|&!<>=\\s\"']+"))?.filter { it.matches(Regex("[A-Za-z_][\\w.]*")) }?.forEach { names += it } }
        blocks.forEach { b ->
            scanExpr(b.showIf)
            when (b) {
                is TextBlock -> scan(b.text)
                is ColumnsBlock -> { scan(b.left); scan(b.right) }
                is QrBlock -> scan(b.data)
                is BarcodeBlock -> scan(b.data)
                is ImageBlock -> scan(b.source)
                else -> Unit
            }
        }
        return names.filterNot { it.startsWith("business.") || it in BUILTIN }
    }

    val BUILTIN = setOf("date", "time", "datetime")

    fun substitute(text: String, lookup: (String) -> String?, ctx: RenderContext): String =
        VAR.replace(text) { m ->
            val raw = lookup(m.groupValues[1]) ?: ""
            when (m.groupValues[2].lowercase()) {
                "money" -> ctx.formatter.money(raw)
                "number" -> ctx.formatter.number(raw)
                "upper" -> raw.uppercase()
                "lower" -> raw.lowercase()
                else -> raw
            }
        }

    private fun globalLookup(data: TemplateData, ctx: RenderContext): (String) -> String? = { key ->
        data.vars[key] ?: when (key) {
            "business.name" -> ctx.business.name
            "business.address" -> ctx.business.address
            "business.phone" -> ctx.business.phone
            "business.taxId" -> ctx.business.taxId
            "business.footer" -> ctx.business.footer
            "date" -> ctx.date()
            "time" -> ctx.time()
            "datetime" -> ctx.dateTime()
            else -> null
        }
    }

    fun resolve(blocks: List<Block>, data: TemplateData, ctx: RenderContext): List<RBlock> {
        val lookup = globalLookup(data, ctx)
        val out = mutableListOf<RBlock>()
        for (b in blocks) {
            if (!Expr.eval(b.showIf, lookup)) continue
            when (b) {
                is TextBlock -> {
                    val width = (effectiveCols(ctx.cols, b.fontB) / b.widthMul.coerceAtLeast(1)).coerceAtLeast(1)
                    substitute(b.text, lookup, ctx).split("\n").forEach { paragraph ->
                        wrap(paragraph, width).forEach { line ->
                            out += RText(line, b.align, b.bold, b.underline, b.invert, b.italic, b.widthMul, b.heightMul, b.fontB)
                        }
                    }
                }
                is DividerBlock -> out += RText((b.char.firstOrNull() ?: '-').toString().repeat(ctx.cols))
                is ColumnsBlock -> out += RText(
                    twoColumns(substitute(b.left, lookup, ctx), substitute(b.right, lookup, ctx), ctx.cols), bold = b.bold,
                )
                is TableBlock -> out += table(b, data, lookup, ctx)
                is ImageBlock -> {
                    val src = substitute(b.source, lookup, ctx)
                    if (src.isNotBlank()) out += RImage(src, b.widthPct, b.dither, b.align)
                }
                is QrBlock -> substitute(b.data, lookup, ctx).takeIf { it.isNotBlank() }?.let { out += RQr(it, b.size, b.align) }
                is BarcodeBlock -> substitute(b.data, lookup, ctx).takeIf { it.isNotBlank() }?.let { out += RBarcode(it, b.type, b.height, b.align) }
                is SpacerBlock -> out += RSpacer(b.lines)
                is CommandBlock -> out += RCommand(b.kind)
                is HeaderBlock -> {
                    if (b.showLogo && ctx.business.hasLogo) out += RImage("logo", 50, DitherMode.FLOYD_STEINBERG, Alignment.CENTER)
                    val bz = ctx.business
                    if (bz.name.isNotBlank()) wrap(bz.name, ctx.cols / 2).forEach { out += RText(it, Alignment.CENTER, bold = true, widthMul = 2, heightMul = 2) }
                    listOf(bz.address, bz.phone, if (bz.taxId.isNotBlank()) "Tax ID: ${bz.taxId}" else "")
                        .filter { it.isNotBlank() }
                        .forEach { s -> s.split("\n").forEach { p -> wrap(p, ctx.cols).forEach { out += RText(it, Alignment.CENTER) } } }
                }
                is FooterBlock -> ctx.business.footer.takeIf { it.isNotBlank() }?.split("\n")?.forEach { p ->
                    wrap(p, ctx.cols).forEach { out += RText(it, Alignment.CENTER) }
                }
            }
        }
        return out
    }

    private fun effectiveCols(cols: Int, fontB: Boolean) = if (fontB) cols * 4 / 3 else cols

    fun wrap(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val lines = mutableListOf<String>()
        var cur = StringBuilder()
        for (word in text.split(" ")) {
            var w = word
            while (w.length > width) {
                if (cur.isNotEmpty()) { lines += cur.toString().trimEnd(); cur = StringBuilder() }
                lines += w.take(width); w = w.drop(width)
            }
            if (cur.isEmpty()) cur.append(w)
            else if (cur.length + 1 + w.length <= width) cur.append(' ').append(w)
            else { lines += cur.toString(); cur = StringBuilder(w) }
        }
        if (cur.isNotEmpty()) lines += cur.toString()
        return lines.ifEmpty { listOf("") }
    }

    fun twoColumns(left: String, right: String, cols: Int): String {
        val r = right.take(cols)
        val room = (cols - r.length - 1).coerceAtLeast(0)
        return left.take(room).padEnd(cols - r.length) + r
    }

    private fun table(b: TableBlock, data: TemplateData, lookup: (String) -> String?, ctx: RenderContext): List<RBlock> {
        val rows = data.lists[b.source].orEmpty()
        if (b.columns.isEmpty()) return emptyList()
        val gaps = b.columns.size - 1
        val fixed = b.columns.filter { it.width > 0 }.sumOf { it.width }
        val flexCount = b.columns.count { it.width == 0 }.coerceAtLeast(1)
        val flexWidth = ((ctx.cols - fixed - gaps) / flexCount).coerceAtLeast(3)
        val widths = b.columns.mapIndexed { i, c -> if (c.width > 0) c.width else if (i == b.columns.indexOfFirst { it.width == 0 }) ctx.cols - fixed - gaps - flexWidth * (flexCount - 1) else flexWidth }
            .map { it.coerceAtLeast(1) }

        fun line(cells: List<List<String>>): List<String> {
            val height = cells.maxOf { it.size }
            return (0 until height).map { r ->
                b.columns.indices.joinToString(" ") { i ->
                    val cell = cells[i].getOrElse(r) { "" }
                    when (b.columns[i].align) {
                        Alignment.RIGHT -> cell.padStart(widths[i])
                        Alignment.CENTER -> cell.padStart((widths[i] + cell.length) / 2).padEnd(widths[i])
                        else -> cell.padEnd(widths[i])
                    }
                }.take(ctx.cols).trimEnd()
            }
        }

        val out = mutableListOf<RBlock>()
        if (b.showHeader) {
            line(b.columns.mapIndexed { i, c -> wrap(c.title, widths[i]) }).forEach { out += RText(it, bold = true) }
            out += RText("-".repeat(ctx.cols))
        }
        for (row in rows) {
            val rowLookup: (String) -> String? = { k -> row[k] ?: lookup(k) }
            val cells = b.columns.mapIndexed { i, c ->
                val raw = row[c.field] ?: lookup(c.field) ?: ""
                val shown = if (c.field in MONEY_FIELDS) ctx.formatter.number(raw) else raw
                wrap(substitute(shown, rowLookup, ctx), widths[i])
            }
            line(cells).forEach { out += RText(it) }
        }
        return out
    }

    private val MONEY_FIELDS = setOf("price", "total", "amount", "subtotal")

    /** Parses "a,b,c" lines into rows keyed by the table's column fields. */
    fun parseRows(text: String, fields: List<String>): List<Map<String, String>> =
        text.lines().filter { it.isNotBlank() }.map { l ->
            val parts = l.split(",").map { it.trim() }
            fields.mapIndexed { i, f -> f to parts.getOrElse(i) { "" } }.toMap()
        }

    fun formatRows(rows: List<Map<String, String>>, fields: List<String>): String =
        rows.joinToString("\n") { r -> fields.joinToString(",") { r[it].orEmpty() } }

    fun money(v: BigDecimal, f: Formatter) = f.money(v.toPlainString())
}
