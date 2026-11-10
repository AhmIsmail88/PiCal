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
        CalculatorReportFixtures.generate(File(args.first()))
        } catch (error: Throwable) {
            error.printStackTrace(System.err)
        }
    }
}
