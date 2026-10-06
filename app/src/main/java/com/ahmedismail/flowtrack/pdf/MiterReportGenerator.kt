package com.ahmedismail.flowtrack.pdf

import com.ahmedismail.flowtrack.util.*
import java.io.File

/** Recalculate from raw inputs; segment thickness is never a qualification certificate. */
object MiterReportGenerator {
    fun generate(code: PipingCode, project: String?, engineer: String?, reference: String?,
        material: String?, temperatureC: Double?, b313Input: B313MiterInput?, pressureBar: Double,
        thetaDeg: Double, conditions: B311MiterConditions?, segment: B311MiterSegmentInput?, outputDir: File): File {
        val input = if (code == PipingCode.B31_3_PROCESS) requireNotNull(b313Input) else null
        val result313 = input?.let { B313MiterCalculator.calculate(it) }
        val result311 = if (input == null) B311MiterCalculator.calculate(pressureBar, thetaDeg, requireNotNull(conditions)) else null
        val requiredSegment = segment?.let { B311MiterSegmentCalculator.requiredThicknessMm(it) }
        if (input != null) {
            require(temperatureC != null && temperatureC.isFinite() && temperatureC > -273.15)
            require(input.designPressureBar == pressureBar && input.geometry.thetaDeg == thetaDeg)
        }
        segment?.let { require(it.thetaDeg == thetaDeg) }
        val report = CalculatorReport("Miter Bend Calculation Report",
            if (input != null) "ASME B31.3-2018 | Internal pressure" else "ASME B31.1-2022 | Pressure path and segment wall",
            if (input != null) EngineeringEquations.MITER_313 else EngineeringEquations.MITER_311)
        report.heading(project, engineer, reference)
        report.section("01 INPUTS")
        report.row("Design gauge pressure", "${fmt(pressureBar)} bar")
        report.row("Miter cut angle theta", "${fmt(thetaDeg)} deg")
        if (input != null && result313 != null) {
            report.row("Material / product", material?.ifBlank { null } ?: "Not recorded")
            report.row("Design metal temperature", "${fmt(temperatureC!!)} C")
            report.row("S at design temperature", "${fmt(input.allowableStressMPa)} MPa; user supplied, A-1/A-1M")
            report.row("E / W", "${fmt(input.qualityFactorE)} / ${fmt(input.weldStrengthReductionW)}")
            report.row("OD D / segment wall T", "${fmt(input.geometry.outsideDiameterMm)} / ${fmt(input.geometry.thicknessMm)} mm")
            report.row("Total allowances c", "${fmt(input.allowancesMm)} mm")
            report.row("Miter weld joint count", input.geometry.jointCount.toString())
            report.row("Effective radius R1", "${fmt(input.geometry.effectiveRadiusMm)} mm")
            report.section("02 RESULTS")
            report.row("Governing equation", result313.equationId)
            report.row("Maximum pressure Pm", "${fmt(result313.maxAllowablePressureBar)} bar")
            report.row("Pressure margin Pm-P", "${fmt(result313.marginBar)} bar")
            report.row("Required R1 / wall extension M", "${fmt(result313.minimumEffectiveRadiusMm)} / ${fmt(result313.minimumWallExtensionMm)} mm")
            report.row("Minimum T meeting pressure", B313MiterCalculator.requiredThicknessMm(input)?.let { "${fmt(it)} mm; numerical solution" } ?: "No supported solution")
            report.paragraph(if (result313.passes) "MEETS the implemented internal-pressure equations." else "BELOW the internal-pressure requirement.",
                10f, if (result313.passes) CalculatorReport.PASS else CalculatorReport.FAIL, true)
            report.paragraph("Provide the required extension M in the actual fabrication geometry; the app does not measure it. Overall design, material suitability, fabrication and other loads require independent verification.")
        } else {
            val declared = requireNotNull(conditions)
            val result = requireNotNull(result311)
            report.row("Nominal wall tn / crotch B", "${fmt(declared.nominalThicknessMm)} / ${fmt(declared.crotchDimensionBMm)} mm")
            report.section("02 DECLARED CONDITIONS AND PATH")
            result.conditions.forEach { report.paragraph("${it.id} ${it.description}: ${if (it.satisfied) "Declared met" else "NOT met"}") }
            report.row("Selected path", result.pathId)
            report.row("Pressure path limit", result.pressureLimitBar?.let { "${fmt(it)} bar" } ?: "Not a simplified path")
            report.paragraph(if (result.qualificationRequired) "QUALIFICATION REQUIRED under 104.7. No approved pressure verdict is issued." else "Pressure and declared conditions meet the selected simplified path only.", 10f)
            report.section("03 SEGMENT THICKNESS")
            if (segment == null) report.paragraph("Not evaluated: tm, r and spacing were not supplied.")
            else {
                report.row("Declared spacing", segment.spacing.name)
                report.row("Straight-pipe minimum tm", "${fmt(segment.straightMinimumThicknessMm)} mm")
                report.row("Pipe radius r", "${fmt(segment.meanRadiusMm)} mm")
                if (segment.spacing == MiterSpacing.CLOSE) report.row("Bend radius R", "${fmt(requireNotNull(segment.bendRadiusMm))} mm")
                report.row("Required segment wall ts", "${fmt(requireNotNull(requiredSegment))} mm")
                report.paragraph("104.2.3(c)(3) wall calculation does not replace qualification under 104.7. Geometry and spacing are designer declarations using ASME B31J.")
            }
        }
        report.section("04 REFERENCES AND LIMITS")
        report.paragraph("Sources: B31.3-2018 304.2.3, supplied printed pp.22-23; B31.1-2022 104.2.3, supplied printed p.29. Inputs are user supplied. No external-pressure, fatigue, remaining-life or complete code qualification is performed.")
        return report.write(outputDir, "PiCal_Miter")
    }
    private fun fmt(value: Double) = DisplayFormat.number(value, 4)
}
