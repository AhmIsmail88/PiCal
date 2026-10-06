package com.ahmedismail.flowtrack.util

import org.junit.Assert.*
import org.junit.Test

/**
 * ASME B31.3-2018 §304.2.1 bend design. Expected values were recomputed
 * independently (see CALC_UI_REVIEW_AR.md).
 */
class BendThicknessCalculatorTest {
    // 1.5D elbow on a 4" pipe: R1 = 1.5 × 114.3 = 171.45 mm.
    private val elbow = BendInput(
        designPressureBar = 20.0, outsideDiameterMm = 114.3, bendRadiusMm = 171.45,
        allowableStressMPa = 100.0, qualityFactorE = 0.85, weldStrengthReductionW = 0.9,
        yCoefficient = 0.4, corrosionAllowanceMm = 1.5, erosionAllowanceMm = 0.5,
        mechanicalAllowanceMm = 1.0
    )

    @Test fun intradosAndExtradosFactorsMatchTheCodeForm() {
        val result = BendThicknessCalculator.calculate(elbow)
        assertEquals(1.25, result.intradosFactorI, 1e-12)
        assertEquals(0.875, result.extradosFactorI, 1e-12)
        assertTrue(result.intradosFactorI > 1.0)
        assertTrue(result.extradosFactorI < 1.0)
    }

    @Test fun intradosGovernsAndExceedsTheStraightPipe() {
        val result = BendThicknessCalculator.calculate(elbow)
        assertEquals(1.8435483870967742, result.intradosThicknessMm, 1e-12)
        assertEquals(1.2954987046632125, result.extradosThicknessMm, 1e-12)
        assertEquals(1.4786545924967658, result.straightThicknessMm, 1e-12)
        assertTrue(result.intradosThicknessMm > result.straightThicknessMm)
        assertEquals(result.intradosThicknessMm + 3.0, result.intradosMinimumRequiredThicknessMm, 1e-12)
        assertEquals(result.intradosThicknessMm + 2.0, result.intradosMinimumFinishedWallMm, 1e-12)
    }

    /** A longer-radius bend converges on the straight-pipe thickness as R1/D grows. */
    @Test fun longRadiusBendsConvergeOnTheStraightPipe() {
        val tight = BendThicknessCalculator.calculate(elbow)
        val medium = BendThicknessCalculator.calculate(elbow.copy(bendRadiusMm = elbow.outsideDiameterMm * 10.0))
        val long = BendThicknessCalculator.calculate(elbow.copy(bendRadiusMm = elbow.outsideDiameterMm * 500.0))
        assertTrue(tight.intradosFactorI > medium.intradosFactorI)
        assertTrue(medium.intradosFactorI > long.intradosFactorI)
        assertEquals(1.0, long.intradosFactorI, 1e-3)
        assertEquals(long.straightThicknessMm, long.intradosThicknessMm, 2e-3)
    }

    @Test fun tighterBendsNeedMoreWall() {
        val tight = BendThicknessCalculator.calculate(elbow.copy(bendRadiusMm = 85.725))
        assertEquals(2.0, tight.intradosFactorI, 1e-12)
        assertEquals(0.8, tight.extradosFactorI, 1e-12)
        assertTrue(tight.intradosThicknessMm > BendThicknessCalculator.calculate(elbow).intradosThicknessMm)
    }

    @Test fun pressuresOutsideTheThinWallScopeAreRejected() {
        assertThrows(ThicknessScopeException::class.java) {
            BendThicknessCalculator.calculate(elbow.copy(designPressureBar = 400.0, qualityFactorE = 1.0, weldStrengthReductionW = 1.0))
        }
    }

    @Test fun measuredIntradosWallIsCheckedAgainstTheRequiredRemainingMetal() {
        val result = BendThicknessCalculator.calculate(elbow)
        val required = result.intradosMinimumFinishedWallMm
        assertTrue(BendThicknessCalculator.checkIntradosWall(result, 114.3, required).passes)
        assertFalse(BendThicknessCalculator.checkIntradosWall(result, 114.3, required - 0.001).passes)
        assertEquals(0.05, BendThicknessCalculator.checkIntradosWall(result, 114.3, required + 0.05).marginMm, 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            BendThicknessCalculator.checkIntradosWall(result, 114.3, 0.0)
        }
    }

    @Test fun invalidInputsAreRejected() {
        // The intrados factor has the denominator 4(R1/D) − 2, so R1 must exceed OD/2.
        assertThrows(IllegalArgumentException::class.java) {
            BendThicknessCalculator.calculate(elbow.copy(bendRadiusMm = 57.15))
        }
        listOf(
            elbow.copy(qualityFactorE = 0.0), elbow.copy(qualityFactorE = 1.2),
            elbow.copy(weldStrengthReductionW = 0.0), elbow.copy(yCoefficient = 1.5),
            elbow.copy(yCoefficient = -0.1), elbow.copy(corrosionAllowanceMm = -1.0),
            elbow.copy(mechanicalAllowanceMm = -1.0), elbow.copy(outsideDiameterMm = 0.0),
            elbow.copy(allowableStressMPa = 0.0), elbow.copy(designPressureBar = -1.0)
        ).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { BendThicknessCalculator.calculate(bad) }
        }
    }
}
