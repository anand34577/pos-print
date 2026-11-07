package com.posprint.app.domain.render

import com.posprint.app.domain.template.TemplateData
import java.math.BigDecimal

/**
 * Fills in the numbers people should never have to work out by hand: each item's line total (qty × price),
 * and — when a template uses them — subtotal, total (subtotal − discount + tax) and change (paid − total).
 */
object ReceiptMath {
    /** Variables this calculator owns when a template uses them; the UI shows them as "calculated". */
    val CALCULATED = setOf("subtotal", "total", "change")

    private fun num(s: String?) = s?.trim()?.replace(',', '.')?.toBigDecimalOrNull()
    private fun BigDecimal.plain() = stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /** Returns [data] with calculated values filled in, plus the names that were calculated. */
    fun autofill(data: TemplateData, used: Collection<String>): Pair<TemplateData, Set<String>> {
        val lists = data.lists.mapValues { (_, rows) ->
            rows.map { r ->
                val q = num(r["qty"]); val p = num(r["price"])
                if (q != null && p != null) r + ("total" to (q * p).plain()) else r
            }
        }
        val rows = lists["items"] ?: lists.values.firstOrNull().orEmpty()
        if (rows.isEmpty()) return data.copy(lists = lists) to emptySet()

        val vars = data.vars.toMutableMap()
        val done = mutableSetOf<String>()
        val subtotal = rows.fold(BigDecimal.ZERO) { a, r -> a + (num(r["total"]) ?: BigDecimal.ZERO) }
        if ("subtotal" in used) { vars["subtotal"] = subtotal.plain(); done += "subtotal" }
        val total = subtotal - (num(vars["discount"]) ?: BigDecimal.ZERO) + (num(vars["tax"]) ?: BigDecimal.ZERO)
        if ("total" in used) { vars["total"] = total.plain(); done += "total" }
        if ("change" in used) {
            val paid = num(vars["paid"])
            vars["change"] = if (paid != null && paid > total) (paid - total).plain() else "0"
            done += "change"
        }
        return TemplateData(vars, lists) to done
    }
}
