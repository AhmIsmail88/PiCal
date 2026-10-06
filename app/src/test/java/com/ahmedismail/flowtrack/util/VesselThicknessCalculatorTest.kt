package com.ahmedismail.flowtrack.util

import org.junit.Assert.*
import org.junit.Test

/**
 * ASME VIII Div. 1 internal-pressure checks. Every expected value here was
 * recomputed independently (not copied from the implementation) — see
 * CALC_UI_REVIEW_AR.md.
 */
class VesselThicknessCalculatorTest {
    private val shell = VesselInput(
        designPressureBar = 10.0, insideRadiusMm = 500.0, allowableStressMPa = 138.0,
        jointEfficiency = 1.0, corrosionAllowanceMm = 3.0
    )

    @Test fun cylindricalShellUsesCircumferentialStressAndAddsCorrosionAllowance() {
        val result = VesselThicknessCalculator.calculate(VesselPart.CYLINDRICAL_SHELL, shell)
        assertEquals(3.6390101892285296, result.circumferentialThicknessMm!!, 1e-12)
        assertEquals(1.8089725036179451, result.longitudinalThicknessMm!!, 1e-12)
        assertEquals(3.6390101892285296, result.pressureThicknessMm, 1e-12)
        assertEquals(6.63901018922853, result.minimumOrderThicknessMm, 1e-12)
    }

    /** UG-32(d)/(e)/(f)/(g): the four formed-head forms, with L defaulting to the inside diameter. */
    @Test fun formedHeadFormsMatchUg32() {
        assertEquals(3.6258158085569252,
            VesselThicknessCalculator.calculate(VesselPart.ELLIPSOIDAL_HEAD_2_1, shell).pressureThicknessMm, 1e-12)
        assertEquals(1.8129079042784626,
            VesselThicknessCalculator.calculate(VesselPart.HEMISPHERICAL_HEAD, shell).pressureThicknessMm, 1e-12)
        // Appendix 1-4(d) with the default 6% knuckle radius: M = 0.25(3 + √(1000/60)) = 1.7706207261596576.
        val torispherical = VesselThicknessCalculator.calculate(VesselPart.TORISPHERICAL_HEAD, shell).pressureThicknessMm
        assertEquals(6.4199446198682288, torispherical, 1e-12)
        // The M form reproduces the 0.885 form the code quotes for a standard 6% head
        // (t = 0.885PL / (SE − 0.1P)) to within a fraction of a percent.
        val standardSixPercentForm = 0.885 * 1.0 * 1000.0 / (138.0 - 0.1 * 1.0)
        assertEquals(standardSixPercentForm, torispherical, standardSixPercentForm * 0.001)
        assertEquals(4.2019670246697647,
            VesselThicknessCalculator.calculate(VesselPart.CONICAL_HEAD, shell).pressureThicknessMm, 1e-12)
    }

    /**
     * UG-32(d) with K = (1/6)[2 + (D/2h)²]: K = 1 for the standard 2:1 head, and at
     * D/2h = 1 (K = 0.5) the ellipsoidal equation must reproduce UG-32(f) exactly.
     */
    @Test fun ellipsoidalKFactorReproducesTheHemisphericalRuleAtOneToOne() {
        val twoToOne = VesselThicknessCalculator.calculate(VesselPart.ELLIPSOIDAL_HEAD_2_1, shell)
        assertEquals(3.6258158085569252, twoToOne.pressureThicknessMm, 1e-12)

        val deep = VesselThicknessCalculator.calculate(
            VesselPart.ELLIPSOIDAL_HEAD_2_1, shell.copy(ellipsoidalRatio = 1.0)
        )
        val hemispherical = VesselThicknessCalculator.calculate(VesselPart.HEMISPHERICAL_HEAD, shell)
        assertEquals(hemispherical.pressureThicknessMm, deep.pressureThicknessMm, 1e-12)

        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.calculate(VesselPart.ELLIPSOIDAL_HEAD_2_1, shell.copy(ellipsoidalRatio = 2.5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            VesselThicknessCalculator.calculate(VesselPart.ELLIPSOIDAL_HEAD_2_1, shell.copy(ellipsoidalRatio = 0.8))
        }
    }

    @Test fun thinPartsAreRaisedToTheUg16MinimumFormedThickness() {
        val thin = shell.copy(designPressureBar = 1.0, corrosionAllowanceMm = 0.0)
        val result = VesselThicknessCalculator.calculate(VesselPart.CYLINDRICAL_SHELL, thin)
        assertTrue(result.pressureThicknessMm < VesselThicknessCalculator.MINIMUM_FORMED_THICKNESS_MM)
        assertEquals(1.5, result.minimumOrderThicknessMm, 1e-12)
    }

    /** UG-27(d): beyond t > R/2 the simple equations no longer apply. */
    @Test fun thicknessBeyondHalfTheInsideRadiusExitsTheImplementedScope() {
        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.calculate(
                VesselPart.CYLINDRICAL_SHELL, VesselInput(200.0, 100.0, 30.0, 1.0, 0.0)
            )
        }
    }

    /** UG-32(f) limits a hemispherical head to P ≤ 0.665SE, and UG-32(g) a cone to α ≤ 30°. */
    @Test fun headRuleLimitsAreEnforced() {
        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.calculate(VesselPart.HEMISPHERICAL_HEAD, shell.copy(designPressureBar = 1000.0))
        }
        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.calculate(VesselPart.CONICAL_HEAD, shell.copy(halfApexAngleDeg = 45.0))
        }
    }

    /** The MAWP rearrangements must invert each design equation exactly. */
    @Test fun mawpInvertsEveryDesignEquation() {
        VesselPart.values().forEach { part ->
            val result = VesselThicknessCalculator.calculate(part, shell)
            assertEquals(part.name, 10.0,
                VesselThicknessCalculator.maximumAllowablePressureBar(part, shell, result.minimumOrderThicknessMm), 1e-9)
        }
    }

    @Test fun invalidInputsAreRejected() {
        listOf(
            shell.copy(jointEfficiency = 0.0), shell.copy(jointEfficiency = 1.01),
            shell.copy(insideRadiusMm = 0.0), shell.copy(allowableStressMPa = -1.0),
            shell.copy(corrosionAllowanceMm = -0.5), shell.copy(designPressureBar = -1.0)
        ).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                VesselThicknessCalculator.calculate(VesselPart.CYLINDRICAL_SHELL, bad)
            }
        }
        listOf(0.0, 90.0, -5.0).forEach { angle ->
            assertThrows(IllegalArgumentException::class.java) {
                VesselThicknessCalculator.calculate(VesselPart.CONICAL_HEAD, shell.copy(halfApexAngleDeg = angle))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            // A knuckle radius larger than the crown radius is not a torispherical head.
            VesselThicknessCalculator.calculate(
                VesselPart.TORISPHERICAL_HEAD, shell.copy(crownRadiusMm = 1000.0, knuckleRadiusMm = 1200.0)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            // Design pressure far beyond what the entered S and E can carry.
            VesselThicknessCalculator.calculate(VesselPart.CYLINDRICAL_SHELL, VesselInput(3000.0, 500.0, 10.0, 1.0, 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            // Nominal thickness at or below the corrosion allowance leaves no pressure wall.
            VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.CYLINDRICAL_SHELL, shell, 3.0)
        }
    }

    @Test fun mawpRejectsInvalidBaseInputsJustLikeTheThicknessCalculation() {
        listOf(
            shell.copy(jointEfficiency = 2.0), shell.copy(jointEfficiency = 0.0),
            shell.copy(insideRadiusMm = -1.0), shell.copy(allowableStressMPa = -1.0),
            shell.copy(corrosionAllowanceMm = -1.0), shell.copy(designPressureBar = -1.0),
            shell.copy(allowableStressMPa = Double.NaN), shell.copy(insideRadiusMm = Double.POSITIVE_INFINITY)
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) {
                VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.CYLINDRICAL_SHELL, invalid, 5.0)
            }
        }
    }

    @Test fun hemisphericalMawpCannotEscapeTheDesignRuleLimits() {
        val input = shell.copy(corrosionAllowanceMm = 0.0)
        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.HEMISPHERICAL_HEAD, input, 200.0)
        }
        // The thickness boundary is allowed; a wall just above it is outside scope.
        val boundary = VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.HEMISPHERICAL_HEAD, input, 178.0)
        assertTrue(boundary * 0.1 <= 0.665 * input.allowableStressMPa)
        assertThrows(VesselScopeException::class.java) {
            VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.HEMISPHERICAL_HEAD, input, 178.000001)
        }
        val nominal = 170.0
        val mawp = VesselThicknessCalculator.maximumAllowablePressureBar(VesselPart.HEMISPHERICAL_HEAD, input, nominal)
        val inverse = VesselThicknessCalculator.calculate(VesselPart.HEMISPHERICAL_HEAD, input.copy(designPressureBar = mawp))
        assertEquals(nominal, inverse.pressureThicknessMm, 1e-9)
        assertTrue(mawp * 0.1 <= 0.665 * input.allowableStressMPa * input.jointEfficiency)
    }
}
