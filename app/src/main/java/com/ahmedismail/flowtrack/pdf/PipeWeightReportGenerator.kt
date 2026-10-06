package com.ahmedismail.flowtrack.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.PipeWeightResult
import com.ahmedismail.flowtrack.util.PipeWeightInput
import com.ahmedismail.flowtrack.util.PipeWeightCalculator
import com.ahmedismail.flowtrack.util.EngineeringEquations
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A4 PDF for a standalone pipe weight calculation. Same independent,
 * project-free design as PipeThicknessReportGenerator — Project/Engineer
 * name are plain optional strings, not pulled from the app's Project
 * entity. Clearly separates INPUTS and OUTPUTS.
 */
object PipeWeightReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val FOOTER_RESERVE = 30f

    fun generate(
        projectName: String?,
        engineerName: String?,
        materialName: String,
        nps: String?,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
        densityKgM3: Double,
        lengthM: Double,
        result: PipeWeightResult,
        outputDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    ): File {
        val recalculated = PipeWeightCalculator.calculate(PipeWeightInput(outsideDiameterMm, wallThicknessMm,
            densityKgM3, lengthM, result.waterFillWeightKg != null))
        val document = PdfDocument()
        val cursor = Cursor(document)

        cursor.y = drawHeader(cursor.canvas, projectName, engineerName, cursor.y)
        cursor.y = drawInputs(cursor, materialName, nps, outsideDiameterMm, wallThicknessMm, densityKgM3, lengthM)
        cursor.y = drawOutputs(cursor, recalculated)
        cursor.ensureSpace(42f)
        val note = Paint().apply { color = Color.parseColor("#526575"); textSize = 9f }
        listOf(
            "Nominal bare-pipe mass = pi * t * (D - t) * density / 1,000,000 (kg/m).",
            "For carbon steel at 7850 kg/m3, this agrees with the B36.10 mass coefficient.",
            "Water density, when selected: 1000 kg/m3. Excludes coatings, insulation and fittings."
        ).forEach { cursor.canvas.drawText(it, MARGIN, cursor.y + 12f, note); cursor.y += 12f }
        cursor.finish()

        if (!outputDir.exists()) outputDir.mkdirs()
        val fileName = "PiCal_PipeWeight_${System.currentTimeMillis()}.pdf"
        val file = File(outputDir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    private class Cursor(private val document: PdfDocument) {
        var pageNumber = 1
        private var page: PdfDocument.Page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas: Canvas = page.canvas
        var y: Float = MARGIN

        fun ensureSpace(needed: Float) {
            if (y + needed > PAGE_HEIGHT - MARGIN - FOOTER_RESERVE) {
                drawFooter(canvas, pageNumber)
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        fun finish() {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
        }
    }

    private fun drawHeader(canvas: Canvas, projectName: String?, engineerName: String?, startY: Float): Float {
        var y = startY
        val title = Paint().apply { color = Color.parseColor("#14263D"); textSize = 20f; isFakeBoldText = true }
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val value = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 12f }

        canvas.drawText("PiCal — Pipe Weight Report", MARGIN, y, title)
        y += 26f
        val formulaPaint = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 9.5f }
        EngineeringEquations.WEIGHT.lines().forEach { line -> canvas.drawText(line, MARGIN, y, formulaPaint); y += 13f }
        y += 8f

        val fields = listOfNotNull(
            projectName?.let { "Project" to it },
            engineerName?.let { "Engineer" to it },
            "Date" to SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
        )
        for ((k, v) in fields) {
            canvas.drawText(k.uppercase(), MARGIN, y, label)
            canvas.drawText(fitText(v, value, PAGE_WIDTH - MARGIN - (MARGIN + 150f)), MARGIN + 150f, y, value)
            y += 16f
        }
        y += 10f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, Paint().apply { color = Color.parseColor("#E1E5E9") })
        return y + 20f
    }

    private fun drawInputs(
        cursor: Cursor, materialName: String, nps: String?, odMm: Double, wallMm: Double, densityKgM3: Double, lengthM: Double
    ): Float {
        cursor.ensureSpace(20f)
        val sectionLabel = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val rowLabel = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 10f }
        val rowValue = Paint().apply { color = Color.parseColor("#2D6E9E"); textSize = 10f; isFakeBoldText = true }

        cursor.canvas.drawText("INPUTS", MARGIN, cursor.y, sectionLabel)
        cursor.y += 18f

        val rows = listOfNotNull(
            "Material" to materialName,
            nps?.let { "Nominal Pipe Size (NPS)" to it },
            "Outside Diameter (OD)" to "${fmt(odMm)} mm",
            "Wall Thickness (t)" to "${fmt(wallMm)} mm",
            "Density" to "${fmt(densityKgM3)} kg/m\u00B3",
            "Pipe Length" to "${fmt(lengthM)} m"
        )
        for ((k, v) in rows) {
            cursor.ensureSpace(14f)
            cursor.canvas.drawText(k, MARGIN + 6f, cursor.y, rowLabel)
            cursor.canvas.drawText(v, MARGIN + 280f, cursor.y, rowValue)
            cursor.y += 14f
        }
        return cursor.y + 12f
    }

    private fun drawOutputs(cursor: Cursor, result: PipeWeightResult): Float {
        cursor.ensureSpace(90f)
        val sectionLabel = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val bigLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9.5f }
        val bigValue = Paint().apply { color = Color.parseColor("#14263D"); textSize = 16f; isFakeBoldText = true }

        cursor.canvas.drawText("OUTPUTS", MARGIN, cursor.y, sectionLabel)
        cursor.y += 18f

        cursor.canvas.drawText("UNIT WEIGHT", MARGIN, cursor.y, bigLabel)
        cursor.canvas.drawText("${fmt(result.unitWeightKgM)} kg/m", MARGIN, cursor.y + 20f, bigValue)
        cursor.canvas.drawText("TOTAL PIPE WEIGHT", MARGIN + 260f, cursor.y, bigLabel)
        cursor.canvas.drawText("${fmt(result.totalPipeWeightKg)} kg", MARGIN + 260f, cursor.y + 20f, bigValue)
        cursor.y += 40f

        if (result.waterFillWeightKg != null) {
            cursor.ensureSpace(45f)
            cursor.canvas.drawText("WATER FILL WEIGHT", MARGIN, cursor.y, bigLabel)
            cursor.canvas.drawText("${fmt(result.waterFillWeightKg)} kg", MARGIN, cursor.y + 20f, bigValue)
            cursor.canvas.drawText("TOTAL (INCL. WATER)", MARGIN + 260f, cursor.y, bigLabel)
            cursor.canvas.drawText("${fmt(result.totalWithWaterKg)} kg", MARGIN + 260f, cursor.y + 20f, bigValue)
            cursor.y += 40f
        }
        return cursor.y
    }

    private fun fmt(value: Double): String = DisplayFormat.number(value, 2)

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footer = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9f; textAlign = Paint.Align.CENTER }
        canvas.drawText("By. Ahmed Ismail", PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footer)
        val pageLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 8.5f; textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, pageLabel)
    }
}
