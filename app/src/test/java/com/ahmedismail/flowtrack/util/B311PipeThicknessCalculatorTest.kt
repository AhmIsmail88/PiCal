package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.reference.B311MaterialGroup
import com.ahmedismail.flowtrack.data.reference.B311YOutOfRangeException
import com.ahmedismail.flowtrack.data.reference.B311YTable
import org.junit.Assert.*
import org.junit.Test

/**
 * ASME B31.1-2022 §104.1.2 (Power Piping) and Table 104.1.2-1, transcribed from the
 * code excerpt supplied with the project. Expected values were recomputed
 * independently — see CALC_UI_REVIEW_AR.md.
 */
class B311PipeThicknessCalculatorTest {
    private val input = B311PipeInput(
        designPressureBar = 20.0, outsideDiameterMm = 114.3, allowableStressMPa = 138.0,
        qualityFactorE = 1.0, weldStrengthReductionW = 1.0,
        additionalThicknessAMm = 3.0, materialGroup = B311MaterialGroup.FERRITIC,
        designTemperatureC = 20.0
    )

    @Test fun tableValuesMatchTheCodeRows() {
        val headings = B311YTable.temperaturesC
        assertEquals(8, headings.size)
        // Ferritic: 0.4 / 0.5 / 0.7 at 482 / 510 / 538 °C, then 0.7 up to "and above".
        assertEquals(0.4, B311YTable.coefficient(B311MaterialGroup.FERRITIC, 482.0), 1e-12)
        assertEquals(0.5, B311YTable.coefficient(B311MaterialGroup.FERRITIC, 510.0), 1e-12)
        assertEquals(0.7, B311YTable.coefficient(B311MaterialGroup.FERRITIC, 538.0), 1e-12)
        assertEquals(0.7, B311YTable.coefficient(B311MaterialGroup.FERRITIC, 677.0), 1e-12)
        // Austenitic: 0.4 up to 566 °C, then 0.5 at 593 and 0.7 from 621.
        assertEquals(0.4, B311YTable.coefficient(B311MaterialGroup.AUSTENITIC, 566.0), 1e-12)
        assertEquals(0.5, B311YTable.coefficient(B311MaterialGroup.AUSTENITIC, 593.0), 1e-12)
        assertEquals(0.7, B311YTable.coefficient(B311MaterialGroup.AUSTENITIC, 621.0), 1e-12)
        // Nickel alloy UNS N06690 peaks at 0.7; the other nickel alloys reach 0.5 at 649.
        assertEquals(0.7, B311YTable.coefficient(B311MaterialGroup.NICKEL_N06690, 621.0), 1e-12)
        assertEquals(0.5, B311YTable.coefficient(B311MaterialGroup.NICKEL_ALLOY, 649.0), 1e-12)
        // Cast iron is tabulated as 0.0 at 900 °F and below only.
        assertEquals(0.0, B311YTable.coefficient(B311MaterialGroup.CAST_IRON, 20.0), 1e-12)
        // Other metals: 0.4 throughout.
        assertEquals(0.4, B311YTable.coefficient(B311MaterialGroup.OTHER, 677.0), 1e-12)
    }

    @Test fun interpolationFollowsGeneralNoteAAndRefusesUnpublishedCells() {
        assertEquals(0.4642857142857143, B311YTable.coefficient(B311MaterialGroup.FERRITIC, 500.0), 1e-12)
        assertThrows(B311YOutOfRangeException::class.java) {
            B311YTable.coefficient(B311MaterialGroup.NICKEL_N06690, 677.0)
        }
        assertThrows(B311YOutOfRangeException::class.java) {
            B311YTable.coefficient(B311MaterialGroup.CAST_IRON, 510.0)
        }
    }

    @Test fun thicknessFollowsConfirmedEq7WithoutAnIndependentX() {
        val result = B311PipeThicknessCalculator.calculate(input)
        assertEquals(0.4, result.yCoefficient, 1e-12)
        assertFalse(result.thinWallRuleApplied)
        assertEquals(0.82348703170028814, result.pressureThicknessMm, 1e-12)
        assertEquals(3.8234870317002883, result.minimumRequiredThicknessMm, 1e-12)
        // W acts on S·E; the supplied Eq. (7) has no x multiplier.
        assertEquals(0.9144,
            B311PipeThicknessCalculator.calculate(input.copy(weldStrengthReductionW = 0.9)).pressureThicknessMm, 1e-12)
        // A designer-supplied y overrides the table. A larger y enlarges the
        // denominator, so the required thickness falls.
        val overridden = B311PipeThicknessCalculator.calculate(input.copy(yOverride = 0.7))
        assertEquals(0.7, overridden.yCoefficient, 0.0)
        assertTrue(overridden.pressureThicknessMm < result.pressureThicknessMm)
    }

    /** Table 104.1.2-1 General Note (b): Do/tm < 6 makes y = d/(d + Do), so the result must satisfy it. */
    @Test fun thinWallNoteIsSelfConsistentWhenDoOverTmFallsBelowSix() {
        val heavy = input.copy(
            outsideDiameterMm = 100.0, allowableStressMPa = 10.0, designPressureBar = 50.0,
            additionalThicknessAMm = 0.0
        )
        val result = B311PipeThicknessCalculator.calculate(heavy)
        assertTrue(result.thinWallRuleApplied)
        val insideDiameter = heavy.outsideDiameterMm - 2.0 * result.minimumRequiredThicknessMm
        assertEquals(insideDiameter / (insideDiameter + heavy.outsideDiameterMm), result.yCoefficient, 1e-9)
        assertTrue(result.pressureThicknessMm > B311PipeThicknessCalculator
            .calculate(heavy.copy(yOverride = 0.4)).pressureThicknessMm)
    }

    @Test fun invalidInputsAreRejected() {
        listOf(
            input.copy(qualityFactorE = 0.0), input.copy(qualityFactorE = 1.1),
            input.copy(weldStrengthReductionW = 0.0),
            input.copy(additionalThicknessAMm = -1.0), input.copy(outsideDiameterMm = 0.0),
            input.copy(allowableStressMPa = 0.0), input.copy(designPressureBar = -1.0),
            input.copy(yOverride = 1.5)
        ).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { B311PipeThicknessCalculator.calculate(bad) }
        }
    }

    /** B31.3-2018 Eq. (3b) remains subject to the thin-wall equation limits. */
    @Test fun b313InsideDiameterFormMatchesEq3b() {
        val eq3bInput = PipeThicknessInput(
            designPressureBar = 20.0, outsideDiameterMm = 114.3, allowableStressMPa = 100.0,
            qualityFactorE = 0.85, weldStrengthReductionW = 0.9, yCoefficient = 0.4,
            corrosionAllowanceMm = 3.0, erosionAllowanceMm = 0.0, mechanicalAllowanceMm = 0.0
        )
        assertEquals(1.4077025232403719,
            PipeThicknessCalculator.pressureThicknessInsideDiameterForm(eq3bInput, 100.0), 1e-12)
        assertThrows(IllegalArgumentException::class.java) {
            PipeThicknessCalculator.pressureThicknessInsideDiameterForm(eq3bInput, 0.0)
        }
    }

    @Test fun wallThatConsumesTheInsideDiameterIsRejectedWithOrWithoutAutomaticY() {
        val impossible = input.copy(outsideDiameterMm = 100.0, additionalThicknessAMm = 60.0)
        listOf(impossible, impossible.copy(yOverride = 0.4), impossible.copy(designTemperatureC = 600.0)).forEach { bad ->
            assertThrows(B311PipeScopeException::class.java) { B311PipeThicknessCalculator.calculate(bad) }
        }
    }

    @Test fun zeroPressureAndZeroAllowancesStayFiniteAndPreliminary() {
        val result = B311PipeThicknessCalculator.calculate(input.copy(designPressureBar = 0.0, additionalThicknessAMm = 0.0))
        assertEquals(0.0, result.pressureThicknessMm, 0.0)
        assertEquals(0.0, result.minimumRequiredThicknessMm, 0.0)
        assertFalse(result.thinWallRuleApplied)
        assertEquals(CalculationStatus.CALCULATED, result.status)
    }

    @Test fun manualYDoesNotBypassAbsoluteZeroValidation() {
        assertThrows(IllegalArgumentException::class.java) {
            B311PipeThicknessCalculator.calculate(input.copy(designTemperatureC = -273.15, yOverride = 0.4))
        }
    }

    @Test fun b313InsideDiameterAlternativeDoesNotBypass2018ScopeAndInputChecks() {
        val valid = PipeThicknessInput(20.0, 114.3, 100.0, 0.85, 0.9, 0.4, 3.0)
        assertThrows(ThicknessScopeException::class.java) {
            PipeThicknessCalculator.pressureThicknessInsideDiameterForm(valid.copy(designPressureBar = 50.0, allowableStressMPa = 10.0), 100.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PipeThicknessCalculator.pressureThicknessInsideDiameterForm(valid.copy(qualityFactorE = -1.0), 100.0)
        }
    }
}
