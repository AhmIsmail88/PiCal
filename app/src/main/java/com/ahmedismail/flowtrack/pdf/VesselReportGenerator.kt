package com.ahmedismail.flowtrack.pdf

import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.EngineeringEquations
import com.ahmedismail.flowtrack.util.VesselInput
import com.ahmedismail.flowtrack.util.VesselPart
import com.ahmedismail.flowtrack.util.VesselThicknessCalculator
import java.io.File

/**
 * ASME BPVC Section VIII Division 1 pressure vessel report. Values are
 * recalculated from the raw inputs and the selected component at export time.
 */
object VesselReportGenerator {
    fun generate(
        projectName: String?,
        engineerName: String?,
        designReference: String?,
        part: VesselPart,
        input: VesselInput,
        nominalThicknessMm: Double?,
        outputDir: File,
        materialName: String? = null,
        designTemperatureC: Double = 20.0
    ): File {
        require(designTemperatureC.isFinite() && designTemperatureC > -273.15)
        val result = VesselThicknessCalculator.calculate(part, input)
        val mawp = nominalThicknessMm?.let {
            VesselThicknessCalculator.maximumAllowablePressureBar(part, input, it)
        }
        val report = CalculatorReport(
            "Pressure Vessel Thickness Report",
            "ASME BPVC Section VIII Division 1 | Internal pressure on the concave side",
            EngineeringEquations.vessel(part)
        )
        report.heading(projectName, engineerName, designReference)

        report.section("01  COMPONENT AND GEOMETRY")
        report.row("Component", partLabel(part))
        report.row("Code rule", partRule(part))
        report.row("Inside radius R (corroded)", "${fmt(input.insideRadiusMm)} mm")
        report.row("Inside diameter D", "${fmt(input.insideRadiusMm * 2.0)} mm")
        when (part) {
            VesselPart.TORISPHERICAL_HEAD -> {
                val crown = input.crownRadiusMm ?: (input.insideRadiusMm * 2.0)
                val knuckle = input.knuckleRadiusMm ?: (crown * 0.06)
                report.row("Inside crown radius L", "${fmt(crown)} mm")
                report.row("Inside knuckle radius r", "${fmt(knuckle)} mm (${fmt(knuckle / crown * 100.0)}% of L)")
            }
            VesselPart.CONICAL_HEAD -> report.row("Half-apex angle", "${fmt(input.halfApexAngleDeg)} deg")
            VesselPart.ELLIPSOIDAL_HEAD_2_1 -> {
                report.row("Ellipsoidal ratio D/2h", fmt(input.ellipsoidalRatio))
                report.row("Shape factor K", fmt((2.0 + input.ellipsoidalRatio * input.ellipsoidalRatio) / 6.0))
            }
            else -> Unit
        }

        report.section("02  DESIGN INPUTS")
        report.row("Material / product", materialName?.ifBlank { null } ?: "User-supplied; not recorded")
        report.row("Design metal temperature", "${fmt(designTemperatureC)} C")
        report.row("Stress source", "Section II-D 1A/1B; S supplied at material/temperature")
        report.row("Internal design gauge pressure P", "${fmt(input.designPressureBar)} bar(g) = ${fmt(input.designPressureBar * 0.1)} MPa")
        report.row("Allowable stress S", "${fmt(input.allowableStressMPa)} MPa")
        report.row("Joint efficiency E (Table UW-12)", fmt(input.jointEfficiency))
        report.row("Corrosion allowance", "${fmt(input.corrosionAllowanceMm)} mm")

        report.section("03  CALCULATION TRACE")
        report.paragraph(partEquation(part))
        if (part == VesselPart.CYLINDRICAL_SHELL) {
            result.circumferentialThicknessMm?.let { report.row("Circumferential stress, UG-27(c)(1)", "${fmt(it)} mm") }
            result.longitudinalThicknessMm?.let { report.row("Longitudinal stress, UG-27(c)(2)", "${fmt(it)} mm") }
        }
        report.row("Governing pressure thickness t", "${fmt(result.pressureThicknessMm)} mm")
        report.row("UG-16(b) minimum after forming", "${fmt(result.minimumFormedThicknessMm)} mm (excluding corrosion)")
        report.row("Minimum thickness to order", "${fmt(result.minimumOrderThicknessMm)} mm (max(t, minimum formed thickness) + corrosion)")

        report.section("04  MAXIMUM ALLOWABLE WORKING PRESSURE")
        report.row("Nominal plate thickness", nominalThicknessMm?.let { "${fmt(it)} mm" } ?: "Not provided")
        report.row("MAWP for that thickness", mawp?.let { "${fmt(it)} bar(g)" } ?: "Not evaluated")

        report.section("05  LIMITS AND REFERENCES", minimumContentHeight = 60f)
        report.paragraph("Basis: ASME BPVC Section VIII Division 1, UG-27(c) and UG-32(d)(e)(f)(g), with the UG-16(b) minimum formed thickness. S (Section II, Part D), E (Table UW-12) and the corrosion allowance are user inputs. Dimensions are stated in the corroded condition and the corrosion allowance is added to the computed thickness once. External pressure (UG-28), openings and reinforcement (UG-36/37/40), bolted flanges (Appendix 2), supports, fatigue, thermal discontinuity stress, the Division 2 alternative rules, and the thick-wall rules of Appendix 1-2 are outside this report.")
        return report.write(outputDir, "PiCal_PressureVessel")
    }

    private fun partLabel(part: VesselPart): String = when (part) {
        VesselPart.CYLINDRICAL_SHELL -> "Cylindrical shell, longitudinal seam"
        VesselPart.ELLIPSOIDAL_HEAD_2_1 -> "Ellipsoidal head"
        VesselPart.TORISPHERICAL_HEAD -> "Torispherical (flanged and dished) head"
        VesselPart.HEMISPHERICAL_HEAD -> "Hemispherical head"
        VesselPart.CONICAL_HEAD -> "Conical shell / head"
    }

    private fun partRule(part: VesselPart): String = when (part) {
        VesselPart.CYLINDRICAL_SHELL -> "UG-27(c)(1) and UG-27(c)(2)"
        VesselPart.ELLIPSOIDAL_HEAD_2_1 -> "UG-32(d)"
        VesselPart.TORISPHERICAL_HEAD -> "UG-32(e) / Appendix 1-4(d)"
        VesselPart.HEMISPHERICAL_HEAD -> "UG-32(f)"
        VesselPart.CONICAL_HEAD -> "UG-32(g)"
    }

    private fun partEquation(part: VesselPart): String = when (part) {
        VesselPart.CYLINDRICAL_SHELL ->
            "t = P x R / (S x E - 0.6 x P) for the circumferential stress at a longitudinal seam, and t = P x R / (2 x S x E + 0.4 x P) for the longitudinal stress at a circumferential seam."
        VesselPart.ELLIPSOIDAL_HEAD_2_1 ->
            "t = P x D x K / (2 x S x E - 0.2 x P), with K = (1/6) x [2 + (D/2h)^2]. K is 1 for the standard 2:1 head and 0.5 at D/2h = 1, where the equation becomes the hemispherical rule."
        VesselPart.TORISPHERICAL_HEAD ->
            "t = P x L x M / (2 x S x E - 0.2 x P), with M = 0.25 x (3 + sqrt(L/r)). For the standard 6% knuckle radius this matches the 0.885 form of UG-32(e)."
        VesselPart.HEMISPHERICAL_HEAD ->
            "t = P x L / (2 x S x E - 0.2 x P), with L the inside radius; limited to t <= 0.356L and P <= 0.665 x S x E."
        VesselPart.CONICAL_HEAD ->
            "t = P x D / (2 x cos(alpha) x (S x E - 0.6 x P)), with alpha the half-apex angle, up to 30 degrees."
    }

    private fun fmt(value: Double): String = DisplayFormat.number(value, 4)
}
