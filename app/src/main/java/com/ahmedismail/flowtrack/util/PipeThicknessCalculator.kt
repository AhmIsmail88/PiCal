package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.reference.MaterialClass

data class PipeThicknessInput(
    val designPressureBar: Double,
    val outsideDiameterMm: Double,
    val allowableStressMPa: Double,
    val qualityFactorE: Double,
    val weldStrengthReductionW: Double,
    val yCoefficient: Double,
    val corrosionAllowanceMm: Double,
    val erosionAllowanceMm: Double = 0.0,
    /** Includes thread/groove depth and applicable machining tolerance. */
    val mechanicalAllowanceMm: Double = 0.0
)

data class PipeThicknessResult(
    /** t — pressure design thickness per ASME B31.3 Eq. (3a), §304.1.2. Does not include corrosion/mechanical allowances. */
    val calculatedThicknessMm: Double,
    /** t_m = t + c — total minimum required thickness per §304.1.1. */
    val minimumRequiredThicknessMm: Double,
    /** Remaining metal required at the thinnest finished location, after all machining. */
    val minimumFinishedWallMm: Double = minimumRequiredThicknessMm
)

/**
 * ASME B31.3-2018 §304.1.2 — pressure design thickness for straight pipe under
 * internal pressure: t = PD / [2(SEW + PY)].
 *
 * S, E and W are supplied by the designer for the material/product and design
 * temperature. This engine does not infer allowable stress from room-temperature
 * strengths or certify material suitability. Other loads/services are outside scope.
 */
object PipeThicknessCalculator {
    private const val BAR_TO_MPA = 0.1

    /**
     * Last heading of B31.3-2018 Table 304.1.1 (1250 °F and above).
     * A Y lookup does not establish the allowable temperature of a material.
     */
    const val Y_TABLE_MAX_TEMPERATURE_C = 677.0

    fun calculate(input: PipeThicknessInput): PipeThicknessResult {
        validateInputs(input)
        val pMPa = input.designPressureBar * BAR_TO_MPA
        val denominator = 2.0 * (input.allowableStressMPa * input.qualityFactorE * input.weldStrengthReductionW + pMPa * input.yCoefficient)
        require(denominator.isFinite() && denominator > 0.0) { "Invalid pressure-design denominator." }
        val t = (pMPa * input.outsideDiameterMm) / denominator
        val tm = t + input.corrosionAllowanceMm + input.erosionAllowanceMm + input.mechanicalAllowanceMm
        require(t.isFinite() && tm.isFinite()) { "Thickness exceeds the supported numeric range." }
        if (t >= input.outsideDiameterMm / 6.0 || pMPa / (input.allowableStressMPa * input.qualityFactorE) > 0.385) {
            throw ThicknessScopeException()
        }
        require(tm < input.outsideDiameterMm / 2.0) { "Required thickness leaves no bore." }
        return PipeThicknessResult(calculatedThicknessMm = t, minimumRequiredThicknessMm = tm,
            minimumFinishedWallMm = t + input.corrosionAllowanceMm + input.erosionAllowanceMm)
    }

    /** SI temperature points of B31.3-2018 Table 304.1.1; steel values remain 0.7 at 649/677 and above. */
    fun defaultYCoefficient(materialClass: MaterialClass, designTempC: Double): Double {
        require(designTempC.isFinite()) { "Temperature must be finite." }
        require(designTempC > -273.15) { "Temperature must exceed absolute zero." }
        val points = when (materialClass) {
            MaterialClass.FERRITIC -> listOf(482.0 to 0.4, 510.0 to 0.5, 538.0 to 0.7)
            MaterialClass.AUSTENITIC -> listOf(566.0 to 0.4, 593.0 to 0.5, 621.0 to 0.7)
        }
        return interpolateY(designTempC, points)
    }

    /**
     * §304.1.2 eq. (3b) — the inside-diameter alternative within the same
     * t < D/6 and P/(SE) ≤ 0.385 limits as eq. (3a):
     *
     *   t = P(d + 2c) / [2(SEW − P(1 − Y))]
     *
     * d is the inside diameter of the pipe whose wall has been selected, and
     * c = corrosion + erosion + mechanical allowances.
     */
    fun pressureThicknessInsideDiameterForm(input: PipeThicknessInput, insideDiameterMm: Double): Double {
        validateInputs(input)
        require(insideDiameterMm.isFinite() && insideDiameterMm > 0.0) { "Inside diameter must be positive." }
        require(insideDiameterMm < input.outsideDiameterMm) { "Inside diameter must be smaller than the outside diameter." }
        val pMpa = input.designPressureBar * BAR_TO_MPA
        val allowances = input.corrosionAllowanceMm + input.erosionAllowanceMm + input.mechanicalAllowanceMm
        val sew = input.allowableStressMPa * input.qualityFactorE * input.weldStrengthReductionW
        val denominator = 2.0 * (sew - pMpa * (1.0 - input.yCoefficient))
        require(denominator.isFinite() && denominator > 0.0) { "Invalid pressure-design denominator for Eq. (3b)." }
        val t = pMpa * (insideDiameterMm + 2.0 * allowances) / denominator
        require(t.isFinite() && t >= 0.0)
        if (t >= input.outsideDiameterMm / 6.0 || pMpa / (input.allowableStressMPa * input.qualityFactorE) > 0.385) throw ThicknessScopeException()
        require(t + allowances < input.outsideDiameterMm / 2.0) { "Required thickness leaves no bore." }
        return t
    }

    private fun validateInputs(input: PipeThicknessInput) {
        require(listOf(input.designPressureBar, input.outsideDiameterMm, input.allowableStressMPa,
            input.qualityFactorE, input.weldStrengthReductionW, input.yCoefficient, input.corrosionAllowanceMm,
            input.erosionAllowanceMm, input.mechanicalAllowanceMm).all { it.isFinite() }) { "All inputs must be finite." }
        require(input.designPressureBar >= 0.0 && input.outsideDiameterMm > 0.0 && input.allowableStressMPa > 0.0) { "Pressure must be nonnegative; diameter and allowable stress must be positive." }
        require(input.qualityFactorE > 0.0 && input.qualityFactorE <= 1.0 && input.weldStrengthReductionW > 0.0 && input.weldStrengthReductionW <= 1.0) { "E and W must be in (0, 1]." }
        require(input.yCoefficient >= 0.0 && input.yCoefficient <= 1.0 && input.corrosionAllowanceMm >= 0.0 &&
            input.erosionAllowanceMm >= 0.0 && input.mechanicalAllowanceMm >= 0.0) { "Y must be in [0, 1]; allowances must be nonnegative." }
    }

    fun requiredNominalThicknessMm(result: PipeThicknessResult, millTolerancePercent: Double): Double {        require(millTolerancePercent.isFinite() && millTolerancePercent >= 0 && millTolerancePercent < 100)
        require(result.minimumRequiredThicknessMm.isFinite() && result.minimumRequiredThicknessMm >= 0)
        return (result.minimumRequiredThicknessMm / (1 - millTolerancePercent / 100)).also { require(it.isFinite()) }
    }

    fun checkNominalWall(result: PipeThicknessResult, outsideDiameterMm: Double, nominalWallMm: Double, millTolerancePercent: Double): NominalWallCheck {
        val required = requiredNominalThicknessMm(result, millTolerancePercent)
        require(outsideDiameterMm.isFinite() && nominalWallMm.isFinite() && nominalWallMm > 0 && 2 * nominalWallMm < outsideDiameterMm)
        val effective = nominalWallMm * (1 - millTolerancePercent / 100)
        return NominalWallCheck(effective, required, effective >= result.minimumRequiredThicknessMm)
    }

    /**
     * User-supplied minimum remaining metal AFTER all machining, including groove/thread roots.
     * The removed metal is already absent from this measurement; do not deduct it or mill
     * tolerance again. This is a pressure-wall comparison, not inspection/code certification.
     */
    fun checkFinishedWall(result: PipeThicknessResult, outsideDiameterMm: Double, measuredMinimumMm: Double): FinishedWallCheck {
        require(outsideDiameterMm.isFinite() && outsideDiameterMm > 0)
        require(measuredMinimumMm.isFinite() && measuredMinimumMm > 0 && 2 * measuredMinimumMm < outsideDiameterMm)
        require(result.minimumFinishedWallMm.isFinite() && result.minimumFinishedWallMm >= 0)
        val margin = measuredMinimumMm - result.minimumFinishedWallMm
        return FinishedWallCheck(measuredMinimumMm, result.minimumFinishedWallMm, margin, margin >= 0)
    }

    private fun interpolateY(temperatureC: Double, points: List<Pair<Double, Double>>): Double {
        if (temperatureC <= points.first().first) return points.first().second
        if (temperatureC >= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (t1, y1) = points[i]
            val (t2, y2) = points[i + 1]
            if (temperatureC in t1..t2) {
                val fraction = (temperatureC - t1) / (t2 - t1)
                return y1 + fraction * (y2 - y1)
            }
        }
        return points.last().second
    }
}

class ThicknessScopeException : IllegalArgumentException("Special design consideration required by B31.3-2018 304.1.2(b).")
data class NominalWallCheck(val effectiveWallMm: Double, val requiredNominalWallMm: Double, val passes: Boolean)

data class FinishedWallCheck(val measuredMinimumMm: Double, val requiredMinimumMm: Double, val marginMm: Double, val passes: Boolean)
