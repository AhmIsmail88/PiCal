package com.ahmedismail.flowtrack.util

/**
 * ASME BPVC Section VIII Division 1 — internal pressure design of cylindrical
 * shells and formed heads: UG-27(c)(1)/(c)(2) for shells, UG-32(d)/(e)/(f)/(g)
 * for the head forms, and the UG-16(b) minimum formed-thickness rule.
 *
 * S (Section II, Part D at the design temperature) and E (Table UW-12 for the
 * actual joint type and examination) are supplied by the designer. This engine
 * never infers them, and it does not cover external pressure (UG-28), openings
 * and reinforcement (UG-36/UG-37/UG-40), bolted flanges (Appendix 2), supports,
 * fatigue, thermal discontinuity stress, or the Division 2 alternative rules.
 */
enum class VesselPart {
    /** Cylindrical shell with a longitudinal seam — circumferential stress governs (UG-27(c)(1)). */
    CYLINDRICAL_SHELL,

    /** 2:1 ellipsoidal head (UG-32(d), K = 1). */
    ELLIPSOIDAL_HEAD_2_1,

    /** Torispherical (flanged and dished) head (UG-32(e) / Appendix 1-4(d)). */
    TORISPHERICAL_HEAD,

    /** Hemispherical head (UG-32(f)). */
    HEMISPHERICAL_HEAD,

    /** Conical shell or head with half-apex angle α ≤ 30° (UG-32(g)). */
    CONICAL_HEAD
}

data class VesselInput(
    val designPressureBar: Double,
    /**
     * Inside radius at the point under consideration, in the CORRODED condition
     * (mm): the new inside radius plus any internal corrosion allowance. Used as
     * D/2 for the ellipsoidal and conical forms. The corrosion allowance is
     * added to the computed pressure thickness, never folded into this radius a
     * second time.
     */
    val insideRadiusMm: Double,
    /** Allowable stress S from Section II, Part D at the design temperature (MPa). */
    val allowableStressMPa: Double,
    /** Joint efficiency E from Table UW-12 for the governing joint (0 < E ≤ 1). */
    val jointEfficiency: Double,
    val corrosionAllowanceMm: Double,
    /** Inside crown radius L for a torispherical head (mm). Defaults to the inside diameter. */
    val crownRadiusMm: Double? = null,
    /** Inside knuckle radius r for a torispherical head (mm). Defaults to 6% of the crown radius. */
    val knuckleRadiusMm: Double? = null,
    /** Half-apex angle α of a conical part, in degrees. UG-32(g) covers α ≤ 30°. */
    val halfApexAngleDeg: Double = 30.0,
    /**
     * Inside diameter ÷ (2 × inside head depth) for an ellipsoidal head. 2.0 is the
     * standard 2:1 head, for which the factor K = 1. Deepening the head towards
     * hemispherical (1.0) lowers K towards 0.5, which reproduces UG-32(f) exactly.
     */
    val ellipsoidalRatio: Double = 2.0
)

data class VesselResult(
    /** Governing pressure-design thickness t, excluding the corrosion allowance. */
    val pressureThicknessMm: Double,
    /** UG-27(c)(1) circumferential-stress thickness — cylindrical shell only. */
    val circumferentialThicknessMm: Double?,
    /** UG-27(c)(2) longitudinal-stress thickness — cylindrical shell only. */
    val longitudinalThicknessMm: Double?,
    /** UG-16(b): shells and heads must be at least 1.5 mm thick after forming, before corrosion allowance. */
    val minimumFormedThicknessMm: Double,
    /** Pressure thickness raised to the UG-16(b) floor, plus the corrosion allowance. */
    val minimumOrderThicknessMm: Double
)

/** Raised when the entered case falls outside the rule set this engine implements. */
class VesselScopeException(message: String) : IllegalArgumentException(message)

object VesselThicknessCalculator {
    private const val BAR_TO_MPA = 0.1

    /** UG-16(b): 1.5 mm (0.0625 in.) minimum thickness of shells and heads after forming, exclusive of corrosion allowance. */
    const val MINIMUM_FORMED_THICKNESS_MM = 1.5

    /** UG-32(g) covers conical parts with a half-apex angle up to 30°. */
    const val MAX_CONICAL_HALF_APEX_DEG = 30.0

    /** UG-32(f): a hemispherical head is limited to t ≤ 0.356L or P ≤ 0.665SE. */
    private const val HEMISPHERICAL_THICKNESS_LIMIT_RATIO = 0.356
    private const val HEMISPHERICAL_PRESSURE_LIMIT_RATIO = 0.665

    fun calculate(part: VesselPart, input: VesselInput): VesselResult {
        validate(input)
        val p = input.designPressureBar * BAR_TO_MPA

        val se = input.allowableStressMPa * input.jointEfficiency
        val (circumferential, longitudinal) = if (part == VesselPart.CYLINDRICAL_SHELL) {
            cylindricalShell(p, input.insideRadiusMm, se)
        } else {
            null to null
        }

        val pressureThickness = when (part) {
            VesselPart.CYLINDRICAL_SHELL -> maxOf(circumferential!!, longitudinal!!)
            VesselPart.ELLIPSOIDAL_HEAD_2_1 -> ellipsoidalHead(p, input.insideRadiusMm * 2.0, ellipsoidalRatioFor(input), se)
            VesselPart.TORISPHERICAL_HEAD -> {
                val crown = input.crownRadiusMm ?: (input.insideRadiusMm * 2.0)
                val knuckle = input.knuckleRadiusMm ?: (crown * 0.06)
                torisphericalHead(p, crown, knuckle, se)
            }
            VesselPart.HEMISPHERICAL_HEAD -> hemisphericalHead(p, input.insideRadiusMm, se)
            VesselPart.CONICAL_HEAD -> conicalHead(p, input.insideRadiusMm * 2.0, input.halfApexAngleDeg, se)
        }
        require(pressureThickness.isFinite() && pressureThickness >= 0.0) { "Thickness exceeds the supported numeric range." }

        // UG-27(d) / UG-32: beyond t > R/2 the simple equations no longer apply and
        // Division 1 requires the thick-wall rules of Appendix 1-2. This engine does
        // not compute that branch, so it reports the scope exit instead of a number.
        if (pressureThickness > input.insideRadiusMm / 2.0) {
            throw VesselScopeException(
                "t exceeds half the inside radius: UG-27/UG-32 no longer apply and Appendix 1-2 (thick-wall) rules are required."
            )
        }

        return VesselResult(
            pressureThicknessMm = pressureThickness,
            circumferentialThicknessMm = circumferential,
            longitudinalThicknessMm = longitudinal,
            minimumFormedThicknessMm = MINIMUM_FORMED_THICKNESS_MM,
            minimumOrderThicknessMm = maxOf(pressureThickness, MINIMUM_FORMED_THICKNESS_MM) + input.corrosionAllowanceMm
        )
    }

    /**
     * Maximum allowable working pressure (bar, gauge) for a part of known nominal
     * thickness — the UG-27/UG-32 equations rearranged, using the wall remaining
     * after the corrosion allowance is consumed.
     */
    fun maximumAllowablePressureBar(part: VesselPart, input: VesselInput, nominalThicknessMm: Double): Double {
        validate(input)
        require(nominalThicknessMm.isFinite() && nominalThicknessMm > input.corrosionAllowanceMm) {
            "Nominal thickness must exceed the corrosion allowance."
        }
        val t = nominalThicknessMm - input.corrosionAllowanceMm
        val se = input.allowableStressMPa * input.jointEfficiency
        val r = input.insideRadiusMm
        if (t > r / 2.0) {
            throw VesselScopeException(
                "t exceeds half the inside radius: Appendix 1-2 (thick-wall) rules are required."
            )
        }
        val pMpa = when (part) {
            // t = PR / (SE − 0.6P)  →  P = SEt / (R + 0.6t)
            VesselPart.CYLINDRICAL_SHELL -> se * t / (r + 0.6 * t)
            // t = PDK / (2SE − 0.2P) →  P = 2SEt / (DK + 0.2t)
            VesselPart.ELLIPSOIDAL_HEAD_2_1 -> {
                val k = ellipsoidalFactorK(ellipsoidalRatioFor(input))
                2.0 * se * t / (2.0 * r * k + 0.2 * t)
            }
            // t = PL / (2SE − 0.2P) with L the inside radius →  P = 2SEt / (L + 0.2t)
            VesselPart.HEMISPHERICAL_HEAD -> {
                if (t > HEMISPHERICAL_THICKNESS_LIMIT_RATIO * r) {
                    throw VesselScopeException("t exceeds 0.356L: the hemispherical-head rule does not apply.")
                }
                val pressure = 2.0 * se * t / (r + 0.2 * t)
                if (pressure > HEMISPHERICAL_PRESSURE_LIMIT_RATIO * se) {
                    throw VesselScopeException("P exceeds 0.665SE: the hemispherical-head rule does not apply.")
                }
                pressure
            }
            // t = PLM / (2SE − 0.2P) →  P = 2SEt / (LM + 0.2t)
            VesselPart.TORISPHERICAL_HEAD -> {
                val crown = input.crownRadiusMm ?: (r * 2.0)
                val knuckle = input.knuckleRadiusMm ?: (crown * 0.06)
                val m = torisphericalFactorM(crown, knuckle)
                2.0 * se * t / (crown * m + 0.2 * t)
            }
            // t = PD / (2cosα (SE − 0.6P)) → P = 2SEt·cosα / (D + 1.2t·cosα)
            VesselPart.CONICAL_HEAD -> {
                requireHalfApex(input.halfApexAngleDeg)
                val cos = kotlin.math.cos(Math.toRadians(input.halfApexAngleDeg))
                2.0 * se * t * cos / (2.0 * r + 1.2 * t * cos)
            }
        }
        require(pMpa.isFinite() && pMpa >= 0.0) { "Pressure exceeds the supported numeric range." }
        return pMpa / BAR_TO_MPA
    }

    private fun validate(input: VesselInput) {
        require(listOf(input.designPressureBar, input.insideRadiusMm, input.allowableStressMPa,
            input.jointEfficiency, input.corrosionAllowanceMm).all { it.isFinite() }) { "All inputs must be finite." }
        require(input.designPressureBar >= 0.0 && input.insideRadiusMm > 0.0 && input.allowableStressMPa > 0.0) {
            "Pressure must be nonnegative; radius and allowable stress must be positive."
        }
        require(input.jointEfficiency > 0.0 && input.jointEfficiency <= 1.0) { "E must be in (0, 1]." }
        require(input.corrosionAllowanceMm >= 0.0) { "Corrosion allowance must be nonnegative." }
    }

    /** UG-27(c)(1) and (c)(2): circumferential governs a longitudinal seam, longitudinal governs a circumferential seam. */
    private fun cylindricalShell(pMpa: Double, insideRadiusMm: Double, se: Double): Pair<Double, Double> {
        val circumferentialDenominator = se - 0.6 * pMpa
        val longitudinalDenominator = 2.0 * se + 0.4 * pMpa
        require(circumferentialDenominator > 0.0 && longitudinalDenominator > 0.0) {
            "P is too high for the entered S and E."
        }
        return (pMpa * insideRadiusMm / circumferentialDenominator) to
            (pMpa * insideRadiusMm / longitudinalDenominator)
    }

    /**
     * UG-32(d): t = PDK / (2SE − 0.2P) with D the inside diameter and
     * K = (1/6)[2 + (D/2h)²]. K = 1 for a 2:1 head; at D/2h = 1 it gives 0.5,
     * which turns the equation into the hemispherical rule of UG-32(f).
     */
    private fun ellipsoidalHead(pMpa: Double, insideDiameterMm: Double, ratio: Double, se: Double): Double {
        val k = ellipsoidalFactorK(ratio)
        val denominator = 2.0 * se - 0.2 * pMpa
        require(denominator > 0.0) { "P is too high for the entered S and E." }
        return pMpa * insideDiameterMm * k / denominator
    }

    private fun ellipsoidalFactorK(ratio: Double): Double {
        require(ratio.isFinite() && ratio >= 1.0 && ratio <= 2.0) {
            "D/2h must be between 1.0 and 2.0."
        }
        return (2.0 + ratio * ratio) / 6.0
    }

    /** K covers heads from hemispherical (D/2h = 1) up to the standard 2:1; shallower heads need the code's tables. */
    private fun ellipsoidalRatioFor(input: VesselInput): Double {
        if (input.ellipsoidalRatio > 2.0) {
            throw VesselScopeException(
                "D/2h above 2.0 is shallower than the standard 2:1 head: verify K and the head geometry against the code."
            )
        }
        return input.ellipsoidalRatio
    }

    /** Appendix 1-4(d): M = 0.25(3 + √(L/r)); with r = 6% of L this is 1.7706, i.e. the 0.885 form of UG-32(e). */
    private fun torisphericalFactorM(crownRadiusMm: Double, knuckleRadiusMm: Double): Double {
        require(crownRadiusMm.isFinite() && crownRadiusMm > 0.0) { "Crown radius must be positive." }
        require(knuckleRadiusMm.isFinite() && knuckleRadiusMm > 0.0 && knuckleRadiusMm <= crownRadiusMm) {
            "Knuckle radius must be positive and no larger than the crown radius."
        }
        return 0.25 * (3.0 + kotlin.math.sqrt(crownRadiusMm / knuckleRadiusMm))
    }

    /** UG-32(e) / Appendix 1-4(d): t = PLM / (2SE − 0.2P). */
    private fun torisphericalHead(pMpa: Double, crownRadiusMm: Double, knuckleRadiusMm: Double, se: Double): Double {
        val m = torisphericalFactorM(crownRadiusMm, knuckleRadiusMm)
        val denominator = 2.0 * se - 0.2 * pMpa
        require(denominator > 0.0) { "P is too high for the entered S and E." }
        return pMpa * crownRadiusMm * m / denominator
    }

    /** UG-32(f): t = PL / (2SE − 0.2P) with L the inside radius, limited to t ≤ 0.356L / P ≤ 0.665SE. */
    private fun hemisphericalHead(pMpa: Double, insideRadiusMm: Double, se: Double): Double {
        require(insideRadiusMm.isFinite() && insideRadiusMm > 0.0) { "Spherical radius must be positive." }
        if (pMpa > HEMISPHERICAL_PRESSURE_LIMIT_RATIO * se) {
            throw VesselScopeException("P exceeds 0.665SE: the hemispherical-head rule of UG-32(f) does not apply.")
        }
        val denominator = 2.0 * se - 0.2 * pMpa
        require(denominator > 0.0) { "P is too high for the entered S and E." }
        val t = pMpa * insideRadiusMm / denominator
        if (t > HEMISPHERICAL_THICKNESS_LIMIT_RATIO * insideRadiusMm) {
            throw VesselScopeException("t exceeds 0.356L: the hemispherical-head rule of UG-32(f) does not apply.")
        }
        return t
    }

    /** UG-32(g): t = PD / (2cosα (SE − 0.6P)), D the inside diameter at the point considered, α ≤ 30°. */
    private fun conicalHead(pMpa: Double, insideDiameterMm: Double, halfApexAngleDeg: Double, se: Double): Double {
        requireHalfApex(halfApexAngleDeg)
        val cos = kotlin.math.cos(Math.toRadians(halfApexAngleDeg))
        val denominator = 2.0 * cos * (se - 0.6 * pMpa)
        require(denominator > 0.0) { "P is too high for the entered S and E." }
        return pMpa * insideDiameterMm / denominator
    }

    private fun requireHalfApex(halfApexAngleDeg: Double) {
        require(halfApexAngleDeg.isFinite() && halfApexAngleDeg > 0.0 && halfApexAngleDeg < 90.0) {
            "Half-apex angle must be between 0 and 90 degrees."
        }
        if (halfApexAngleDeg > MAX_CONICAL_HALF_APEX_DEG) {
            throw VesselScopeException("α exceeds 30°: UG-32(g) does not cover this cone; a reinforced cone design is required.")
        }
    }
}
