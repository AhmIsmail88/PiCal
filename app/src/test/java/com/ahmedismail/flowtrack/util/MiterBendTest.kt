package com.ahmedismail.flowtrack.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Miter bends: shared geometry, ASME B31.3-2018 §304.2.3 (Pm) and ASME B31.1-2022
 * §104.2.3 (the 10 psi / 100 psi / §104.7 decision tree). Expected values were
 * recomputed independently — see CALC_UI_REVIEW_AR.md.
 */
class MiterBendTest {
    // 90° over two joints: deflection 45° per joint, θ=22.5°, R1=150 mm.
    private val geometry = MiterGeometry(
        totalBendAngleDeg = 90.0, jointCount = 2,
        outsideDiameterMm = 114.3, thicknessMm = 6.02, effectiveRadiusMm = 150.0
    )
    private val input = B313MiterInput(
        designPressureBar = 20.0, allowableStressMPa = 138.0, qualityFactorE = 1.0,
        weldStrengthReductionW = 1.0, allowancesMm = 1.5, geometry = geometry
    )

    /** θ is half the deflection per joint: the code's definition, everywhere. */
    @Test fun thetaIsHalfTheDeflectionPerJoint() {
        fun theta(total: Double, joints: Int) = MiterGeometry(total, joints, 114.3, 6.02, 50.0).thetaDeg
        assertEquals(45.0, theta(90.0, 1), 1e-12)
        assertEquals(22.5, theta(90.0, 2), 1e-12)
        assertEquals(15.0, theta(90.0, 3), 1e-12)
        assertEquals(22.5, theta(45.0, 1), 1e-12)
        assertEquals(45.0, geometry.deflectionPerJointDeg, 1e-12)
    }

    @Test fun geometryUsesActualEffectiveRadiusAndMeanPipeRadius() {
        assertEquals(54.14, geometry.meanRadiusMm, 1e-12)
        assertEquals(150.0, geometry.effectiveRadiusMm, 1e-12)
    }

    @Test fun closelySpacedMultipleMiterTakesTheSmallerOfEq4aAndEq4b() {
        val result = B313MiterCalculator.calculate(input)
        assertEquals(59.951023308622496, result.pmEqA!!, 1e-9)
        assertEquals(89.84187618705698, result.pmEqB!!, 1e-9)
        assertEquals(59.951023308622496, result.maxAllowablePressureBar, 1e-9)
        assertTrue(result.equationId.startsWith("Eq. 4a & 4b"))
        assertTrue(result.passes)
        assertEquals(39.951023308622496, result.marginBar, 1e-9)
        assertFalse(B313MiterCalculator.calculate(input.copy(designPressureBar = 65.0)).passes)
    }

    @Test fun singleMitersUse2018Eq4aAndEq4c() {
        val single = input.copy(geometry = geometry.copy(totalBendAngleDeg = 45.0, jointCount = 1))
        val low = B313MiterCalculator.calculate(single)
        assertEquals(MiterEquation.SINGLE_4A, low.equation)
        assertEquals(59.951023308622496, low.maxAllowablePressureBar, 1e-9)
        val steep = single.copy(geometry = single.geometry.copy(totalBendAngleDeg = 90.0))
        val high = B313MiterCalculator.calculate(steep)
        assertEquals(MiterEquation.SINGLE_4C, high.equation)
        assertEquals(21.631529020854742, high.maxAllowablePressureBar, 1e-9)
    }

    /** The code gives Pm, so a thickness is only "the smallest T that satisfies Pm ≥ P". */
    @Test fun requiredThicknessIsTheSmallestWallMeetingPm() {
        val required = B313MiterCalculator.requiredThicknessMm(input.copy(designPressureBar = 20.0))!!
        assertTrue(required <= 6.02)
        assertTrue(B313MiterCalculator.calculate(
            input.copy(designPressureBar = 20.0, geometry = geometry.copy(thicknessMm = required))
        ).maxAllowablePressureBar >= 20.0)
        assertTrue(B313MiterCalculator.calculate(
            input.copy(designPressureBar = 20.0, geometry = geometry.copy(thicknessMm = required - 0.01))
        ).maxAllowablePressureBar < 20.0)
    }

    @Test fun b311DecisionTreeSelectsThePressurePathAndListsEveryCondition() {
        val safe = B311MiterConditions(
            crotchDimensionBMm = 60.0, nominalThicknessMm = 6.02,
            segmentsMeetPara1041 = true, fluidNonflammableNonToxicIncompressible = true,
            fullPressureCyclesUnder7000 = true, fullPenetrationWelds = true
        )
        // θ ≤ 22.5° with B ≥ 6tn -> the 100 psi path.
        val hundred = B311MiterCalculator.calculate(5.0, 22.5, safe)
        assertEquals(100.0 * 0.06894757293168, hundred.pressureLimitBar!!, 1e-15)
        assertTrue(hundred.passes!!)
        assertFalse(hundred.qualificationRequired)
        assertTrue(B311MiterCalculator.calculate(8.0, 22.5, safe).qualificationRequired)
        assertEquals(5, hundred.conditions.size)
        assertEquals("(b)(1)-(2)", hundred.conditions.first().id)
        assertTrue(hundred.conditions.all { it.satisfied })

        // θ > 22.5° -> the 10 psi path (10 psi = 0.689 475 7 bar).
        val ten = B311MiterCalculator.calculate(0.05, 30.0, safe)
        assertTrue(ten.pathId.contains("10 psi"))
        assertEquals(10.0 * 0.06894757293168, ten.pressureLimitBar!!, 1e-15)
        assertTrue(ten.passes!!)
        assertTrue(B311MiterCalculator.calculate(0.8, 30.0, safe).qualificationRequired)

        // B < 6tn also routes to the 10 psi path.
        val shortSegment = B311MiterCalculator.calculate(0.05, 15.0, safe.copy(crotchDimensionBMm = 10.0))
        assertTrue(shortSegment.pathId.contains("10 psi"))

        // Any failed common condition drops out of both simplified paths.
        val notQualified = B311MiterCalculator.calculate(0.05, 15.0, safe.copy(fullPressureCyclesUnder7000 = false))
        assertTrue(notQualified.qualificationRequired)
        assertNull(notQualified.passes)
        assertNull(notQualified.pressureLimitBar)
        assertTrue(notQualified.pathId.contains("104.7"))
    }

    @Test fun invalidMiterInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { MiterGeometry(90.0, 0, 114.3, 6.02, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { MiterGeometry(180.0, 1, 114.3, 6.02, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { MiterGeometry(90.0, 1, 114.3, 60.0, 50.0) }
        assertThrows(IllegalArgumentException::class.java) { MiterGeometry(90.0, 1, 114.3, 6.02, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { B313MiterCalculator.calculate(input.copy(qualityFactorE = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { B313MiterCalculator.calculate(input.copy(designPressureBar = 0.0)) }
        assertThrows(IllegalArgumentException::class.java) {
            B313MiterCalculator.calculate(input.copy(allowancesMm = 6.5))
        }
    }

    @Test fun unsupportedSteepCloseMiterHasNeitherPressureNorThicknessVerdict() {
        val steep = input.copy(geometry = geometry.copy(totalBendAngleDeg = 120.0))
        assertEquals(30.0, steep.geometry.thetaDeg, 0.0)
        assertThrows(MiterScopeException::class.java) { B313MiterCalculator.calculate(steep) }
        assertThrows(MiterScopeException::class.java) { B313MiterCalculator.requiredThicknessMm(steep) }
    }

    @Test fun closeMiterRejectsNonpositiveInnerBendRadiusAndSingularGeometry() {
        // R1 <= r2 can make both terms of Eq. 4b negative and produce a false positive pressure.
        listOf(10.0, geometry.meanRadiusMm).forEach { radius ->
            assertThrows(MiterScopeException::class.java) {
                B313MiterCalculator.calculate(input.copy(geometry = geometry.copy(effectiveRadiusMm = radius)))
            }
        }
    }

    @Test fun singleMiterDoesNotExposeInapplicableMultipleMiterPressures() {
        val wide = geometry.copy(totalBendAngleDeg = 45.0, jointCount = 1)
        val shallow = B313MiterCalculator.calculate(input.copy(geometry = wide))
        val steep = B313MiterCalculator.calculate(input.copy(geometry = wide.copy(totalBendAngleDeg = 90.0)))
        assertEquals(MiterEquation.SINGLE_4A, shallow.equation)
        assertEquals(MiterEquation.SINGLE_4C, steep.equation)
        assertNotNull(shallow.pmEqA)
        assertNull(steep.pmEqA)
        listOf(shallow, steep).forEach { result ->
            assertNull(result.pmEqB)
        }
    }

    @Test fun b311PressureBoundariesRequireQualificationOnlyAboveTheSelectedLimit() {
        val safe = B311MiterConditions(60.0, 6.02, true, true, true, true)
        listOf(22.5 to B311MiterCalculator.HUNDRED_PSI_BAR, 30.0 to B311MiterCalculator.TEN_PSI_BAR).forEach { (angle, limit) ->
            val boundary = B311MiterCalculator.calculate(limit, angle, safe)
            assertEquals(CalculationStatus.ACCEPTABLE, boundary.status)
            assertTrue(boundary.passes!!)
            val above = B311MiterCalculator.calculate(limit + 1e-8, angle, safe)
            assertEquals(CalculationStatus.QUALIFICATION_REQUIRED, above.status)
            assertEquals(B311MiterPath.QUALIFICATION, above.path)
            assertNull(above.passes)
            assertNull(above.pressureLimitBar)
        }
    }

    @Test fun nonfiniteGeometryAndConditionDimensionsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { geometry.copy(outsideDiameterMm = Double.POSITIVE_INFINITY) }
        val safe = B311MiterConditions(60.0, 6.02, true, true, true, true)
        listOf(safe.copy(crotchDimensionBMm = Double.POSITIVE_INFINITY), safe.copy(nominalThicknessMm = Double.NaN)).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { B311MiterCalculator.calculate(1.0, 22.5, bad) }
        }
    }

    @Test fun thicknessSolverRejectsInvalidTargetPressure() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { pressure ->
            assertThrows(IllegalArgumentException::class.java) { B313MiterCalculator.requiredThicknessMm(input, pressure) }
        }
    }

    @Test fun equation5UsesAllThreeSiWallRangesAndRejectsTooSmallRadius() {
        // θ=45° makes tan θ=1: minimum R1 is A + D/2.
        assertEquals(75.0, B313MiterCalculator.minimumEffectiveRadiusMm(100.0, 13.0, 45.0), 1e-9)
        assertEquals(80.0, B313MiterCalculator.minimumEffectiveRadiusMm(100.0, 15.0, 45.0), 1e-9)
        assertEquals(96.0, B313MiterCalculator.minimumEffectiveRadiusMm(100.0, 24.0, 45.0), 1e-9)
        val minimum = B313MiterCalculator.minimumEffectiveRadiusMm(114.3, 4.52, 22.5)
        assertThrows(MiterScopeException::class.java) {
            B313MiterCalculator.calculate(input.copy(geometry = geometry.copy(effectiveRadiusMm = minimum - 0.001)))
        }
        assertTrue(B313MiterCalculator.calculate(input.copy(geometry = geometry.copy(effectiveRadiusMm = minimum))).maxAllowablePressureBar > 0.0)
    }

    @Test fun wallExtensionUsesLargerOfBoth2018Requirements() {
        val result = B313MiterCalculator.calculate(input)
        assertEquals(maxOf(2.5 * kotlin.math.sqrt(54.14 * 6.02), kotlin.math.tan(Math.PI / 8.0) * (150.0 - 54.14)), result.minimumWallExtensionMm, 1e-9)
    }

    @Test fun smallOffsetDoesNotProduceAMiterVerdict() {
        assertThrows(MiterScopeException::class.java) {
            B313MiterCalculator.calculate(input.copy(geometry = geometry.copy(totalBendAngleDeg = 3.0, jointCount = 1)))
        }
    }
}
