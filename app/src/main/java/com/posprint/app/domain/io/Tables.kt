package com.posprint.app.domain.io

import java.io.InputStream
import java.util.zip.ZipInputStream

/** A parsed spreadsheet: first row is the header. */
data class Table(val headers: List<String>, val rows: List<List<String>>) {
    fun records(): List<Map<String, String>> =
        rows.map { r -> headers.mapIndexed { i, h -> h to r.getOrElse(i) { "" } }.toMap() }
}

object TableParser {

    /** RFC 4180 CSV with automatic comma / semicolon / tab detection and quoted fields. */
    fun csv(textIn: String): Table {
        val text = textIn.removePrefix(0xFEFF.toChar().toString()) // strip a UTF-8 byte-order mark
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delim = listOf(',', ';', '\t').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { firstLine.contains(it) } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        fun endCell() { row += cell.toString(); cell.clear() }
        fun endRow() { endCell(); if (row.any { it.isNotBlank() }) rows += row; row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == delim -> endCell()
                !quoted && c == '\r' -> Unit
                !quoted && c == '\n' -> endRow()
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
        return toTable(rows)
    }

    /** Minimal .xlsx reader: first worksheet, shared and inline strings, numbers as written. */
    fun xlsx(input: InputStream): Table {
        var shared = emptyList<String>()
        val sheets = sortedMapOf<Int, String>()
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { e ->
                val n = SHEET.matchEntire(e.name)?.groupValues?.get(1)?.toIntOrNull()
                when {
                    e.name == "xl/sharedStrings.xml" -> shared = parseShared(zip.readBytes().toString(Charsets.UTF_8))
                    n != null -> sheets[n] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        // Zip entry order is arbitrary; the first worksheet is the lowest-numbered one.
        val xml = sheets.values.firstOrNull() ?: return Table(emptyList(), emptyList())
        val rows = mutableListOf<List<String>>()
        Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { rm ->
            val cells = sortedMapOf<Int, String>()
            Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(rm.groupValues[1]).forEach { cm ->
                val attrs = cm.groupValues[1]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: return@forEach
                val type = Regex("t=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                val body = cm.groupValues[2]
                val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)
                val value = when (type) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(body).joinToString("") { unescape(it.groupValues[1]) }
                    else -> unescape(v.orEmpty()).let { n -> if (n.endsWith(".0")) n.dropLast(2) else n }
                }
                cells[colIndex(ref)] = value
            }
            if (cells.isNotEmpty()) rows += (0..cells.lastKey()).map { cells[it].orEmpty() }
        }
        return toTable(rows.filter { r -> r.any { it.isNotBlank() } })
    }

    private val SHEET = Regex("xl/worksheets/sheet(\\d+)\\.xml")

    private fun parseShared(xml: String): List<String> =
        Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si ->
            Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.groupValues[1]).joinToString("") { unescape(it.groupValues[1]) }
        }.toList()

    private fun colIndex(letters: String): Int = letters.fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

    private fun unescape(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun toTable(rows: List<List<String>>): Table {
        if (rows.isEmpty()) return Table(emptyList(), emptyList())
        val headers = rows.first().map { it.trim() }
        return Table(headers, rows.drop(1))
    }
}
