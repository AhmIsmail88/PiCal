package com.ahmedismail.flowtrack.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A dedicated welding-productivity report — deliberately scoped to only
 * entries whose Task is of type WELDING (Argon, TIG, Electrical, etc. all
 * count; Fit-up, Inspection, and every other task type are excluded, since
 * welders and, say, fit-up crew are different people with different
 * output to track). Groups by welder ("Performed By"), and within each
 * welder, by the specific welding task/process.
 *
 * "Weld Inches" here is the standard industry diameter-inch convention —
 * one circumferential weld on an N-inch pipe counts as N weld-inches,
 * regardless of the pipe's actual wall thickness or the weld's true bead
 * length. That's exactly what Pipe Inches (diameter × joint quantity)
 * already stores on every entry, so no new field or calculation is
 * needed — this report just filters and re-groups the existing data.
 */
object WeldInchesReportGenerator {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val FOOTER_RESERVE = 30f

    fun generate(
        project: Project,
        weldingEntries: List<ProgressEntry>,
        tasks: List<TaskItem>,
        team: List<TeamMember>,
        periodLabel: String?,
        outputDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    ): File {
        val document = PdfDocument()
        val cursor = Cursor(document)

        cursor.y = drawHeader(cursor.canvas, project, periodLabel, cursor.y)
        cursor.y = drawSummary(cursor.canvas, weldingEntries, cursor.y)
        drawByWelder(cursor, weldingEntries, tasks, team)
        cursor.finish()

        if (!outputDir.exists()) outputDir.mkdirs()
        val fileName = "PiCal_WeldInches_${fileNameSafe(project.name, "Project")}_${System.currentTimeMillis()}.pdf"
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

    private fun drawHeader(canvas: Canvas, project: Project, periodLabel: String?, startY: Float): Float {
        var y = startY
        val title = Paint().apply { color = Color.parseColor("#14263D"); textSize = 20f; isFakeBoldText = true }
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val value = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 12f }

        canvas.drawText("PiCal — Weld Inches Report", MARGIN, y, title)
        y += 26f

        val fields = listOfNotNull(
            "Project" to project.name,
            project.number?.let { "Number" to it },
            "Responsible Engineer" to project.responsibleEngineer,
            periodLabel?.let { "Period" to it },
            "Date" to SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()),
            "Scope" to "Welding entries only (Fit-up, Inspection, and other task types excluded)"
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

    private fun drawSummary(canvas: Canvas, entries: List<ProgressEntry>, startY: Float): Float {
        var y = startY
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val num = Paint().apply { color = Color.parseColor("#14263D"); textSize = 16f; isFakeBoldText = true }

        val totalWeldInches = entries.sumOf { it.pipeInches }
        val totalJoints = entries.sumOf { it.jointQuantity }
        // Entries with no "Performed By" are shown as Unassigned further down;
        // counting that bucket as a welder would overstate the crew size.
        val welderCount = entries.mapNotNull { it.performedById }.distinct().size

        val stats = listOf(
            "TOTAL WELD-INCHES" to String.format(Locale.US, "%,.1f\"", totalWeldInches),
            "TOTAL WELD JOINTS" to totalJoints.toString(),
            "WELDERS" to welderCount.toString()
        )
        var x = MARGIN
        for ((k, v) in stats) {
            canvas.drawText(k, x, y, label)
            canvas.drawText(v, x, y + 20f, num)
            x += 175f
        }
        return y + 45f
    }

    /** Per welder, then per welding task/process within that welder (Argon vs Electrical, etc.), then a welder subtotal. */
    private fun drawByWelder(cursor: Cursor, entries: List<ProgressEntry>, tasks: List<TaskItem>, team: List<TeamMember>) {
        if (entries.isEmpty()) return
        val welderHeader = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val taskRow = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 10f }
        val taskValue = Paint().apply { color = Color.parseColor("#2D6E9E"); textSize = 10f; isFakeBoldText = true }
        val subtotal = Paint().apply { color = Color.parseColor("#14263D"); textSize = 10.5f; isFakeBoldText = true }

        val byWelder = entries.groupBy { it.performedById }
            .toList()
            .sortedByDescending { (_, list) -> list.sumOf { it.pipeInches } }

        for ((performedById, welderEntries) in byWelder) {
            cursor.ensureSpace(20f)
            val welderName = team.find { it.id == performedById }?.name ?: "Unassigned"
            cursor.canvas.drawText(welderName, MARGIN, cursor.y, welderHeader)
            cursor.y += 16f

            val byTask = welderEntries.groupBy { it.taskId }
            for ((taskId, taskEntries) in byTask) {
                cursor.ensureSpace(14f)
                val taskName = tasks.find { it.id == taskId }?.name ?: "—"
                val joints = taskEntries.sumOf { it.jointQuantity }
                val weldInches = taskEntries.sumOf { it.pipeInches }
                cursor.canvas.drawText(taskName, MARGIN + 10f, cursor.y, taskRow)
                cursor.canvas.drawText(
                    String.format(Locale.US, "%d joints  ·  %,.1f\"", joints, weldInches),
                    MARGIN + 280f, cursor.y, taskValue
                )
                cursor.y += 14f
            }

            cursor.ensureSpace(16f)
            val welderTotalJoints = welderEntries.sumOf { it.jointQuantity }
            val welderTotalWeldInches = welderEntries.sumOf { it.pipeInches }
            cursor.canvas.drawText(
                String.format(Locale.US, "Subtotal: %d joints, %,.1f\" weld-inches", welderTotalJoints, welderTotalWeldInches),
                MARGIN + 10f, cursor.y, subtotal
            )
            cursor.y += 22f
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footer = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9f; textAlign = Paint.Align.CENTER }
        canvas.drawText("By. Ahmed Ismail", PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footer)
        val pageLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 8.5f; textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, pageLabel)
    }
}
