package com.ahmedismail.flowtrack.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.AttendanceRecord
import com.ahmedismail.flowtrack.data.entity.AttendanceStatus
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.data.entity.TeamRole
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A per-employee A4 PDF: name, role, and their achievement over a period —
 * total joints, total Pipe Inches, total hours worked — plus a dated ledger
 * of every entry they were "Performed By" on, with its task, area, and
 * diameter. Built from the same ProgressEntry rows as the project report,
 * just filtered to one team member (and an optional date range).
 */
object EmployeeReportGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val FOOTER_RESERVE = 30f

    fun generate(
        project: Project,
        member: TeamMember,
        entries: List<ProgressEntry>,
        tasks: List<TaskItem>,
        areas: List<Area>,
        attendanceRecords: List<AttendanceRecord> = emptyList(),
        periodLabel: String?,
        outputDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    ): File {
        val document = PdfDocument()
        val cursor = Cursor(document)

        cursor.y = drawHeader(cursor.canvas, project, member, periodLabel, cursor.y)
        cursor.y = drawSummary(cursor.canvas, entries, cursor.y)
        drawAttendanceSummary(cursor, attendanceRecords)
        drawLedger(cursor, entries, tasks, areas)

        cursor.finish()

        if (!outputDir.exists()) outputDir.mkdirs()
        val safeName = fileNameSafe(member.name, "Member")
        val fileName = "PiCal_Employee_${safeName}_${System.currentTimeMillis()}.pdf"
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

    private fun roleDisplayName(member: TeamMember): String =
        if (member.role == TeamRole.OTHER && !member.customRoleLabel.isNullOrBlank()) {
            member.customRoleLabel
        } else {
            member.role.name.lowercase().replaceFirstChar { it.uppercase() }
        }

    private fun drawHeader(canvas: Canvas, project: Project, member: TeamMember, periodLabel: String?, startY: Float): Float {
        var y = startY
        val title = Paint().apply { color = Color.parseColor("#14263D"); textSize = 20f; isFakeBoldText = true }
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val value = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 12f }

        canvas.drawText("PiCal — Employee Report", MARGIN, y, title)
        y += 26f

        val fields = listOfNotNull(
            "Name" to member.name,
            "Role" to roleDisplayName(member),
            "Project" to project.name,
            periodLabel?.let { "Period" to it },
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

    private fun drawSummary(canvas: Canvas, entries: List<ProgressEntry>, startY: Float): Float {
        var y = startY
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val num = Paint().apply { color = Color.parseColor("#14263D"); textSize = 16f; isFakeBoldText = true }

        val totalJoints = entries.sumOf { it.jointQuantity }
        val totalPipeInches = entries.sumOf { it.pipeInches }
        val hoursLogged = entries.mapNotNull { it.hoursWorked }
        val totalHours = hoursLogged.sum()

        val stats = listOf(
            "TOTAL JOINTS" to totalJoints.toString(),
            "TOTAL PIPE INCHES" to String.format(Locale.US, "%,.1f\"", totalPipeInches),
            // Hours are optional per entry: with none logged, say so instead of
            // printing a "0.0" that reads like genuinely recorded time.
            "TOTAL HOURS" to if (hoursLogged.isEmpty()) "Not recorded" else String.format(Locale.US, "%,.1f", totalHours)
        )
        var x = MARGIN
        for ((k, v) in stats) {
            canvas.drawText(k, x, y, label)
            canvas.drawText(v, x, y + 20f, num)
            x += 175f
        }
        return y + 45f
    }

    /**
     * A separate section from the joints/Pipe Inches/hours achievement
     * stats above — attendance rate is a distinct measure (were they on
     * site) from output (what they got done), and keeping them visually
     * apart avoids the two kinds of numbers reading as one blended metric.
     * Late still counts as attended for the rate; only Absent doesn't.
     */
    private fun drawAttendanceSummary(cursor: Cursor, records: List<AttendanceRecord>) {
        if (records.isEmpty()) return
        cursor.ensureSpace(40f)
        var y = cursor.y
        val sectionLabel = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 11f; isFakeBoldText = true }
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9.5f }
        val num = Paint().apply { color = Color.parseColor("#14263D"); textSize = 14f; isFakeBoldText = true }

        cursor.canvas.drawText("ATTENDANCE", MARGIN, y, sectionLabel)
        y += 18f

        val present = records.count { it.status == AttendanceStatus.PRESENT }
        val late = records.count { it.status == AttendanceStatus.LATE }
        val absent = records.count { it.status == AttendanceStatus.ABSENT }
        val rate = if (records.isNotEmpty()) (present + late).toDouble() / records.size * 100 else 0.0

        val stats = listOf(
            "DAYS PRESENT" to present.toString(),
            "DAYS LATE" to late.toString(),
            "DAYS ABSENT" to absent.toString(),
            "ATTENDANCE RATE" to String.format(Locale.US, "%.0f%%", rate)
        )
        var x = MARGIN
        for ((k, v) in stats) {
            cursor.canvas.drawText(k, x, y, label)
            cursor.canvas.drawText(v, x, y + 18f, num)
            x += 135f
        }
        cursor.y = y + 40f
    }

    /** One line per entry: date, task, area, diameter, joints, and hours if logged. */
    private fun drawLedger(cursor: Cursor, entries: List<ProgressEntry>, tasks: List<TaskItem>, areas: List<Area>) {
        if (entries.isEmpty()) return
        val sectionLabel = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val rowLabel = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 9.5f }
        val rowValue = Paint().apply { color = Color.parseColor("#2D6E9E"); textSize = 9.5f; isFakeBoldText = true }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        cursor.ensureSpace(20f)
        cursor.canvas.drawText("WORK LOG", MARGIN, cursor.y, sectionLabel)
        cursor.y += 16f

        for (entry in entries.sortedByDescending { it.date }) {
            cursor.ensureSpace(14f)
            val task = tasks.find { it.id == entry.taskId }?.name ?: "—"
            val area = areas.find { it.id == entry.areaId }?.name ?: "—"
            val unitLabel = if (entry.unit == DiameterUnit.INCH) "\"" else "mm"
            val hoursPart = entry.hoursWorked?.let { String.format(Locale.US, "  ·  %.1fh", it) } ?: ""

            cursor.canvas.drawText(dateFormat.format(Date(entry.date)), MARGIN, cursor.y, rowLabel)
            cursor.canvas.drawText(fitText("$task — $area", rowLabel, MARGIN + 300f - (MARGIN + 75f) - 8f), MARGIN + 75f, cursor.y, rowLabel)
            cursor.canvas.drawText(
                "${formatDiameter(entry.diameter)}$unitLabel  ·  ${entry.jointQuantity} joints$hoursPart",
                MARGIN + 300f, cursor.y, rowValue
            )
            cursor.y += 14f
        }
    }

    private fun formatDiameter(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footer = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9f; textAlign = Paint.Align.CENTER }
        canvas.drawText("By. Ahmed Ismail", PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footer)
        val pageLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 8.5f; textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, pageLabel)
    }
}
