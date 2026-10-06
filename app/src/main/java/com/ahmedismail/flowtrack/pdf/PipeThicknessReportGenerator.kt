package com.ahmedismail.flowtrack.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.PipeThicknessCalculator
import com.ahmedismail.flowtrack.util.PipeThicknessInput
import com.ahmedismail.flowtrack.util.PipeThicknessResult
import com.ahmedismail.flowtrack.util.PipingCode
import com.ahmedismail.flowtrack.util.EngineeringEquations
import com.ahmedismail.flowtrack.util.B311PipeThicknessCalculator
import com.ahmedismail.flowtrack.util.B311PipeInput
import com.ahmedismail.flowtrack.data.reference.B311MaterialGroup
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Headline: user-entered finished-pipe measurement, never a nominal-wall estimate. */
object PipeThicknessReportGenerator {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val LEFT = 40f
    private const val BOTTOM = 785f
    private val navy = Color.rgb(21, 43, 60)
    private val teal = Color.rgb(0, 105, 110)
    private val muted = Color.rgb(83, 103, 116)
    private val pale = Color.rgb(240, 245, 247)

    fun generate(
        projectName: String?, engineerName: String?, materialName: String, nps: String,
        outsideDiameterMm: Double, designPressureBar: Double, designTemperatureC: Double,
        allowableStressMPa: Double, qualityFactorE: Double,
        weldFactorW: Double, yCoefficient: Double, corrosionAllowanceMm: Double,
        erosionAllowanceMm: Double, mechanicalAllowanceMm: Double, designReference: String,
        millTolerancePercent: Double?, measuredMinimumWallMm: Double?, measurementReference: String,
        selectedSchedule: String?, scheduleWallThicknessMm: Double?,
        pipingCode: PipingCode = PipingCode.B31_3_PROCESS,
        b311MaterialGroup: B311MaterialGroup = B311MaterialGroup.FERRITIC,
        b311ManualY: Boolean = false,
        outputDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    ): File {
        require(designTemperatureC.isFinite() && designTemperatureC > -273.15)
        // Recalculate from report inputs; never export a stale UI result.
        val rawInput = PipeThicknessInput(designPressureBar,
            outsideDiameterMm, allowableStressMPa, qualityFactorE, weldFactorW, yCoefficient,
            corrosionAllowanceMm, erosionAllowanceMm, mechanicalAllowanceMm)
        require(listOf(corrosionAllowanceMm, erosionAllowanceMm, mechanicalAllowanceMm).all { it.isFinite() && it >= 0.0 })
        val b311 = if (pipingCode == PipingCode.B31_1_POWER) B311PipeThicknessCalculator.calculate(
            B311PipeInput(designPressureBar, outsideDiameterMm, allowableStressMPa, qualityFactorE, weldFactorW,
                corrosionAllowanceMm + erosionAllowanceMm + mechanicalAllowanceMm, b311MaterialGroup,
                designTemperatureC, if (b311ManualY) yCoefficient else null)
        ) else null
        val result = b311?.let { PipeThicknessResult(it.pressureThicknessMm, it.minimumRequiredThicknessMm,
            it.pressureThicknessMm + corrosionAllowanceMm + erosionAllowanceMm)
        } ?: PipeThicknessCalculator.calculate(rawInput)
        val codeReference = if (pipingCode == PipingCode.B31_1_POWER) "ASME B31.1-2022" else "ASME B31.3-2018"
        val actualY = b311?.yCoefficient ?: yCoefficient
        val measured = measuredMinimumWallMm?.let { PipeThicknessCalculator.checkFinishedWall(result, outsideDiameterMm, it) }
        if (scheduleWallThicknessMm != null) require(scheduleWallThicknessMm.isFinite() && scheduleWallThicknessMm > 0 && 2 * scheduleWallThicknessMm < outsideDiameterMm)
        val nominal = if (millTolerancePercent != null && scheduleWallThicknessMm != null)
            PipeThicknessCalculator.checkNominalWall(result, outsideDiameterMm, scheduleWallThicknessMm, millTolerancePercent) else null
        val requiredNominal = millTolerancePercent?.let { PipeThicknessCalculator.requiredNominalThicknessMm(result, it) }
        val document = PdfDocument()
        var activePage: ReportPage? = null
        try {
            val page = ReportPage(document).also { activePage = it }
            page.begin()
            page.heading("FINISHED PIPE WALL", "Measurement and internal-pressure thickness review")
            page.paragraph("$codeReference | Straight pipe | Internal pressure", 10f, teal)
            page.paragraph("DESIGN EQUATION", 10f, teal, true)
            page.paragraph(EngineeringEquations.pipe(pipingCode), 9f)
            page.paragraph("P and S in MPa; dimensions in mm. Input pressure in bar x 0.1 = MPa.", 9f)
            page.y += 10f
            page.hero(measured?.measuredMinimumMm, measured?.passes)
            page.row("Required remaining wall", "${fmt(result.minimumFinishedWallMm)} mm")
            page.row("Measured minus required", measured?.let { "${fmt(it.marginMm)} mm" } ?: "Not available")
            page.paragraph("Required remaining wall = pressure thickness + corrosion + erosion. The measured value is the minimum remaining metal after ALL fabrication and machining, including groove/thread roots. It is supplied by the user; the app does not measure the pipe.")
            page.paragraph("No mill under-tolerance or already removed metal is deducted from this measurement again.", 10f, teal)
            page.section("01  PIPE AND INSPECTION")
            page.row("Material / grade", materialName)
            page.row("Pipe size / outside diameter", "$nps / ${fmt(outsideDiameterMm)} mm")
            page.row("Inspection / pipe reference", measurementReference.ifBlank { "Not provided" })
            page.row("Project", projectName?.takeIf { it.isNotBlank() } ?: "Not provided")
            page.row("Prepared by", engineerName?.takeIf { it.isNotBlank() } ?: "Not provided")
            page.row("Report date", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            page.section("02  MEASUREMENT BASIS")
            page.paragraph("Use an inspection minimum that accounts for measurement uncertainty. Exclude coatings, lining and weld reinforcement. This comparison applies to a new straight pipe before service. An average reading or nominal schedule wall is not a minimum finished-wall measurement.")
            page.paragraph("MEETS / BELOW refers only to the internal-pressure wall requirement for these inputs. It is not an inspection certificate or approval of the complete piping design.")
            page.newPage()
            page.heading("DESIGN BASIS", "Calculation trail and nominal pipe selection")
            page.section("03  PRESSURE DESIGN INPUTS")
            page.row("Internal design gauge pressure P", "${fmt(designPressureBar)} bar(g) = ${fmt(designPressureBar * 0.1)} MPa")
            page.row("Design metal temperature", "${fmt(designTemperatureC)} °C")
            page.row("Allowable stress S", "${fmt(allowableStressMPa)} MPa")
            page.row("Quality factor E / weld factor W", "${fmt(qualityFactorE)} / ${fmt(weldFactorW)}")
            page.row("Coefficient Y / y", fmt(actualY))
            if (b311 != null) {
                page.row("Material group for y", b311MaterialGroup.displayName)
                page.row("Table 104.1.2-1 note (b)", if (b311.thinWallRuleApplied) "Applied: y = d/(d + Do)" else "Not applied")
            }
            page.row("Corrosion / erosion allowance", "${fmt(corrosionAllowanceMm)} / ${fmt(erosionAllowanceMm)} mm")
            page.row("Mechanical removal allowance", "${fmt(mechanicalAllowanceMm)} mm (before machining)")
            page.row("Code / material input reference", designReference.ifBlank { "Not provided" })
            page.section("04  CALCULATION TRACE")
            page.paragraph("P is converted from bar to MPa; D and thicknesses are in mm. S is in MPa.", 9.5f)
            page.row("Pressure thickness t", "P × D / [2 × (S × E × W + P × Y)] = ${fmt(result.calculatedThicknessMm)} mm")
            page.row("Required remaining finished wall", "t + corrosion + erosion = ${fmt(result.minimumFinishedWallMm)} mm")
            page.row("Required wall before machining tm", "t + corrosion + erosion + mechanical = ${fmt(result.minimumRequiredThicknessMm)} mm")
            if (b311 == null) page.row("Equation scope checks", "t < D/6; P/(S × E) = ${fmt(designPressureBar * 0.1 / (allowableStressMPa * qualityFactorE))} ≤ 0.385")
            else page.paragraph("B31.1-2022 Eq. (7): tm = P Do / [2(SEW + Py)] + A. S is entered before the quality factor; do not apply E twice to a combined SE/SF table stress. The required wall leaves a positive bore.")
            page.section("05  NOMINAL PIPE ORDERING CHECK (SEPARATE)")
            page.row("Specified minus tolerance", millTolerancePercent?.let { "${fmt(it)} %" } ?: "Not provided; ordering check not evaluated")
            page.row("Minimum nominal pipe wall to order", requiredNominal?.let { "tm / (1 - tolerance/100) = ${fmt(it)} mm" } ?: "Not evaluated")
            page.row("Selected nominal pipe", if (scheduleWallThicknessMm != null) "${selectedSchedule ?: "Manual"} / ${fmt(scheduleWallThicknessMm)} mm" else "Not selected")
            page.row("Lower wall bound from tolerance", nominal?.let { "${fmt(it.effectiveWallMm)} mm - calculated bound, NOT measured" } ?: "Not evaluated")
            page.row("Nominal selection result", nominal?.let { if (it.passes) "Meets the ordering thickness check" else "BELOW the ordering thickness requirement" } ?: "Not evaluated")
            page.paragraph("These are pipe wall dimensions. No flat-sheet thickness or forming-process calculation is performed. A nominal-wall check cannot replace the measured finished-wall result at the start of this report.", 9.5f, teal)
            page.section("06  LIMITS AND REFERENCES")
            val basis = if (b311 == null) "B31.3-2018, 304.1.1 and 304.1.2, equations (2) and (3a); Y: Table 304.1.1"
                else "B31.1-2022, 104.1.2(a), equation (7); y: Table 104.1.2-1; source: STRAIGHT PIPE.pdf p.27 and ASME B31.1, 104.pdf p.28"
            page.paragraph("Basis: $basis. Nominal dimensions: B36.10-2022 Table 2-1. S, E, W, allowances, tolerance and inspection values require verification against project specifications. This report does not assess external pressure, bends, branches, other loads, fatigue, remaining life or high-pressure special services. Comparisons use unrounded numbers; displayed numbers are formatted for readability.", 9f)
            page.finish()
            check(outputDir.isDirectory || outputDir.mkdirs()) { "Cannot create report directory" }
            val file = File(outputDir, "PiCal_FinishedPipeWall_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { document.writeTo(it) }
            return file
        } finally {
            activePage?.abort()
            document.close()
        }
    }

    private fun fmt(value: Double): String = DisplayFormat.number(value, 4)

    private fun paint(size: Float, color: Int = navy, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setLineSpacing(3f, 1f).build()

    private class ReportPage(private val document: PdfDocument) {
        private var finished = false
        private var number = 1
        private var page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
        private val canvas get() = page.canvas
        var y = 68f
        fun begin() { banner() }
        private fun banner() {
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), 40f, paint(1f, navy))
            canvas.drawText("PiCal", LEFT, 26f, paint(12f, Color.WHITE, true))
            canvas.drawText("ENGINEERING / PIPE WALL", 340f, 26f, paint(9f, Color.WHITE))
        }
        private fun footer() {
            canvas.drawLine(LEFT, 802f, WIDTH - LEFT, 802f, paint(1f, Color.LTGRAY))
            canvas.drawText("PiCal | User-supplied design and inspection data", LEFT, 819f, paint(8f, muted))
            canvas.drawText("$number", WIDTH - LEFT - 8f, 819f, paint(9f, muted))
        }
        fun newPage() {
            footer(); document.finishPage(page); number++
            page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, number).create())
            y = 62f; banner()
        }
        fun finish() { if (!finished) { footer(); document.finishPage(page); finished = true } }
        fun abort() { if (!finished) { document.finishPage(page); finished = true } }
        private fun space(height: Float) { if (y + height > BOTTOM) newPage() }
        fun heading(title: String, subtitle: String) { paragraph(title, 23f, navy, true); paragraph(subtitle, 11f, muted) }
        fun section(title: String) {
            space(100f); y += 6f
            canvas.drawRect(LEFT, y, WIDTH - LEFT, y + 25f, paint(1f, pale))
            canvas.drawText(title, LEFT + 9f, y + 17f, paint(10f, teal, true)); y += 32f
        }
        fun hero(measuredMm: Double?, passes: Boolean?) {
            space(142f)
            canvas.drawRoundRect(LEFT, y, WIDTH - LEFT, y + 127f, 8f, 8f, paint(1f, navy))
            canvas.drawText("MINIMUM MEASURED WALL AFTER FABRICATION", LEFT + 16f, y + 24f, paint(10f, Color.WHITE))
            canvas.drawText(measuredMm?.let { "${fmt(it)} mm" } ?: "NOT PROVIDED", LEFT + 16f, y + 64f, paint(28f, Color.WHITE, true))
            val status = when (passes) { true -> "MEETS the internal-pressure wall requirement"; false -> "BELOW the internal-pressure wall requirement"; null -> "NOT EVALUATED - physical measurement required" }
            canvas.drawText(status, LEFT + 16f, y + 91f, paint(11f, if (passes == false) Color.rgb(255, 181, 173) else Color.rgb(156, 232, 214), true))
            canvas.drawText("User-entered inspection minimum | Not calculated from Schedule", LEFT + 16f, y + 112f, paint(8.5f, Color.WHITE)); y += 142f
        }
        fun row(label: String, value: String) {
            val key = layout(label, paint(9.5f, muted), 205)
            val data = layout(value, paint(10f, navy, true), 290)
            val height = maxOf(key.height, data.height).toFloat() + 8f
            if (height > 650f) { paragraph(label, 9.5f, muted, true); paragraph(value); return }
            space(height)
            canvas.save(); canvas.translate(LEFT, y); key.draw(canvas); canvas.restore()
            canvas.save(); canvas.translate(LEFT + 225f, y); data.draw(canvas); canvas.restore()
            y += height; canvas.drawLine(LEFT, y - 5f, WIDTH - LEFT, y - 5f, paint(1f, pale))
        }
        /** Paginate shaped text, including RTL names and long unbroken references. */
        fun paragraph(text: String, size: Float = 10f, color: Int = muted, bold: Boolean = false) {
            val block = layout(text, paint(size, color, bold), (WIDTH - 2 * LEFT).toInt())
            if (block.height < 250) space(block.height.toFloat())
            var line = 0
            while (line < block.lineCount) {
                space((block.getLineBottom(line) - block.getLineTop(line)).toFloat())
                val top = block.getLineTop(line)
                var end = line + 1
                while (end < block.lineCount && block.getLineBottom(end) - top <= BOTTOM - y) end++
                val height = (block.getLineBottom(end - 1) - top).toFloat()
                canvas.save(); canvas.clipRect(LEFT, y, WIDTH - LEFT, y + height)
                canvas.translate(LEFT, y - top); block.draw(canvas); canvas.restore()
                y += height; line = end
                if (line < block.lineCount) newPage()
            }
            y += 7f
        }
    }
}
