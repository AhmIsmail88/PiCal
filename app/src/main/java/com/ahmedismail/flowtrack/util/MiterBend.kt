package com.ahmedismail.flowtrack.util

import kotlin.math.tan

/** Exact conversion constant: 1 psi = 6894.757293168 Pa. */
private const val PSI_TO_BAR = 0.06894757293168

/**
 * B31.3-2018 miter geometry. Geometry only: it never
 * decides code compliance.
 *
 * θ is the miter cut angle — one half of the deflection at a single joint, in the
 * UI, the report and here:
 *
 *   deflection per joint = total bend angle ÷ number of joints
 *   θ = deflection per joint ÷ 2
 *
 * so 90° over one joint is θ = 45°, and 90° over two joints is θ = 22.5°.
 */
data class MiterGeometry(
    val totalBendAngleDeg: Double,
    val jointCount: Int,
    val outsideDiameterMm: Double,
    val thicknessMm: Double,
    /** R1 established from the actual geometry using Fig. 304.2.3. */
    val effectiveRadiusMm: Double
) {
    val deflectionPerJointDeg: Double get() = totalBendAngleDeg / jointCount
    val thetaDeg: Double get() = deflectionPerJointDeg / 2.0
    val thetaRad: Double get() = Math.toRadians(thetaDeg)
    /** Mean radius of the pipe cross-section using T, r2 = (D − T) / 2. */
    val meanRadiusMm: Double get() = (outsideDiameterMm - thicknessMm) / 2.0

    init {
        require(totalBendAngleDeg.isFinite() && totalBendAngleDeg > 0.0 && totalBendAngleDeg < 180.0) {
            "Total bend angle must be between 0 and 180 degrees."
        }
        require(jointCount >= 1) { "At least one miter joint is required." }
        require(outsideDiameterMm.isFinite() && thicknessMm.isFinite() &&
            outsideDiameterMm > 0.0 && thicknessMm > 0.0 && 2.0 * thicknessMm < outsideDiameterMm) {
            "Wall thickness must be positive and less than half the outside diameter."
        }
        require(effectiveRadiusMm.isFinite() && effectiveRadiusMm > 0.0) { "R1 must be finite and positive." }
        require(thetaDeg > 0.0 && thetaDeg < 90.0) { "The miter cut angle θ must be between 0 and 90 degrees." }
    }
}

data class B313MiterInput(
    val designPressureBar: Double,
    val allowableStressMPa: Double,
    val qualityFactorE: Double,
    val weldStrengthReductionW: Double,
    /** c — corrosion + erosion + mechanical allowances (mm). */
    val allowancesMm: Double,
    val geometry: MiterGeometry
)

/**
 * ASME B31.3-2018 §304.2.3. These equations give the MAXIMUM ALLOWABLE INTERNAL
 * PRESSURE Pm for a geometry and thickness — they are not thickness equations.
 * A required thickness can only be reported as "the minimum T that satisfies
 * Pm ≥ P", obtained by iteration.
 */
enum class MiterEquation { MULTIPLE_4A_4B, SINGLE_4A, SINGLE_4C }

class MiterScopeException(message: String) : IllegalArgumentException(message) {
    val status = CalculationStatus.OUT_OF_CODE_LIMITS
}

data class B313MiterResult(
    val equationId: String,
    val equation: MiterEquation,
    val multipleMiter: Boolean,
    val minimumEffectiveRadiusMm: Double,
    val minimumWallExtensionMm: Double,
    val pmEqA: Double?,
    val pmEqB: Double?,
    /** Governing Pm in bar(g). */
    val maxAllowablePressureBar: Double,
    val designPressureBar: Double,
    val marginBar: Double,
    val passes: Boolean
) {
    val status: CalculationStatus
        get() = if (passes) CalculationStatus.ACCEPTABLE else CalculationStatus.NOT_ACCEPTABLE
}

object B313MiterCalculator {

    fun calculate(input: B313MiterInput): B313MiterResult {
        validate(input)
        val geometry = input.geometry
        if (geometry.deflectionPerJointDeg <= 3.0) {
            throw MiterScopeException("An offset of 3 degrees or less does not require miter design under 304.2.3; use the applicable straight-pipe and joint rules.")
        }
        val t = geometry.thicknessMm - input.allowancesMm
        require(t > 0.0) { "The allowances c consume the whole wall thickness." }
        val r2 = geometry.meanRadiusMm
        val sew = input.allowableStressMPa * input.qualityFactorE * input.weldStrengthReductionW
        val base = sew * t / r2
        val tanTheta = tan(geometry.thetaRad)
        val root = kotlin.math.sqrt(r2 * t)

        val pmMultipleA = base * (t / (t + 0.643 * tanTheta * root))
        val pmSingleAbove = base * (t / (t + 1.25 * tanTheta * root))

        val multiple = geometry.jointCount > 1
        val r1 = geometry.effectiveRadiusMm
        val minimumRadius = minimumEffectiveRadiusMm(geometry.outsideDiameterMm, t, geometry.thetaDeg)
        if (r1 < minimumRadius) throw MiterScopeException("R1 is below the minimum of B31.3-2018 equation (5).")
        val (equationId, pmMpa, pmB) = when {
            multiple && geometry.thetaDeg <= 22.5 -> {
                if (!r1.isFinite() || r1 <= r2) {
                    throw MiterScopeException("The close-miter geometry requires R1 > r2; no pressure verdict is available.")
                }
                val pmBValue = base * ((r1 - r2) / (r1 - 0.5 * r2))
                Triple("Eq. 4a & 4b (multiple miter)", minOf(pmMultipleA, pmBValue), pmBValue)
            }
            multiple -> throw MiterScopeException("Equations 4a and 4b do not apply to multiple miters with θ > 22.5°.")
            geometry.thetaDeg <= 22.5 -> Triple("Eq. 4a (single miter, θ ≤ 22.5°)", pmMultipleA, null)
            else -> Triple("Eq. 4c (single miter, θ > 22.5°)", pmSingleAbove, null)
        }
        require(pmMpa.isFinite() && pmMpa > 0.0) { "Pm is outside the supported numeric range." }
        val pmBar = pmMpa / 0.1
        return B313MiterResult(
            equationId = equationId,
            equation = when {
                multiple -> MiterEquation.MULTIPLE_4A_4B
                geometry.thetaDeg <= 22.5 -> MiterEquation.SINGLE_4A
                else -> MiterEquation.SINGLE_4C
            },
            multipleMiter = multiple,
            minimumEffectiveRadiusMm = minimumRadius,
            minimumWallExtensionMm = maxOf(2.5 * kotlin.math.sqrt(r2 * geometry.thicknessMm), tanTheta * (r1 - r2)),
            pmEqA = if (multiple || geometry.thetaDeg <= 22.5) pmMultipleA / 0.1 else null,
            pmEqB = pmB?.let { it / 0.1 },
            maxAllowablePressureBar = pmBar,
            designPressureBar = input.designPressureBar,
            marginBar = pmBar - input.designPressureBar,
            passes = input.designPressureBar <= pmBar
        )
    }

    /** B31.3-2018 Eq. (5), SI empirical A values based on net wall T-c. */
    fun minimumEffectiveRadiusMm(diameterMm: Double, netWallMm: Double, thetaDeg: Double): Double {
        require(listOf(diameterMm, netWallMm, thetaDeg).all { it.isFinite() })
        require(diameterMm > 0.0 && netWallMm > 0.0 && thetaDeg > 0.0 && thetaDeg < 90.0)
        val a = when {
            netWallMm <= 13.0 -> 25.0
            netWallMm < 22.0 -> 2.0 * netWallMm
            else -> 2.0 * netWallMm / 3.0 + 30.0
        }
        return a / tan(Math.toRadians(thetaDeg)) + diameterMm / 2.0
    }

    /**
     * Minimum wall thickness T that satisfies Pm ≥ P, by bisection. This is NOT a
     * closed-form code result: the code gives Pm, so the answer is reported as the
     * smallest T meeting the pressure requirement.
     */
    fun requiredThicknessMm(input: B313MiterInput, designPressureBar: Double = input.designPressureBar): Double? {
        // An unsupported current geometry must never acquire a thickness verdict
        // just because another trial thickness changes the spacing classification.
        calculate(input.copy(designPressureBar = designPressureBar))
        val diameter = input.geometry.outsideDiameterMm
        var low = input.allowancesMm + 1e-6
        var high = diameter / 2.0 - 1e-6
        // Eq. (5) also limits the largest admissible trial thickness. Find that
        // geometry bound before searching pressure, so an invalid high endpoint
        // does not conceal a valid smaller solution.
        if (input.geometry.effectiveRadiusMm < minimumEffectiveRadiusMm(diameter, high - input.allowancesMm, input.geometry.thetaDeg)) {
            var valid = input.geometry.thicknessMm
            var invalid = high
            repeat(80) {
                val trial = (valid + invalid) / 2.0
                if (input.geometry.effectiveRadiusMm >= minimumEffectiveRadiusMm(diameter, trial - input.allowancesMm, input.geometry.thetaDeg)) valid = trial else invalid = trial
            }
            high = valid
        }
        val atHigh = runCatching {
            calculate(input.copy(geometry = input.geometry.copy(thicknessMm = high), designPressureBar = designPressureBar))
        }.getOrNull() ?: return null
        if (atHigh.maxAllowablePressureBar < designPressureBar) return null
        repeat(80) {
            val mid = (low + high) / 2.0
            val pm = runCatching {
                calculate(input.copy(geometry = input.geometry.copy(thicknessMm = mid), designPressureBar = designPressureBar))
            }.getOrNull()?.maxAllowablePressureBar ?: Double.NaN
            if (pm.isFinite() && pm >= designPressureBar) high = mid else low = mid
        }
        return high
    }

    private fun validate(input: B313MiterInput) {
        require(listOf(input.designPressureBar, input.allowableStressMPa, input.qualityFactorE,
            input.weldStrengthReductionW, input.allowancesMm).all { it.isFinite() }) { "All inputs must be finite." }
        require(input.designPressureBar > 0.0 && input.allowableStressMPa > 0.0) {
            "Design pressure and allowable stress must be positive."
        }
        require(input.qualityFactorE > 0.0 && input.qualityFactorE <= 1.0) { "E must be in (0, 1]." }
        require(input.weldStrengthReductionW > 0.0 && input.weldStrengthReductionW <= 1.0) { "W must be in (0, 1]." }
        require(input.allowancesMm >= 0.0) { "Allowances must be nonnegative." }
    }
}

data class B311MiterConditions(
    /** B — the crotch dimension of the code figure (mm). */
    val crotchDimensionBMm: Double,
    /** tn — nominal thickness of the miter segments (mm). */
    val nominalThicknessMm: Double,
    /** (a)(2): every segment thickness is not less than that required by para. 104.1. */
    val segmentsMeetPara1041: Boolean,
    /** (a)(3): nonflammable, nontoxic and incompressible fluid (gaseous vents excepted). */
    val fluidNonflammableNonToxicIncompressible: Boolean,
    /** (a)(4): fewer than 7,000 full pressure cycles in the expected lifetime. */
    val fullPressureCyclesUnder7000: Boolean,
    /** (a)(5): full penetration welds join the miter segments. */
    val fullPenetrationWelds: Boolean
)

data class MiterConditionCheck(val id: String, val description: String, val satisfied: Boolean)

/**
 * ASME B31.1-2022 §104.2.3 is a decision tree, not a closed-form formula: the
 * simplified paths cap the pressure at 10 psi or 100 psi, and anything else must be
 * qualified under para. 104.7. Every condition is reported, never just a verdict.
 *
 * The segment-thickness rules of §104.2.3(c)(3) (closely and widely spaced) are NOT
 * computed here: their text could not be transcribed reliably from the supplied
 * excerpt, so the result is flagged instead of guessed.
 */
enum class B311MiterPath { TEN_PSI, HUNDRED_PSI, QUALIFICATION }

data class B311MiterResult(
    val pathId: String,
    val path: B311MiterPath,
    val pressureLimitBar: Double?,
    val passes: Boolean?,
    val qualificationRequired: Boolean,
    val conditions: List<MiterConditionCheck>
) {
    val status: CalculationStatus
        get() = when {
            qualificationRequired -> CalculationStatus.QUALIFICATION_REQUIRED
            passes == true -> CalculationStatus.ACCEPTABLE
            else -> CalculationStatus.NOT_ACCEPTABLE
        }
}

object B311MiterCalculator {
    const val TEN_PSI_BAR = 10.0 * PSI_TO_BAR
    const val HUNDRED_PSI_BAR = 100.0 * PSI_TO_BAR

    fun calculate(designPressureBar: Double, thetaDeg: Double, conditions: B311MiterConditions): B311MiterResult {
        require(designPressureBar > 0.0 && designPressureBar.isFinite()) { "Design pressure must be positive." }
        require(thetaDeg > 0.0 && thetaDeg < 90.0) { "The miter cut angle θ must be between 0 and 90 degrees." }
        require(conditions.crotchDimensionBMm.isFinite() && conditions.nominalThicknessMm.isFinite() &&
            conditions.crotchDimensionBMm >= 0.0 && conditions.nominalThicknessMm > 0.0) {
            "The crotch dimension and the nominal thickness must be positive."
        }

        val bUnderSixTn = conditions.crotchDimensionBMm < 6.0 * conditions.nominalThicknessMm
        val steepOrShort = thetaDeg > 22.5 || bUnderSixTn

        val checks = listOf(
            if (steepOrShort) MiterConditionCheck("(a)(1)", "θ > 22.5° or a segment with B < 6tn", true)
            else MiterConditionCheck("(b)(1)-(2)", "θ ≤ 22.5° and every segment has B ≥ 6tn", true),
            MiterConditionCheck("(a)(2)", "each segment thickness ≥ that required by para. 104.1", conditions.segmentsMeetPara1041),
            MiterConditionCheck("(a)(3)", "fluid nonflammable, nontoxic and incompressible", conditions.fluidNonflammableNonToxicIncompressible),
            MiterConditionCheck("(a)(4)", "fewer than 7,000 full pressure cycles", conditions.fullPressureCyclesUnder7000),
            MiterConditionCheck("(a)(5)", "full penetration welds", conditions.fullPenetrationWelds)
        )
        val commonSatisfied = conditions.segmentsMeetPara1041 &&
            conditions.fluidNonflammableNonToxicIncompressible &&
            conditions.fullPressureCyclesUnder7000 && conditions.fullPenetrationWelds

        return when {
            commonSatisfied && steepOrShort && designPressureBar <= TEN_PSI_BAR -> B311MiterResult(
                pathId = "§104.2.3(a) — 10 psi path",
                path = B311MiterPath.TEN_PSI,
                pressureLimitBar = TEN_PSI_BAR,
                passes = designPressureBar <= TEN_PSI_BAR,
                qualificationRequired = false,
                conditions = checks
            )
            commonSatisfied && !steepOrShort && designPressureBar <= HUNDRED_PSI_BAR -> B311MiterResult(
                pathId = "§104.2.3(b) — 100 psi path",
                path = B311MiterPath.HUNDRED_PSI,
                pressureLimitBar = HUNDRED_PSI_BAR,
                passes = designPressureBar <= HUNDRED_PSI_BAR,
                qualificationRequired = false,
                conditions = checks
            )
            else -> B311MiterResult(
                pathId = "§104.2.3(c) — qualification per §104.7 required",
                path = B311MiterPath.QUALIFICATION,
                pressureLimitBar = null,
                passes = null,
                qualificationRequired = true,
                conditions = checks
            )
        }
    }
}
