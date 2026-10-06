package com.ahmedismail.flowtrack.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Minimal A4 renderer for the calculator reports (pipe bend, pressure vessel):
 * same navy banner, margins, row rules and page footer as the other FlowTrack
 * reports, without the table and photo machinery those need.
 *
 * All numbers and dates are formatted in the Latin/English convention on
 * purpose: the reports are engineering documents and read the same in either
 * app language.
 */
internal class CalculatorReport(private val title: String, private val subtitle: String, private val equation: String? = null) {
    companion object {
        const val PASS = 0xFF25734C.toInt()
        const val FAIL = 0xFFB84326.toInt()
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 40f
        private const val BOTTOM = 785f
        private const val VALUE_X = MARGIN + 185f
        private const val LABEL_WIDTH = 175f
        private val NAVY = Color.rgb(21, 43, 60)
        private val TEAL = Color.rgb(0, 105, 110)
        private val MUTED = Color.rgb(83, 103, 116)
        private val INK = Color.rgb(31, 42, 55)
        private val PALE = Color.rgb(240, 245, 247)
        private val LINE = Color.rgb(225, 229, 233)
    }

    private val document = PdfDocument()
    private var pageNumber = 1
    private var page: PdfDocument.Page =
        document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
    private val canvas: Canvas get() = page.canvas
    private var y = 68f

    init { banner() }

    private fun paint(size: Float, color: Int = INK, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun banner() {
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 40f, paint(1f, NAVY))
        canvas.drawText("PiCal", MARGIN, 26f, paint(12f, Color.WHITE, true))
        val right = paint(9f, Color.WHITE).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("ENGINEERING DESIGN CHECK", PAGE_WIDTH - MARGIN, 26f, right)
    }

    private fun footer() {
        canvas.drawLine(MARGIN, 802f, PAGE_WIDTH - MARGIN, 802f, paint(1f, Color.LTGRAY))
        canvas.drawText("By. Ahmed Ismail | User-supplied design data", MARGIN, 819f, paint(8f, MUTED))
        val pagePaint = paint(9f, MUTED).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, 819f, pagePaint)
    }

    private fun newPage() {
        footer()
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        y = 62f
        banner()
    }

    private fun space(height: Float) {
        if (y + height > BOTTOM) newPage()
    }

    fun heading(projectName: String?, engineerName: String?, reference: String?) {
        paragraph(title, 20f, NAVY, true)
        paragraph(subtitle, 10.5f, TEAL)
        equation?.let {
            paragraph("DESIGN EQUATION", 10f, TEAL, true)
            // Explicit line breaks in shared formula text must be preserved.
            it.lines().forEach { line -> paragraph(line, 9f, afterGap = 1f) }
            paragraph("Pressure equations: P and S in MPa; dimensions in mm; input bar x 0.1 = MPa.", 8.5f)
        }
        y += 6f
        if (!projectName.isNullOrBlank()) row("Project", projectName)
        if (!engineerName.isNullOrBlank()) row("Prepared by", engineerName)
        if (!reference.isNullOrBlank()) row("Design reference", reference)
        row("Report date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        y += 4f
    }

    fun section(title: String, minimumContentHeight: Float = 22f) {
        space(42f + minimumContentHeight)
        y += 4f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f, paint(1f, PALE))
        canvas.drawText(title, MARGIN + 9f, y + 16f, paint(10f, TEAL, true))
        // Rows draw text on their baseline: leave space below the 24pt band.
        y += 38f
    }

    fun row(label: String, value: String) {
        space(18f)
        val labelPaint = paint(9.5f, MUTED)
        val valuePaint = paint(10f, INK, true)
        canvas.drawText(fitText(label, labelPaint, LABEL_WIDTH), MARGIN, y, labelPaint)
        canvas.drawText(
            fitText(value, valuePaint, PAGE_WIDTH - MARGIN - VALUE_X),
            VALUE_X, y, valuePaint
        )
        y += 11f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint(1f, LINE))
        y += 4f
    }

    fun paragraph(text: String, size: Float = 9f, color: Int = MUTED, bold: Boolean = false, afterGap: Float = 5f) {
        val textPaint = paint(size, color, bold)
        val maxWidth = PAGE_WIDTH - 2 * MARGIN
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (textPaint.measureText(candidate) <= maxWidth) {
                current.clear()
                current.append(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current.clear()
                current.append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        val blockHeight = lines.size * (size + 3f) + afterGap
        if (blockHeight < BOTTOM - 62f) space(blockHeight)
        for (line in lines) {
            space(size + 5f)
            canvas.drawText(line, MARGIN, y, textPaint)
            y += size + 3f
        }
        y += afterGap
    }

    fun write(outputDir: File, prefix: String): File {
        footer()
        document.finishPage(page)
        if (!outputDir.exists()) outputDir.mkdirs()
        val file = File(outputDir, "${fileNameSafe(prefix)}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }
}
