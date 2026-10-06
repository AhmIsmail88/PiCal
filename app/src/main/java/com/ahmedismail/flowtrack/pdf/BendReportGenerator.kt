package com.ahmedismail.flowtrack.pdf

import com.ahmedismail.flowtrack.util.BendInput
import com.ahmedismail.flowtrack.util.BendThicknessCalculator
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.EngineeringEquations
import java.io.File

/**
 * ASME B31.3-2018 §304.2.1 pipe bend report. Values are recalculated from the
 * raw inputs at export time so a stale on-screen result can never be printed.
 */
object BendReportGenerator {
    fun generate(
        projectName: String?,
        engineerName: String?,
        materialName: String?,
        designReference: String?,
        outsideDiameterMm: Double,
        bendRadiusMm: Double,
        designPressureBar: Double,
        allowableStressMPa: Double,
        qualityFactorE: Double,
        weldStrengthReductionW: Double,
        yCoefficient: Double,
        corrosionAllowanceMm: Double,
        erosionAllowanceMm: Double,
        mechanicalAllowanceMm: Double,
        measuredMinimumMm: Double?,
        measurementReference: String?,
        outputDir: File,
        designTemperatureC: Double = 20.0
    ): File {
        require(designTemperatureC.isFinite() && designTemperatureC > -273.15)
        val result = BendThicknessCalculator.calculate(
            BendInput(
                designPressureBar = designPressureBar, outsideDiameterMm = outsideDiameterMm,
                bendRadiusMm = bendRadiusMm, allowableStressMPa = allowableStressMPa,
                qualityFactorE = qualityFactorE, weldStrengthReductionW = weldStrengthReductionW,
                yCoefficient = yCoefficient, corrosionAllowanceMm = corrosionAllowanceMm,
                erosionAllowanceMm = erosionAllowanceMm, mechanicalAllowanceMm = mechanicalAllowanceMm
            )
        )
        val measured = measuredMinimumMm?.let {
            BendThicknessCalculator.checkIntradosWall(result, outsideDiameterMm, it)
        }
        val report = CalculatorReport(
            "Pipe Bend Thickness Report",
            "ASME B31.3-2018 para. 304.2.1 | Pipe bend under internal pressure",
            EngineeringEquations.BEND
        )
        report.heading(projectName, engineerName, designReference)

        report.section("01  BEND GEOMETRY")
        report.row("Material / grade", materialName?.takeIf { it.isNotBlank() } ?: "Not provided")
        report.row("Outside diameter D", "${fmt(outsideDiameterMm)} mm")
        report.row("Bend radius to centreline R1", "${fmt(bendRadiusMm)} mm  (${fmt(bendRadiusMm / outsideDiameterMm)} D)")
        report.row("Inspection / pipe reference", measurementReference?.takeIf { it.isNotBlank() } ?: "Not provided")

        report.section("02  DESIGN INPUTS")
        report.row("Internal design gauge pressure P", "${fmt(designPressureBar)} bar(g) = ${fmt(designPressureBar * 0.1)} MPa")
        report.row("Allowable stress S", "${fmt(allowableStressMPa)} MPa")
        report.row("Design metal temperature", "${fmt(designTemperatureC)} C")
        report.row("Stress source", "B31.3-2018 A-1/A-1M; S supplied at design temperature")
        report.row("Quality factor E / weld factor W", "${fmt(qualityFactorE)} / ${fmt(weldStrengthReductionW)}")
        report.row("Coefficient Y", fmt(yCoefficient))
        report.row("Corrosion / erosion allowance", "${fmt(corrosionAllowanceMm)} / ${fmt(erosionAllowanceMm)} mm")
        report.row("Mechanical removal allowance", "${fmt(mechanicalAllowanceMm)} mm (before fabrication)")

        report.section("03  CALCULATION TRACE")
        report.paragraph("Use the equations above with the following geometry factors. The intrados factor is above 1 and governs the pressure wall.")
        report.row("Intrados factor I", fmt(result.intradosFactorI))
        report.row("Extrados factor I", fmt(result.extradosFactorI))
        report.row("Pressure thickness at intrados", "${fmt(result.intradosThicknessMm)} mm")
        report.row("Pressure thickness at extrados", "${fmt(result.extradosThicknessMm)} mm")
        report.row("Matching straight pipe, I = 1", "${fmt(result.straightThicknessMm)} mm")
        report.row("Bend wall before fabrication", "${fmt(result.intradosMinimumRequiredThicknessMm)} mm")
        report.row("Required remaining wall at intrados", "${fmt(result.intradosMinimumFinishedWallMm)} mm")

        report.section("04  MEASURED INTRADOS WALL", minimumContentHeight = 90f)
        report.row("Minimum measured intrados wall", measured?.let { "${fmt(it.measuredMinimumMm)} mm" } ?: "Not provided")
        report.row("Required remaining wall", "${fmt(result.intradosMinimumFinishedWallMm)} mm")
        report.row("Measured minus required", measured?.let { "${fmt(it.marginMm)} mm" } ?: "Not evaluated")
        val passes = measured?.passes
        report.paragraph(
            when (passes) {
                true -> "MEETS the internal-pressure wall requirement for these inputs."
                false -> "BELOW the internal-pressure wall requirement for these inputs."
                null -> "NOT EVALUATED - a measured intrados wall is required."
            },
            11f,
            if (passes == false) CalculatorReport.FAIL else CalculatorReport.PASS,
            true
        )

        report.section("05  LIMITS AND REFERENCES", minimumContentHeight = 48f)
        report.paragraph("Basis: ASME B31.3-2018, 304.2.1. Verify user-supplied S, E, W, Y and allowances against the code and project specification. This report checks required bend wall; it does not predict forming or geometric thinning. Excluded: miter bends (304.2.3), external pressure, other sustained/occasional loads, fatigue and remaining-life assessment.")
        return report.write(outputDir, "PiCal_PipeBend")
    }

    private fun fmt(value: Double): String = DisplayFormat.number(value, 4)
}
