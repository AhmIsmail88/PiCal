package com.ahmedismail.flowtrack.util

/**
 * ASME B31.3-2018 §304.2.1 — pressure design thickness of a pipe bend (elbow).
 *
 * Bending thins the wall and raises the stress at the intrados, so the code
 * applies a factor I at each point of the bend:
 *
 *   t = P·D / [2·((S·E·W / I) + P·Y)]
 *   I_intrados = [4(R1/D) − 1] / [4(R1/D) − 2]
 *   I_extrados = [4(R1/D) + 1] / [4(R1/D) + 2]
 *
 * where R1 is the bend radius to the pipe centreline. I_intrados is always
 * greater than 1 (it governs), I_extrados is always less than 1, and both
 * approach 1 as R1/D grows, converging on the straight-pipe thickness of
 * Eq. (3a). There is no code equation for the geometric wall thinning itself —
 * the code requires the fabricated bend to satisfy t at every point instead.
 *
 * S, E and W are supplied by the designer; this engine does not infer them, and
 * miter bends (§304.2.3) are outside its scope.
 */
data class BendInput(
    val designPressureBar: Double,
    val outsideDiameterMm: Double,
    /** Bend radius measured to the pipe centreline, R1 (mm). Use the manufacturer drawing; standard long-radius designation refers to nominal size, not actual OD. */
    val bendRadiusMm: Double,
    val allowableStressMPa: Double,
    val qualityFactorE: Double,
    val weldStrengthReductionW: Double,
    val yCoefficient: Double,
    val corrosionAllowanceMm: Double,
    val erosionAllowanceMm: Double = 0.0,
    val mechanicalAllowanceMm: Double = 0.0
)

data class BendResult(
    val intradosFactorI: Double,
    val extradosFactorI: Double,
    /** Required pressure thickness at the intrados — this governs the bend. */
    val intradosThicknessMm: Double,
    /** Required pressure thickness at the extrados (shown for reference only). */
    val extradosThicknessMm: Double,
    /** The same equation with I = 1: the matching straight pipe. */
    val straightThicknessMm: Double,
    /** Intrados thickness plus corrosion, erosion and mechanical allowances — the bend before fabrication. */
    val intradosMinimumRequiredThicknessMm: Double,
    /** Straight-pipe equivalent of the same figure, for comparison with the plain pipe purchase. */
    val straightMinimumRequiredThicknessMm: Double,
    /** Remaining metal required at the thinnest intrados point of the finished bend. */
    val intradosMinimumFinishedWallMm: Double
)

object BendThicknessCalculator {
    private const val BAR_TO_MPA = 0.1

    fun calculate(input: BendInput): BendResult {
        require(listOf(input.designPressureBar, input.outsideDiameterMm, input.bendRadiusMm, input.allowableStressMPa,
            input.qualityFactorE, input.weldStrengthReductionW, input.yCoefficient, input.corrosionAllowanceMm,
            input.erosionAllowanceMm, input.mechanicalAllowanceMm).all { it.isFinite() }) { "All inputs must be finite." }
        require(input.designPressureBar >= 0.0 && input.outsideDiameterMm > 0.0 && input.allowableStressMPa > 0.0) {
            "Pressure must be nonnegative; diameter and allowable stress must be positive."
        }
        require(input.qualityFactorE > 0.0 && input.qualityFactorE <= 1.0 &&
            input.weldStrengthReductionW > 0.0 && input.weldStrengthReductionW <= 1.0) { "E and W must be in (0, 1]." }
        require(input.yCoefficient >= 0.0 && input.yCoefficient <= 1.0 && input.corrosionAllowanceMm >= 0.0 &&
            input.erosionAllowanceMm >= 0.0 && input.mechanicalAllowanceMm >= 0.0) {
            "Y must be in [0, 1]; allowances must be nonnegative."
        }
        // I_intrados has the denominator 4(R1/D) − 2, so the bend radius must exceed half the outside diameter.
        require(input.bendRadiusMm > input.outsideDiameterMm / 2.0) {
            "Bend radius must exceed half the outside diameter."
        }

        val ratio = input.bendRadiusMm / input.outsideDiameterMm
        val intradosI = (4.0 * ratio - 1.0) / (4.0 * ratio - 2.0)
        val extradosI = (4.0 * ratio + 1.0) / (4.0 * ratio + 2.0)
        val pMpa = input.designPressureBar * BAR_TO_MPA
        val sew = input.allowableStressMPa * input.qualityFactorE * input.weldStrengthReductionW

        val intrados = thickness(pMpa, input.outsideDiameterMm, sew, input.yCoefficient, intradosI)
        val extrados = thickness(pMpa, input.outsideDiameterMm, sew, input.yCoefficient, extradosI)
        val straight = thickness(pMpa, input.outsideDiameterMm, sew, input.yCoefficient, 1.0)

        // Same scope limits as Eq. (3a): the thin-wall shell equations these factors
        // modify are valid only for t < D/6 and P/(S·E) ≤ 0.385.
        if (intrados >= input.outsideDiameterMm / 6.0 ||
            pMpa / (input.allowableStressMPa * input.qualityFactorE) > 0.385) {
            throw ThicknessScopeException()
        }
        val allowances = input.corrosionAllowanceMm + input.erosionAllowanceMm
        return BendResult(
            intradosFactorI = intradosI,
            extradosFactorI = extradosI,
            intradosThicknessMm = intrados,
            extradosThicknessMm = extrados,
            straightThicknessMm = straight,
            intradosMinimumRequiredThicknessMm = intrados + allowances + input.mechanicalAllowanceMm,
            straightMinimumRequiredThicknessMm = straight + allowances + input.mechanicalAllowanceMm,
            intradosMinimumFinishedWallMm = intrados + allowances
        )
    }

    /**
     * Compares a user-supplied minimum measured intrados wall of the finished
     * bend against the required remaining metal. As with the straight-pipe
     * check, the removed metal is already absent from the measurement, so no
     * machining allowance is deducted again.
     */
    fun checkIntradosWall(result: BendResult, outsideDiameterMm: Double, measuredMinimumMm: Double): FinishedWallCheck {
        require(outsideDiameterMm.isFinite() && outsideDiameterMm > 0.0)
        require(measuredMinimumMm.isFinite() && measuredMinimumMm > 0.0 && 2 * measuredMinimumMm < outsideDiameterMm)
        require(result.intradosMinimumFinishedWallMm.isFinite() && result.intradosMinimumFinishedWallMm >= 0.0)
        val margin = measuredMinimumMm - result.intradosMinimumFinishedWallMm
        return FinishedWallCheck(measuredMinimumMm, result.intradosMinimumFinishedWallMm, margin, margin >= 0.0)
    }

    private fun thickness(pMpa: Double, outsideDiameterMm: Double, sew: Double, y: Double, factorI: Double): Double {
        val denominator = 2.0 * ((sew / factorI) + pMpa * y)
        require(denominator.isFinite() && denominator > 0.0) { "Invalid pressure-design denominator." }
        val t = pMpa * outsideDiameterMm / denominator
        require(t.isFinite()) { "Thickness exceeds the supported numeric range." }
        return t
    }
}
