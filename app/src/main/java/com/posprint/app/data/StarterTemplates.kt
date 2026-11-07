package com.posprint.app.data

import com.posprint.app.domain.template.Alignment
import com.posprint.app.domain.template.BarcodeBlock
import com.posprint.app.domain.template.BarcodeType
import com.posprint.app.domain.template.ColumnsBlock
import com.posprint.app.domain.template.Column
import com.posprint.app.domain.template.CommandBlock
import com.posprint.app.domain.template.CommandKind
import com.posprint.app.domain.template.DividerBlock
import com.posprint.app.domain.template.FooterBlock
import com.posprint.app.domain.template.HeaderBlock
import com.posprint.app.domain.template.LBarcode
import com.posprint.app.domain.template.LBox
import com.posprint.app.domain.template.LLine
import com.posprint.app.domain.template.LQr
import com.posprint.app.domain.template.LText
import com.posprint.app.domain.template.LabelElement
import com.posprint.app.domain.template.LabelSpec
import com.posprint.app.domain.template.MediaType
import com.posprint.app.domain.template.QrBlock
import com.posprint.app.domain.template.SpacerBlock
import com.posprint.app.domain.template.TableBlock
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateData
import com.posprint.app.domain.template.TemplateKind
import com.posprint.app.domain.template.TextBlock

/** The ten built-in templates preloaded on first launch. */
object StarterTemplates {

    private var n = 0
    private fun id() = "e${++n}"
    private fun text(x: Double, y: Double, w: Double, h: Double, t: String, font: Double = 3.5, bold: Boolean = false, align: Alignment = Alignment.LEFT) =
        LText(id(), x, y, w, h, 0, t, bold, align, font)
    private fun bar(x: Double, y: Double, w: Double, h: Double, d: String, human: Boolean = true, type: BarcodeType = BarcodeType.CODE128) =
        LBarcode(id(), x, y, w, h, 0, d, type, human)
    private fun qr(x: Double, y: Double, s: Double, d: String) = LQr(id(), x, y, s, s, 0, d)
    private fun box(x: Double, y: Double, w: Double, h: Double, t: Double = 0.5) = LBox(id(), x, y, w, h, 0, t)
    private fun line(x: Double, y: Double, w: Double, t: Double = 0.4) = LLine(id(), x, y, w, t, 0, t)

    private fun items(vararg rows: Triple<String, String, String>) =
        rows.map { (name, qty, price) -> mapOf("name" to name, "qty" to qty, "price" to price,
            "total" to (qty.toBigDecimal() * price.toBigDecimal()).toPlainString()) }

    private val itemColumns = listOf(
        Column("Item", "name"),
        Column("Qty", "qty", 4, Alignment.RIGHT),
        Column("Price", "price", 8, Alignment.RIGHT),
        Column("Total", "total", 9, Alignment.RIGHT),
    )

    private fun label(name: String, w: Double, h: Double, els: List<LabelElement>, sample: Map<String, String>, media: MediaType = MediaType.GAP) =
        Template(name = name, kind = TemplateKind.LABEL, builtIn = true, label = LabelSpec(w, h, media, 3.0, 203), elements = els, sample = TemplateData(sample))

    fun all(): List<Template> = listOf(
        Template(
            name = "Sales receipt", kind = TemplateKind.RECEIPT, builtIn = true,
            blocks = listOf(
                HeaderBlock(),
                SpacerBlock(1),
                ColumnsBlock("Receipt #", "{{receipt_no}}"),
                ColumnsBlock("Date", "{{datetime}}"),
                ColumnsBlock("Cashier", "{{cashier}}", showIf = "cashier"),
                DividerBlock(),
                TableBlock("items", listOf(
                    Column("Item", "name"), Column("Qty", "qty", 4, Alignment.RIGHT), Column("Total", "total", 9, Alignment.RIGHT),
                )),
                DividerBlock(),
                ColumnsBlock("Subtotal", "{{subtotal|money}}"),
                ColumnsBlock("Discount", "-{{discount|money}}", showIf = "discount > 0"),
                ColumnsBlock("Tax", "{{tax|money}}", showIf = "tax > 0"),
                TextBlock("TOTAL {{total|money}}", Alignment.RIGHT, bold = true, widthMul = 2, heightMul = 2),
                ColumnsBlock("Paid", "{{paid|money}}", showIf = "paid > 0"),
                ColumnsBlock("Change", "{{change|money}}", showIf = "change > 0"),
                SpacerBlock(1),
                FooterBlock(),
                QrBlock("{{qr}}", 5, showIf = "qr"),
                CommandBlock(CommandKind.DRAWER, showIf = "open_drawer"),
            ),
            sample = TemplateData(
                vars = mapOf("receipt_no" to "1042", "cashier" to "Sam", "subtotal" to "17.50", "discount" to "1.50",
                    "tax" to "1.28", "total" to "17.28", "paid" to "20", "change" to "2.72", "qr" to "https://example.com/r/1042"),
                lists = mapOf("items" to items(Triple("Cappuccino", "2", "3.50"), Triple("Blueberry muffin", "1", "2.75"), Triple("Sparkling water 500ml", "3", "2.58"))),
            ),
        ),
        Template(
            name = "Invoice (A4)", kind = TemplateKind.DOCUMENT, builtIn = true, paper = "A4",
            blocks = listOf(
                HeaderBlock(),
                SpacerBlock(1),
                TextBlock("INVOICE", Alignment.RIGHT, bold = true, widthMul = 2, heightMul = 2),
                ColumnsBlock("Invoice no.", "{{invoice_no}}"),
                ColumnsBlock("Date", "{{date}}"),
                ColumnsBlock("Due date", "{{due_date}}", showIf = "due_date"),
                DividerBlock("="),
                TextBlock("Bill to:", bold = true),
                TextBlock("{{customer}}"),
                TextBlock("{{customer_address}}", showIf = "customer_address"),
                SpacerBlock(1),
                TableBlock("items", itemColumns),
                DividerBlock(),
                ColumnsBlock("Subtotal", "{{subtotal|money}}"),
                ColumnsBlock("Discount", "-{{discount|money}}", showIf = "discount > 0"),
                ColumnsBlock("Tax", "{{tax|money}}", showIf = "tax > 0"),
                ColumnsBlock("TOTAL DUE", "{{total|money}}", bold = true),
                SpacerBlock(1),
                TextBlock("Notes: {{notes}}", showIf = "notes"),
                FooterBlock(),
            ),
            sample = TemplateData(
                vars = mapOf("invoice_no" to "INV-2026-0087", "due_date" to "2026-12-05", "customer" to "Acme Traders Ltd",
                    "customer_address" to "14 Market Road, Springfield", "subtotal" to "1250.00", "discount" to "50", "tax" to "96.00",
                    "total" to "1296.00", "notes" to "Payment by bank transfer within 30 days."),
                lists = mapOf("items" to items(Triple("Thermal paper roll 80mm (box of 50)", "5", "120.00"), Triple("Label roll 50x30mm", "10", "35.00"), Triple("Bluetooth receipt printer", "1", "300.00"))),
            ),
        ),
        Template(
            name = "Kitchen order ticket (KOT)", kind = TemplateKind.RECEIPT, builtIn = true,
            blocks = listOf(
                CommandBlock(CommandKind.BEEP),
                TextBlock("KITCHEN ORDER", Alignment.CENTER, bold = true, widthMul = 2, heightMul = 2),
                TextBlock("Table {{table}}", Alignment.CENTER, bold = true, widthMul = 2, heightMul = 2, showIf = "table"),
                ColumnsBlock("Order #", "{{order_no}}"),
                ColumnsBlock("Time", "{{time}}"),
                ColumnsBlock("Waiter", "{{waiter}}", showIf = "waiter"),
                DividerBlock("="),
                TableBlock("items", listOf(Column("Item", "name"), Column("Qty", "qty", 4, Alignment.RIGHT))),
                DividerBlock("="),
                TextBlock("NOTE: {{notes}}", bold = true, showIf = "notes"),
                SpacerBlock(2),
            ),
            sample = TemplateData(
                vars = mapOf("table" to "12", "order_no" to "A-318", "waiter" to "Priya", "notes" to "No onions on the burger"),
                lists = mapOf("items" to items(Triple("Cheese burger", "2", "0"), Triple("French fries (large)", "1", "0"), Triple("Cold coffee", "2", "0"))),
            ),
        ),
        Template(
            name = "Token / queue number", kind = TemplateKind.RECEIPT, builtIn = true,
            blocks = listOf(
                HeaderBlock(showLogo = false),
                SpacerBlock(1),
                TextBlock("YOUR TOKEN", Alignment.CENTER, bold = true),
                TextBlock("{{token}}", Alignment.CENTER, bold = true, widthMul = 6, heightMul = 6),
                TextBlock("{{service}}", Alignment.CENTER, bold = true, showIf = "service"),
                SpacerBlock(1),
                TextBlock("{{datetime}}", Alignment.CENTER),
                TextBlock("{{waiting}} people ahead of you", Alignment.CENTER, showIf = "waiting > 0"),
                TextBlock("Please wait for your number to be called.", Alignment.CENTER),
                SpacerBlock(2),
            ),
            sample = TemplateData(vars = mapOf("token" to "A042", "service" to "Billing counter", "waiting" to "6")),
        ),
        label("Shipping label (100x150)", 100.0, 150.0, listOf(
            box(2.0, 2.0, 96.0, 146.0, 0.8),
            text(5.0, 4.0, 90.0, 6.0, "FROM: {{from_name}}", 3.0),
            text(5.0, 10.0, 90.0, 6.0, "{{from_address}}", 2.8),
            line(2.0, 18.0, 96.0, 0.8),
            text(5.0, 20.0, 90.0, 6.0, "SHIP TO", 3.0, true),
            text(5.0, 27.0, 90.0, 12.0, "{{to_name}}", 6.5, true),
            text(5.0, 40.0, 90.0, 24.0, "{{to_address}}", 4.5),
            line(2.0, 66.0, 96.0, 0.8),
            text(5.0, 68.0, 45.0, 6.0, "Weight: {{weight}}", 3.2),
            text(50.0, 68.0, 45.0, 6.0, "Order: {{order_no}}", 3.2, align = Alignment.RIGHT),
            bar(8.0, 78.0, 84.0, 28.0, "{{tracking}}"),
            qr(35.0, 110.0, 30.0, "{{tracking}}"),
            text(5.0, 140.0, 90.0, 6.0, "{{date}}", 3.0, align = Alignment.CENTER),
        ), mapOf("from_name" to "POS Print Store", "from_address" to "12 Main St, Springfield", "to_name" to "Jordan Lee",
            "to_address" to "88 Harbour View\nApt 4B\nPortland, OR 97201", "weight" to "1.2 kg", "order_no" to "#10442", "tracking" to "SP1234567890")),
        label("Product price label (50x30)", 50.0, 30.0, listOf(
            text(2.0, 1.5, 46.0, 7.0, "{{product_name}}", 4.0, true),
            text(2.0, 8.5, 46.0, 9.0, "{{price|money}}", 7.0, true, Alignment.CENTER),
            bar(4.0, 18.0, 42.0, 11.0, "{{sku}}"),
        ), mapOf("product_name" to "Organic Green Tea 100g", "price" to "4.99", "sku" to "8901234567890")),
        label("Barcode sticker (40x20)", 40.0, 20.0, listOf(
            bar(3.0, 2.0, 34.0, 12.0, "{{sku}}", human = false),
            text(2.0, 14.5, 36.0, 4.5, "{{sku}}", 3.2, align = Alignment.CENTER),
        ), mapOf("sku" to "SKU-001245")),
        label("Address label (70x40)", 70.0, 40.0, listOf(
            text(3.0, 3.0, 64.0, 7.0, "{{name}}", 5.0, true),
            text(3.0, 11.0, 64.0, 20.0, "{{address}}", 3.6),
            text(3.0, 32.0, 64.0, 6.0, "Tel: {{phone}}", 3.2),
        ), mapOf("name" to "Jordan Lee", "address" to "88 Harbour View, Apt 4B\nPortland, OR 97201", "phone" to "+1 555 0100")),
        label("Shelf label (60x40)", 60.0, 40.0, listOf(
            box(1.0, 1.0, 58.0, 38.0, 0.4),
            text(3.0, 3.0, 54.0, 7.0, "{{product_name}}", 4.2, true),
            text(3.0, 11.0, 30.0, 10.0, "{{price|money}}", 8.0, true),
            text(34.0, 12.0, 24.0, 8.0, "{{unit_price}}", 3.0, align = Alignment.RIGHT),
            bar(5.0, 23.0, 50.0, 13.0, "{{sku}}"),
        ), mapOf("product_name" to "Basmati Rice 5kg", "price" to "12.50", "unit_price" to "2.50 / kg", "sku" to "8909876543210")),
        label("Parcel / return label (100x100)", 100.0, 100.0, listOf(
            box(2.0, 2.0, 96.0, 96.0, 0.8),
            text(5.0, 4.0, 90.0, 14.0, "RETURN", 11.0, true, Alignment.CENTER),
            line(2.0, 20.0, 96.0, 0.8),
            text(5.0, 22.0, 60.0, 7.0, "Order: {{order_no}}", 4.0, true),
            text(5.0, 30.0, 60.0, 16.0, "Reason: {{reason}}", 3.6),
            qr(68.0, 22.0, 28.0, "{{rma}}"),
            bar(8.0, 56.0, 84.0, 24.0, "{{rma}}"),
            text(5.0, 84.0, 90.0, 10.0, "Return to: {{return_address}}", 3.2),
        ), mapOf("order_no" to "#10442", "reason" to "Wrong size", "rma" to "RMA-77310", "return_address" to "POS Print Store, 12 Main St, Springfield")),
    )
}
