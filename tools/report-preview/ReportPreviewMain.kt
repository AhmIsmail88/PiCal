package com.ahmedismail.flowtrack

import com.ahmedismail.flowtrack.pdf.PipeThicknessReportGenerator
import com.ahmedismail.flowtrack.pdf.BendReportGenerator
import com.ahmedismail.flowtrack.pdf.VesselReportGenerator
import com.ahmedismail.flowtrack.pdf.MiterReportGenerator
import com.ahmedismail.flowtrack.pdf.PipeWeightReportGenerator
import com.ahmedismail.flowtrack.util.*
import com.ahmedismail.flowtrack.util.PipingCode
import com.ahmedismail.flowtrack.util.VesselInput
import com.ahmedismail.flowtrack.util.VesselPart
import java.io.File

/** Run through Android app_process for PDF QA without installing or updating the app. */
object ReportPreviewMain {
    @android.annotation.TargetApi(29)
    @Suppress("DEPRECATION")
    @JvmStatic fun main(args: Array<String>) {
        try {
        println("REPORT_PREVIEW_START")
        if (android.os.Looper.getMainLooper() == null) android.os.Looper.prepareMainLooper()
        // Standalone app_process does not receive the Zygote font preload.
        if (android.graphics.Typeface.DEFAULT == null) {
            val latin = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(File("/system/fonts/Roboto-Regular.ttf")).build()).build()
            val arabic = android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(File("/system/fonts/NotoNaskhArabic-Regular.ttf")).build()).build()
            val familyClass = android.graphics.fonts.FontFamily::class.java
            val getPtr = familyClass.getDeclaredMethod("getNativePtr").apply { isAccessible = true }
            val families = longArrayOf(getPtr.invoke(latin) as Long, getPtr.invoke(arabic) as Long)
            val typefaceClass = android.graphics.Typeface::class.java
            val create = typefaceClass.getDeclaredMethod("nativeCreateFromArray", LongArray::class.java, java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE).apply { isAccessible = true }
            val nativeFace = create.invoke(null, families, 0L, -1, -1) as Long
            val face = typefaceClass.getDeclaredConstructor(java.lang.Long.TYPE).apply { isAccessible = true }.newInstance(nativeFace)
            android.graphics.Typeface::class.java.getDeclaredMethod("setSystemFontMap", Map::class.java).apply { isAccessible = true }
                .invoke(null, mapOf("sans-serif" to face, "serif" to face, "monospace" to face))
        }
        val dir = File(args.first())
        PipingCode.values().forEach { code ->
        listOf("pass" to 3.5, "fail" to 3.0, "missing" to null, "long" to 3.478654).forEach { (name, measured) ->
            val longText = if (name == "long") "مرجع فحص الماسورة بعد التصنيع - PIPE-ABC1234567890".repeat(18) else "UT-EXAMPLE-001 | New pipe | Minimum at groove root"
            val report = PipeThicknessReportGenerator.generate(
                projectName = "Example only - مثال توضيحي", engineerName = "QA preview - not a real inspection",
                materialName = "Example steel - verify actual material grade", nps = "4 inch", outsideDiameterMm = 114.3,
                designPressureBar = 20.0, designTemperatureC = 20.0, allowableStressMPa = 100.0,
                qualityFactorE = 0.85, weldFactorW = 0.9, yCoefficient = 0.4,
                corrosionAllowanceMm = 1.5, erosionAllowanceMm = 0.5, mechanicalAllowanceMm = 1.0,
                designReference = "Illustrative coefficients only; not project design values.",
                millTolerancePercent = if (name == "missing") null else 12.5,
                measuredMinimumWallMm = measured, measurementReference = longText,
                selectedSchedule = "Sch 40", scheduleWallThicknessMm = 6.02, pipingCode = code, outputDir = dir)
            val filename = "${code.name.lowercase()}-$name"
            check(report.renameTo(File(dir, "$filename.pdf")))
            println("REPORT_OK $filename")
        }
        }
        val bend = BendReportGenerator.generate(projectName = "QA — مثال كوع", engineerName = "Device preview",
            designReference = "B31.3-2018 QA", materialName = "User coefficients - example only",
            outsideDiameterMm = 114.3, bendRadiusMm = 171.45, designPressureBar = 20.0,
            allowableStressMPa = 100.0, qualityFactorE = 0.85, weldStrengthReductionW = 0.9,
            yCoefficient = 0.4, corrosionAllowanceMm = 1.5, erosionAllowanceMm = 0.5, mechanicalAllowanceMm = 1.0,
            measuredMinimumMm = 4.0, measurementReference = "QA-UT", outputDir = dir)
        check(bend.renameTo(File(dir, "bend.pdf")))
        println("REPORT_OK bend")
        val vessel = VesselReportGenerator.generate(projectName = "QA — مثال وعاء", engineerName = "Device preview",
            designReference = "Internal pressure example", part = VesselPart.CYLINDRICAL_SHELL,
            input = VesselInput(10.0, 500.0, 138.0, 1.0, 3.0), nominalThicknessMm = 8.0, outputDir = dir)
        check(vessel.renameTo(File(dir, "vessel.pdf")))
        println("REPORT_OK vessel")
        val miter313 = MiterReportGenerator.generate(PipingCode.B31_3_PROCESS, "QA miter", "Device preview", "Illustrative coefficients only",
            "A106 Gr.B - user stress", 20.0, B313MiterInput(20.0, 100.0, 1.0, 1.0, 1.0, MiterGeometry(90.0, 2, 114.3, 6.02, 180.0)),
            20.0, 22.5, null, null, dir)
        check(miter313.renameTo(File(dir, "miter313.pdf")))
        println("REPORT_OK miter313")
        val miter311 = MiterReportGenerator.generate(PipingCode.B31_1_POWER, "QA miter", "Device preview", null, null, null, null,
            8.0, 22.5, B311MiterConditions(40.0, 6.02, true, true, true, true),
            B311MiterSegmentInput(5.0, 50.0, 22.5, MiterSpacing.CLOSE, 150.0), dir)
        check(miter311.renameTo(File(dir, "miter311.pdf")))
        println("REPORT_OK miter311")
        val weight = PipeWeightReportGenerator.generate("QA weight", "Device preview", "Carbon steel", "4 inch", 114.3, 6.02, 7850.0, 6.0,
            PipeWeightCalculator.calculate(PipeWeightInput(114.3, 6.02, 7850.0, 6.0, true)), dir)
        check(weight.renameTo(File(dir, "weight.pdf")))
        println("REPORT_OK weight")
        } catch (error: Throwable) {
            error.printStackTrace(System.err)
        }
    }
}
