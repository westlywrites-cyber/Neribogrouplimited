package com.westly.opay.feature.statement

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Draws the Account Statement PDF (Letter, portrait, always light colours) with Android's PdfDocument. */
class StatementPdfWriter(private val logo: Bitmap?) {

    private class PagePlan(val from: Int, val to: Int, val bottomY: Float, val footer: Boolean, val footerOnly: Boolean)

    private fun paint(size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = face
            textAlign = align
        }

    private val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    private val cell = paint(CELL_SIZE, DARK, regular)
    private val cellBold = paint(CELL_SIZE, DARK, bold)
    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = BORDER
    }
    private val widths: FloatArray = run {
        val total = WEIGHTS.sum()
        FloatArray(WEIGHTS.size) { WEIGHTS[it] / total * (TABLE_R - TABLE_L) }
    }

    fun write(data: StatementData, target: File) {
        val plans = plan(data.rows)
        val document = PdfDocument()
        try {
            plans.forEachIndexed { index, plan ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
                drawPage(page.canvas, data, plan, index == 0)
                document.finishPage(page)
            }
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun plan(rows: List<StatementRow>): List<PagePlan> {
        val pages = ArrayList<PagePlan>()
        var index = 0
        var first = true
        while (true) {
            var y = if (first) FIRST_BODY_TOP else CONT_TOP
            val start = index
            while (index < rows.size) {
                val height = rowHeight(rows[index])
                if (y + height > LIMIT && index > start) break
                y += height
                index++
            }
            if (index >= rows.size) {
                val fits = y + CARD_PAD + GAP + FOOTER_H <= LIMIT
                pages.add(PagePlan(start, index, y, fits, false))
                if (!fits) pages.add(PagePlan(index, index, CONT_TOP, true, true))
                return pages
            }
            pages.add(PagePlan(start, index, y, false, false))
            first = false
        }
    }

    private fun cellTexts(row: StatementRow): List<String> = listOf(
        row.transTime, row.valueDate, row.description, row.debit, row.credit,
        row.balanceAfter, row.channel, row.reference
    )

    private fun textWidth(column: Int) = widths[column] - CELL_L - CELL_R

    private fun cellLines(row: StatementRow): List<List<String>> =
        cellTexts(row).mapIndexed { column, text ->
            if (column in NUMERIC) listOf(text) else wrap(text, cell, textWidth(column))
        }

    private fun rowHeight(row: StatementRow): Float =
        (cellLines(row).maxOf { it.size }) * LINE + 2 * ROW_PAD

    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = ArrayList<String>()
        var current = ""
        for (word in text.split(' ')) {
            var rest = word
            val candidate = if (current.isEmpty()) rest else "$current $rest"
            if (paint.measureText(candidate) <= width) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) {
                lines.add(current)
                current = ""
            }
            while (paint.measureText(rest) > width) {
                val count = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                lines.add(rest.substring(0, count))
                rest = rest.substring(count)
            }
            current = rest
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines.add(current)
        return lines
    }

    private fun drawPage(canvas: Canvas, data: StatementData, plan: PagePlan, first: Boolean) {
        fill.color = PANEL
        canvas.drawRect(PANEL_L, PANEL_T, PANEL_R, PANEL_B, fill)
        if (plan.footerOnly) {
            drawFooter(canvas, CONT_TOP)
            return
        }
        val lastTablePage = plan.to >= data.rows.size
        val cardTop = if (first) CARD2_TOP else PANEL_T
        val cardBottom = if (lastTablePage) plan.bottomY + CARD_PAD else PANEL_B
        if (first) drawHeader(canvas, data)
        drawCard(canvas, CARD_L, cardTop, CARD_R, cardBottom, roundTop = first, roundBottom = lastTablePage)
        val tableTop = if (first) {
            drawWallet(canvas, data)
            drawHeaderBand(canvas)
            BAND_TOP
        } else {
            CONT_TOP
        }
        var y = if (first) FIRST_BODY_TOP else CONT_TOP
        for (index in plan.from until plan.to) {
            val row = data.rows[index]
            val lines = cellLines(row)
            val height = lines.maxOf { it.size } * LINE + 2 * ROW_PAD
            fill.color = if (index % 2 == 0) Color.WHITE else ROW_ALT
            canvas.drawRect(TABLE_L, y, TABLE_R, y + height, fill)
            drawRow(canvas, row, lines, y, height)
            y += height
        }
        canvas.drawRoundRect(RectF(TABLE_L, tableTop, TABLE_R, plan.bottomY), 6f, 6f, line)
        if (plan.footer) drawFooter(canvas, cardBottom + GAP)
    }

    private fun drawCard(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, roundTop: Boolean, roundBottom: Boolean) {
        fill.color = Color.WHITE
        canvas.drawRoundRect(RectF(l, t, r, b), RADIUS, RADIUS, fill)
        if (!roundTop) canvas.drawRect(l, t, r, t + RADIUS, fill)
        if (!roundBottom) canvas.drawRect(l, b - RADIUS, r, b, fill)
    }

    private fun drawHeader(canvas: Canvas, data: StatementData) {
        val image = logo
        if (image != null) {
            canvas.drawBitmap(image, null, RectF(LOGO_R - 66f, 55f, LOGO_R, 77f), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            drawRing(canvas, LOGO_R - 56f, 66f, 10f)
            canvas.drawText("Pay", LOGO_R - 40f, 73f, paint(17f, INDIGO, bold))
        }
        canvas.drawText("...Beyond Banking", LOGO_R, 86f, paint(5f, GREY, regular, Paint.Align.RIGHT))
        canvas.drawText("Account Statement", 306f, 108f, paint(18f, DARK, bold, Paint.Align.CENTER))
        canvas.drawText(
            "Generated on " + StatementFormat.tableTime(data.generatedAtMillis),
            306f, 120f, paint(7f, GREY, medium, Paint.Align.CENTER)
        )
        drawCard(canvas, CARD_L, 123f, CARD_R, 177f, roundTop = true, roundBottom = true)
        val label = paint(7f, GREY, medium)
        val value = paint(8f, DARK, bold)
        canvas.drawText("Account Name", 66f, 147f, label)
        canvas.drawText(data.accountName, 66f, 158f, value)
        canvas.drawText("Account Number", 224.5f, 147f, label)
        canvas.drawText(data.accountNumber, 224.5f, 158f, value)
    }

    private fun drawWallet(canvas: Canvas, data: StatementData) {
        val label = paint(7f, GREY, medium)
        val value = paint(9f, DARK, bold)
        canvas.drawText("Wallet Account", 66f, 213f, paint(11f, DARK, medium))
        canvas.drawText("Period: " + StatementFormat.periodText(data.period), 224.5f, 212f, paint(8f, GREY, medium))
        val xs = floatArrayOf(66f, 224.5f, 383f)
        val labels = listOf(
            listOf("Opening Balance", "Total Debit", "Debit Count"),
            listOf("Closing Balance", "Total Credit", "Credit Count")
        )
        val values = listOf(
            listOf(money(data.openingKobo), money(data.totalDebitKobo), data.debitCount.toString()),
            listOf(money(data.closingKobo), money(data.totalCreditKobo), data.creditCount.toString())
        )
        for (r in 0..1) {
            val base = if (r == 0) 241f else 278f
            for (c in 0..2) {
                canvas.drawText(labels[r][c], xs[c], base, label)
                val p = if (r == 1 && c == 1) paint(9f, CREDIT, bold) else value
                canvas.drawText(values[r][c], xs[c], base + 11f, p)
            }
        }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
            color = Color.parseColor("#D4D4DA")
            pathEffect = DashPathEffect(floatArrayOf(2f, 2f), 0f)
        }
        canvas.drawLine(66f, 302f, 546f, 302f, dash)
    }

    private fun money(kobo: Long) = com.westly.opay.core.util.formatNaira(kobo)

    private fun drawHeaderBand(canvas: Canvas) {
        fill.color = BAND
        val band = RectF(TABLE_L, BAND_TOP, TABLE_R, FIRST_BODY_TOP)
        canvas.drawRoundRect(band, 6f, 6f, fill)
        canvas.drawRect(TABLE_L, BAND_TOP + 8f, TABLE_R, FIRST_BODY_TOP, fill)
        var x = TABLE_L
        HEADERS.forEachIndexed { column, text ->
            val lines = wrap(text, cellBold, textWidth(column))
            val top = BAND_TOP + (FIRST_BODY_TOP - BAND_TOP - lines.size * HEADER_LINE) / 2f
            lines.forEachIndexed { i, s ->
                canvas.drawText(s, x + CELL_L, top + (i + 1) * HEADER_LINE - 2.2f, cellBold)
            }
            x += widths[column]
        }
    }

    private fun drawRow(canvas: Canvas, row: StatementRow, lines: List<List<String>>, top: Float, height: Float) {
        var x = TABLE_L
        for (column in lines.indices) {
            val parts = lines[column]
            val block = parts.size * LINE
            val start = top + (height - block) / 2f
            val p = paintFor(column, row)
            if (column in NUMERIC) {
                val saved = p.textSize
                while (p.measureText(parts[0]) > textWidth(column) && p.textSize > 3.6f) p.textSize = p.textSize * 0.94f
                canvas.drawText(parts[0], x + CELL_L, start + 8.2f, p)
                p.textSize = saved
            } else {
                parts.forEachIndexed { i, s -> canvas.drawText(s, x + CELL_L, start + i * LINE + 8.2f, p) }
            }
            x += widths[column]
        }
    }

    private fun paintFor(column: Int, row: StatementRow): Paint {
        val color = when (column) {
            3 -> if (row.debit == StatementBuilder.EMPTY) FAINT else DARK
            4 -> if (row.credit == StatementBuilder.EMPTY) FAINT else CREDIT
            5 -> GREY_TEXT
            else -> DARK
        }
        cell.color = color
        return cell
    }

    private fun drawFooter(canvas: Canvas, top: Float) {
        drawCard(canvas, CARD_L, top, CARD_R, top + FOOTER_H, roundTop = true, roundBottom = true)
        val cy = top + FOOTER_H / 2f
        val image = logo
        if (image != null) {
            val srcWidth = (image.width * 0.29f).toInt().coerceAtLeast(1)
            val dstWidth = 26f * srcWidth / image.height
            canvas.drawBitmap(
                image, Rect(0, 0, srcWidth, image.height),
                RectF(66f, cy - 13f, 66f + dstWidth, cy + 13f), Paint(Paint.FILTER_BITMAP_FLAG)
            )
        } else {
            drawRing(canvas, 78f, cy, 11f)
        }
        canvas.drawText(
            "This statement was generated by the OPay demo app. It is not an official bank document.",
            102f, cy + 2.5f, paint(7f, DARK, regular)
        )
    }

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.38f
            color = RING
        }
        canvas.drawCircle(cx, cy, radius, ring)
        fill.color = INDIGO
        canvas.drawRect(cx - radius - 2f, cy - 2f, cx - radius * 0.35f, cy + 2f, fill)
    }

    private companion object {
        const val PAGE_W = 612
        const val PAGE_H = 792
        const val PANEL_L = 36f
        const val PANEL_T = 36f
        const val PANEL_R = 576f
        const val PANEL_B = 756f
        const val CARD_L = 54f
        const val CARD_R = 558f
        const val TABLE_L = 66f
        const val TABLE_R = 546f
        const val LOGO_R = 558f
        const val CARD2_TOP = 187f
        const val BAND_TOP = 315f
        const val FIRST_BODY_TOP = 347f
        const val CONT_TOP = 48f
        const val LIMIT = 744f
        const val CARD_PAD = 12f
        const val GAP = 12f
        const val FOOTER_H = 54f
        const val RADIUS = 8f
        const val CELL_SIZE = 5.4f
        const val CELL_L = 6f
        const val CELL_R = 3f
        const val LINE = 11.5f
        const val HEADER_LINE = 8.5f
        const val ROW_PAD = 5f
        val NUMERIC = setOf(3, 4, 5)
        val WEIGHTS = floatArrayOf(13f, 10f, 26.5f, 8f, 8f, 9f, 11.5f, 14.5f)
        val HEADERS = listOf(
            "Trans. Time", "Value Date", "Description", "Debit(\u20A6)", "Credit(\u20A6)",
            "Balance After(\u20A6)", "Channel", "Transaction Reference"
        )
        val PANEL = Color.parseColor("#F8F8FA")
        val ROW_ALT = Color.parseColor("#F8F8FA")
        val BAND = Color.parseColor("#EFEFF4")
        val BORDER = Color.parseColor("#E6E6EA")
        val DARK = Color.parseColor("#1B1B1F")
        val GREY = Color.parseColor("#8A8A90")
        val GREY_TEXT = Color.parseColor("#6F6F75")
        val FAINT = Color.parseColor("#B5B5BA")
        val CREDIT = Color.parseColor("#00B27A")
        val INDIGO = Color.parseColor("#22145F")
        val RING = Color.parseColor("#1DCF9F")
    }
}
