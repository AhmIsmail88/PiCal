package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.reference.B311MaterialGroup
import com.ahmedismail.flowtrack.data.reference.B311YTable

/**
 * ASME B31.1 (Power Piping) straight-pipe pressure design, §104.1.2.
 *
 *   t  = P·Do / [2·(S·E·W + P·y)]
 *   tm = t + A
 *
 * with Do the outside diameter, E the quality factor for the product form
 * (Table A-1 / §102.4.3), W the weld strength reduction factor (§102.4.7),
 * y from Table 104.1.2-1, and A the additional thickness for corrosion, erosion
 * and mechanical removal.
 *
 * Equation (7) confirmed against STRAIGHT PIPE.pdf, B31.1-2022 p.27.
 * It contains no independent x factor. S must be supplied before E/F;
 * do not multiply an already combined SE/SF table value by E again.
 *
 * Table 104.1.2-1 General Note (b) is implemented: for Do/tm < 6 the coefficient
 * becomes y = d/(d + Do) for ferritic and austenitic steels at 482 °C and below,
 * which makes the equation implicit in y — solved here by fixed-point iteration.
 */
data class B311PipeInput(
    val designPressureBar: Double,
    /** Outside diameter, Do (mm). */
    val outsideDiameterMm: Double,
    val allowableStressMPa: Double,
    /** E from the product form (Table A-1 "E or F" column / §102.4.3). */
    val qualityFactorE: Double,
    /** W per §102.4.7. */
    val weldStrengthReductionW: Double = 1.0,
    /** A: corrosion + erosion + mechanical allowances (mm). */
    val additionalThicknessAMm: Double,
    val materialGroup: B311MaterialGroup,
    val designTemperatureC: Double,
    /** Manual override of Table 104.1.2-1, when the designer must use another value. */
    val yOverride: Double? = null
)

data class B311PipeResult(
    val yCoefficient: Double,
    /** True when General Note (b) governed, i.e. Do/tm < 6 with the thin-wall coefficient. */
    val thinWallRuleApplied: Boolean,
    /** t — pressure design thickness. */
    val pressureThicknessMm: Double,
    /** tm = t + A — minimum required thickness. */
    val minimumRequiredThicknessMm: Double
) {
    val status = CalculationStatus.CALCULATED
}

class B311PipeScopeException(message: String) : IllegalArgumentException(message) {
    val status = CalculationStatus.OUT_OF_CODE_LIMITS
}

object B311PipeThicknessCalculator {
    private const val BAR_TO_MPA = 0.1
    private const val MAX_ITERATIONS = 100

    fun calculate(input: B311PipeInput): B311PipeResult {
        require(listOf(input.designPressureBar, input.outsideDiameterMm, input.allowableStressMPa,
            input.qualityFactorE, input.weldStrengthReductionW,
            input.additionalThicknessAMm, input.designTemperatureC).all { it.isFinite() }) { "All inputs must be finite." }
        require(input.designPressureBar >= 0.0 && input.outsideDiameterMm > 0.0 && input.allowableStressMPa > 0.0) {
            "Pressure must be nonnegative; diameter and allowable stress must be positive."
        }
        require(input.qualityFactorE > 0.0 && input.qualityFactorE <= 1.0) { "E must be in (0, 1]." }
        require(input.weldStrengthReductionW > 0.0 && input.weldStrengthReductionW <= 1.0) { "W must be in (0, 1]." }
        require(input.additionalThicknessAMm >= 0.0) { "The additional thickness A must be nonnegative." }
        require(input.designTemperatureC > -273.15) { "Temperature must be above absolute zero." }
        input.yOverride?.let { require(it >= 0.0 && it <= 1.0) { "y must be in [0, 1]." } }

        val tableY = input.yOverride ?: B311YTable.coefficient(input.materialGroup, input.designTemperatureC)
        val mayUseThinWallRule = input.yOverride == null &&
            B311YTable.thinWallRuleApplies(input.materialGroup, input.designTemperatureC)

        var y = tableY
        var tm = thickness(input, y) + input.additionalThicknessAMm
        requirePossibleWall(input, tm)
        var thinWallApplied = false
        if (mayUseThinWallRule && input.outsideDiameterMm / tm < 6.0) {
            // Note (b) makes the equation implicit in y: iterate until y settles.
            var converged = false
            for (iteration in 0 until MAX_ITERATIONS) {
                val insideDiameter = input.outsideDiameterMm - 2.0 * tm
                val candidate = B311YTable.thinWallCoefficient(insideDiameter, input.outsideDiameterMm)
                val candidateTm = thickness(input, candidate) + input.additionalThicknessAMm
                requirePossibleWall(input, candidateTm)
                converged = kotlin.math.abs(candidateTm - tm) <= 1e-12 * maxOf(1.0, tm)
                y = candidate
                tm = candidateTm
                thinWallApplied = true
                if (converged) break
            }
            if (!converged) throw B311PipeScopeException("The implicit y calculation did not converge; no thickness result is available.")
            // Re-check with the settled thickness: a thin pressure wall can push the
            // ratio back above 6, in which case the table value governs.
            if (input.outsideDiameterMm / tm >= 6.0) {
                y = tableY
                tm = thickness(input, tableY) + input.additionalThicknessAMm
                thinWallApplied = false
            }
        }

        val t = tm - input.additionalThicknessAMm
        require(t.isFinite() && tm.isFinite() && t >= 0.0) { "Thickness exceeds the supported numeric range." }
        return B311PipeResult(
            yCoefficient = y,
            thinWallRuleApplied = thinWallApplied,
            pressureThicknessMm = t,
            minimumRequiredThicknessMm = tm
        )
    }

    private fun requirePossibleWall(input: B311PipeInput, thicknessMm: Double) {
        if (!thicknessMm.isFinite() || thicknessMm >= input.outsideDiameterMm / 2.0) {
            throw B311PipeScopeException("The required wall leaves no positive inside diameter.")
        }
    }

    private fun thickness(input: B311PipeInput, y: Double): Double {
        val pMpa = input.designPressureBar * BAR_TO_MPA
        val denominator = 2.0 * (input.allowableStressMPa * input.qualityFactorE *
            input.weldStrengthReductionW + pMpa * y)
        require(denominator.isFinite() && denominator > 0.0) { "Invalid pressure-design denominator." }
        return pMpa * input.outsideDiameterMm / denominator
    }
}
