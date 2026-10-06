package com.ahmedismail.flowtrack.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Spec §7: export a formatted A4 PDF — project info, per-area/per-task
 * progress table, total Pipe Inches, and the "By. Ahmed Ismail" footer.
 * Also embeds any photos attached to progress entries (camera/gallery),
 * grouped under the area they belong to.
 *
 * Uses Android's built-in PdfDocument API (spec §9) rather than a third-party
 * dependency. Paginates automatically once content (including photo grids)
 * overflows a page — no longer a single-page-only report.
 */
object ReportGenerator {

    // A4 at 72dpi: 595 x 842 points.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val FOOTER_RESERVE = 30f
    private const val THUMB_W = 150f
    private const val THUMB_H = 110f
    private const val THUMB_GAP = 14f
    private const val THUMBS_PER_ROW = 3

    fun generate(
        context: Context,
        project: Project,
        areas: List<Area>,
        tasks: List<TaskItem>,
        entries: List<ProgressEntry>,
        periodLabel: String? = null,
        outputDir: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    ): File {
        val document = PdfDocument()
        val cursor = PdfCursor(document)

        // "Total Pipe Inches"/"Total Joints"/"Joints by Diameter" reflect
        // work that reached the LAST stage of the Workflow — a joint gets a
        // separate entry logged at each stage it passes through, so
        // summing across every entry would count the same physical joints
        // once per stage instead of once as "done". See DashboardViewModel
        // for the same fix applied to the in-app stat cards.
        val lastOrderIndex = tasks.maxOfOrNull { it.orderIndex }
        val finalStageEntries = if (lastOrderIndex != null) {
            entries.filter { entry -> tasks.find { it.id == entry.taskId }?.orderIndex == lastOrderIndex }
        } else {
            emptyList()
        }

        cursor.y = drawHeader(cursor.canvas, project, cursor.y, periodLabel)
        cursor.y = drawSummary(cursor.canvas, finalStageEntries, tasks, cursor.y)
        drawJointsByDiameterSection(cursor, finalStageEntries)

        for (area in areas) {
            cursor.y = drawAreaTable(cursor, area, tasks, entries)

            val photosForArea = entries.filter { it.areaId == area.id && it.photoUri != null }
            if (photosForArea.isNotEmpty()) {
                cursor.y = drawPhotoSection(context, cursor, area, tasks, photosForArea)
            }
        }

        cursor.finish()

        if (!outputDir.exists()) outputDir.mkdirs()
        val fileName = "PiCal_${fileNameSafe(project.name, "Project")}_${System.currentTimeMillis()}.pdf"
        val file = File(outputDir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    /** Tracks the current page/canvas/y-position and starts a new page whenever the next block won't fit. */
    private class PdfCursor(private val document: PdfDocument) {
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

    private fun drawHeader(canvas: Canvas, project: Project, startY: Float, periodLabel: String? = null): Float {
        var y = startY
        val title = Paint().apply { color = Color.parseColor("#14263D"); textSize = 20f; isFakeBoldText = true }
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val value = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 12f }

        canvas.drawText("PiCal — Site Progress Report", MARGIN, y, title)
        y += 26f

        val fields = listOfNotNull(
            "Project" to project.name,
            project.number?.let { "Number" to it },
            "Responsible Engineer" to project.responsibleEngineer,
            project.contractor?.let { "Contractor" to it },
            project.client?.let { "Client" to it },
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

    private fun drawSummary(canvas: Canvas, entries: List<ProgressEntry>, tasks: List<TaskItem>, startY: Float): Float {
        var y = startY
        val label = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 10f }
        val num = Paint().apply { color = Color.parseColor("#14263D"); textSize = 16f; isFakeBoldText = true }

        val totalPipeInches = entries.sumOf { it.pipeInches }
        val totalJoints = entries.sumOf { it.jointQuantity }

        val stats = listOf(
            "COMPLETED PIPE INCHES" to String.format(Locale.US, "%,.1f\"", totalPipeInches),
            "COMPLETED JOINTS" to totalJoints.toString(),
            "WORKFLOW STAGES" to tasks.size.toString()
        )
        var x = MARGIN
        for ((k, v) in stats) {
            canvas.drawText(k, x, y, label)
            canvas.drawText(v, x, y + 20f, num)
            x += 175f
        }
        y += 30f
        val note = Paint().apply { color = Color.parseColor("#8C3413"); textSize = 8f }
        canvas.drawText(
            "\"Completed\" = reached the final Workflow stage. Per-stage progress for every task is in the table below.",
            MARGIN, y, note
        )
        return y + 20f
    }

    /**
     * Per-diameter joint totals with each contributing entry's own date
     * listed underneath — e.g. 16" (Inch): 3 joints on 30/08/2026, 5 joints
     * on 29/08/2026, total 8. Sits above the per-area tables since it's a
     * project-wide rollup, not scoped to one area.
     */
    private fun drawJointsByDiameterSection(cursor: PdfCursor, entries: List<ProgressEntry>) {
        if (entries.isEmpty()) return
        val sectionLabel = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val groupHeader = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 10.5f; isFakeBoldText = true }
        val groupTotal = Paint().apply { color = Color.parseColor("#2D6E9E"); textSize = 10.5f; isFakeBoldText = true }
        val rowText = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9.5f }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        cursor.ensureSpace(20f)
        cursor.canvas.drawText("COMPLETED JOINTS BY DIAMETER", MARGIN, cursor.y, sectionLabel)
        cursor.y += 18f

        val grouped = entries.groupBy { it.diameter to it.unit }.entries.sortedByDescending { it.key.first }
        for (group in grouped) {
            val (diameter, unit) = group.key
            // Append the inch mark only for diameters actually recorded in
            // inches — a millimetre group must never read "100"" (mm).
            val unitSuffix = if (unit == DiameterUnit.INCH) "\"" else " mm"
            val total = group.value.sumOf { it.jointQuantity }

            cursor.ensureSpace(15f)
            cursor.canvas.drawText("${formatDiameter(diameter)}$unitSuffix", MARGIN, cursor.y, groupHeader)
            cursor.canvas.drawText("Total: $total joints", MARGIN + 300f, cursor.y, groupTotal)
            cursor.y += 14f

            for (entry in group.value.sortedByDescending { it.date }) {
                cursor.ensureSpace(13f)
                cursor.canvas.drawText(
                    "${dateFormat.format(Date(entry.date))} — ${entry.jointQuantity} joints",
                    MARGIN + 12f, cursor.y, rowText
                )
                cursor.y += 13f
            }
            cursor.y += 6f
        }
        cursor.y += 8f
    }

    /** Trims a trailing ".0" so whole-number diameters (the common case) read as "16" not "16.0". */
    private fun formatDiameter(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun drawAreaTable(cursor: PdfCursor, area: Area, tasks: List<TaskItem>, entries: List<ProgressEntry>): Float {
        val header = Paint().apply { color = Color.parseColor("#1C3652"); textSize = 12f; isFakeBoldText = true }
        val rowLabel = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 10.5f }
        val rowValue = Paint().apply { color = Color.parseColor("#2D6E9E"); textSize = 10.5f; isFakeBoldText = true }
        val valueColumnX = MARGIN + 260f
        val labelWidth = valueColumnX - (MARGIN + 10f) - 8f
        val headerWidth = PAGE_WIDTH - 2 * MARGIN

        // Header plus the first row, so a table never starts as an orphan caption.
        cursor.ensureSpace(33f)
        cursor.canvas.drawText(fitText("Area: ${area.name}", header, headerWidth), MARGIN, cursor.y, header)
        cursor.y += 18f

        var headerPage = cursor.pageNumber
        for (task in tasks) {
            cursor.ensureSpace(15f)
            // A table longer than one page continues instead of running off the
            // sheet; the area caption is repeated so the rows stay attributable.
            if (cursor.pageNumber != headerPage) {
                cursor.canvas.drawText(fitText("Area: ${area.name} (cont.)", header, headerWidth), MARGIN, cursor.y, header)
                cursor.y += 18f
                headerPage = cursor.pageNumber
            }
            val jointsForTask = entries.filter { it.taskId == task.id && it.areaId == area.id }.sumOf { it.jointQuantity }
            val pipeInchesForTask = entries.filter { it.taskId == task.id && it.areaId == area.id }.sumOf { it.pipeInches }
            cursor.canvas.drawText(fitText(task.name, rowLabel, labelWidth), MARGIN + 10f, cursor.y, rowLabel)
            cursor.canvas.drawText(
                String.format(Locale.US, "%d joints  ·  %,.1f\"", jointsForTask, pipeInchesForTask),
                valueColumnX, cursor.y, rowValue
            )
            cursor.y += 15f
        }
        return cursor.y + 15f
    }

    /**
     * Draws a small photo gallery for one area: every entry in it that has a
     * photoUri, captioned with its task name + date, laid out 3-per-row and
     * paginating automatically as the gallery grows.
     */
    private fun drawPhotoSection(
        context: Context,
        cursor: PdfCursor,
        area: Area,
        tasks: List<TaskItem>,
        photoEntries: List<ProgressEntry>
    ): Float {
        val sectionLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9.5f; isFakeBoldText = true }
        val caption = Paint().apply { color = Color.parseColor("#1F2A37"); textSize = 8f }
        val borderPaint = Paint().apply { color = Color.parseColor("#E1E5E9"); style = Paint.Style.STROKE; strokeWidth = 1f }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        cursor.ensureSpace(16f)
        var y = cursor.y
        cursor.canvas.drawText(fitText("PHOTOS — ${area.name}".uppercase(), sectionLabel, PAGE_WIDTH - 2 * MARGIN), MARGIN, y, sectionLabel)
        y += 12f
        cursor.y = y

        photoEntries.chunked(THUMBS_PER_ROW).forEach { row ->
            cursor.ensureSpace(THUMB_H + 20f)
            y = cursor.y
            var x = MARGIN
            for (entry in row) {
                val bitmap = decodeThumbnail(context, entry.photoUri!!, THUMB_W.toInt(), THUMB_H.toInt())
                val rect = RectF(x, y, x + THUMB_W, y + THUMB_H)
                if (bitmap != null) {
                    cursor.canvas.drawBitmap(bitmap, null, rect, null)
                } else {
                    cursor.canvas.drawRect(rect, Paint().apply { color = Color.parseColor("#EFF1F3") })
                }
                cursor.canvas.drawRect(rect, borderPaint)

                val taskName = tasks.find { it.id == entry.taskId }?.name ?: ""
                cursor.canvas.drawText(fitText("$taskName — ${dateFormat.format(Date(entry.date))}", caption, THUMB_W), x, y + THUMB_H + 12f, caption)
                x += THUMB_W + THUMB_GAP
            }
            cursor.y = y + THUMB_H + 20f
        }
        return cursor.y + 8f
    }

    /**
     * Decodes and downsamples a stored photo (camera FileProvider or gallery
     * content:// URI) for embedding. Uses inSampleSize to downscale DURING
     * decode rather than loading the full-resolution bitmap into memory
     * first — a real camera photo (often 12MP+) decoded at full size before
     * scaling down can OOM, especially on the budget devices this app
     * targets. Returns null on any failure so a missing/revoked photo never
     * breaks report export.
     */
    private fun decodeThumbnail(context: Context, uriString: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)

            // Pass 1: read only the dimensions, no pixel data allocated.
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            val width = boundsOptions.outWidth
            val height = boundsOptions.outHeight
            if (width <= 0 || height <= 0) return null

            var sampleSize = 1
            var halfWidth = width / 2
            var halfHeight = height / 2
            while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
                sampleSize *= 2
            }

            // Pass 2: actually decode, downsampled — content:// streams generally
            // can't be reset, so this needs its own fresh InputStream.
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val original = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null
            Bitmap.createScaledBitmap(original, reqWidth, reqHeight, true)
        } catch (t: Throwable) {
            null
        }
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int) {
        val footer = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 9f; textAlign = Paint.Align.CENTER }
        canvas.drawText("By. Ahmed Ismail", PAGE_WIDTH / 2f, PAGE_HEIGHT - 24f, footer)
        val pageLabel = Paint().apply { color = Color.parseColor("#5B6B7A"); textSize = 8.5f; textAlign = Paint.Align.RIGHT }
        canvas.drawText(pageNumber.toString(), PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 24f, pageLabel)
    }
}
