package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.reference.MaterialClass
import com.ahmedismail.flowtrack.data.reference.PipeSchedule
import com.ahmedismail.flowtrack.data.reference.PipeScheduleData
import org.junit.Assert.*
import org.junit.Test

/** References and independently calculated cases are documented in ASME_REVIEW_AR.md. */
class AsmeReviewTest {
    private val input = PipeThicknessInput(20.0, 114.3, 100.0, 0.85, 0.9, 0.4, 1.5, 0.5, 1.0)

    @Test fun finishedMeasurementIsNotReducedByMillToleranceOrMachiningTwice() {
        val result = PipeThicknessCalculator.calculate(input)
        assertEquals(3.4786545924967658, result.minimumFinishedWallMm, 1e-12)
        val measured = PipeThicknessCalculator.checkFinishedWall(result, 114.3, 3.5)
        assertTrue(measured.passes)
        assertEquals(3.5, measured.measuredMinimumMm, 0.0)
        assertEquals(0.0213454075032342, measured.marginMm, 1e-12)
        // A nominal 3.5 mm pipe fails the separate purchasing check, despite this
        // actual 3.5 mm NET finished measurement meeting the remaining-wall check.
        assertFalse(PipeThicknessCalculator.checkNominalWall(result, 114.3, 3.5, 12.5).passes)
        assertEquals(result.minimumFinishedWallMm,
            PipeThicknessCalculator.calculate(input.copy(mechanicalAllowanceMm = 2.0)).minimumFinishedWallMm, 0.0)
    }

    @Test fun finishedWallBoundaryUsesFullPrecisionAndRejectsInvalidMeasurement() {
        val result = PipeThicknessCalculator.calculate(input)
        assertTrue(PipeThicknessCalculator.checkFinishedWall(result, 114.3, result.minimumFinishedWallMm).passes)
        assertFalse(PipeThicknessCalculator.checkFinishedWall(result, 114.3, result.minimumFinishedWallMm - 0.000001).passes)
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 57.15).forEach {
            assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.checkFinishedWall(result, 114.3, it) }
        }
    }

    @Test fun noMechanicalRemovalMeansSameRequiredWallBeforeAndAfterFabrication() {
        val result = PipeThicknessCalculator.calculate(input.copy(mechanicalAllowanceMm = 0.0))
        assertEquals(result.minimumRequiredThicknessMm, result.minimumFinishedWallMm, 0.0)
    }

    @Test fun nonUnitFactorsAndAllAllowancesMatchDecimalCalculation() {
        val result = PipeThicknessCalculator.calculate(input)
        assertEquals(1.4786545924967658, result.calculatedThicknessMm, 1e-12)
        assertEquals(4.4786545924967658, result.minimumRequiredThicknessMm, 1e-12)
        assertEquals(5.118462391424875, PipeThicknessCalculator.requiredNominalThicknessMm(result, 12.5), 1e-12)
    }

    @Test fun nominalWallDoesNotPassBeforeManufacturingToleranceIsApplied() {
        val result = PipeThicknessCalculator.calculate(input)
        assertTrue(5.0 > result.minimumRequiredThicknessMm)
        assertFalse(PipeThicknessCalculator.checkNominalWall(result, 114.3, 5.0, 12.5).passes)
        assertTrue(PipeThicknessCalculator.checkNominalWall(result, 114.3, 5.12, 12.5).passes)
    }

    @Test fun comparisonUsesUnroundedThickness() {
        val result = PipeThicknessResult(1.23456, 1.23456)
        assertFalse(PipeThicknessCalculator.checkNominalWall(result, 100.0, 1.2345, 0.0).passes)
        assertTrue(PipeThicknessCalculator.checkNominalWall(result, 100.0, 1.23456, 0.0).passes)
    }

    @Test fun yUsesThePublishedSiKnotsAndInterpolationWithoutDisplayRounding() {
        val ferritic = listOf(482.0 to 0.4, 510.0 to 0.5, 538.0 to 0.7, 677.0 to 0.7)
        val austenitic = listOf(538.0 to 0.4, 566.0 to 0.4, 593.0 to 0.5, 621.0 to 0.7)
        ferritic.forEach { (t, y) -> assertEquals(y, PipeThicknessCalculator.defaultYCoefficient(MaterialClass.FERRITIC, t), 1e-12) }
        austenitic.forEach { (t, y) -> assertEquals(y, PipeThicknessCalculator.defaultYCoefficient(MaterialClass.AUSTENITIC, t), 1e-12) }
        val y = PipeThicknessCalculator.defaultYCoefficient(MaterialClass.FERRITIC, 500.0)
        assertEquals(0.4642857142857143, y, 1e-12)
        assertEquals(0.45, PipeThicknessCalculator.defaultYCoefficient(MaterialClass.AUSTENITIC, 579.5), 1e-12)
        assertTrue(PipeThicknessCalculator.calculate(input.copy(yCoefficient = y)).calculatedThicknessMm >
            PipeThicknessCalculator.calculate(input.copy(yCoefficient = 0.47)).calculatedThicknessMm)
    }

    @Test fun pressureRatioLimitAllowsEqualityButRejectsExceedance() {
        val boundary = PipeThicknessInput(385.0, 120.0, 100.0, 1.0, 1.0, 0.7, 0.0)
        assertTrue(PipeThicknessCalculator.calculate(boundary).calculatedThicknessMm < 20.0)
        assertThrows(ThicknessScopeException::class.java) { PipeThicknessCalculator.calculate(boundary.copy(designPressureBar = 385.00001)) }
    }

    @Test fun thicknessLimitRejectsEqualityIndependentlyOfPressureRatio() {
        val boundary = PipeThicknessInput(300.0, 120.0, 90.0, 1.0, 1.0, 0.0, 0.0)
        assertThrows(ThicknessScopeException::class.java) { PipeThicknessCalculator.calculate(boundary) }
        assertTrue(PipeThicknessCalculator.calculate(boundary.copy(designPressureBar = 299.99)).calculatedThicknessMm < 20.0)
        assertThrows(ThicknessScopeException::class.java) {
            PipeThicknessCalculator.calculate(boundary.copy(designPressureBar = 280.0, allowableStressMPa = 100.0, weldStrengthReductionW = 0.5, yCoefficient = 0.4))
        }
    }

    @Test fun allowancesAndNominalGeometryAreValidated() {
        listOf(input.copy(erosionAllowanceMm = -0.1), input.copy(mechanicalAllowanceMm = -0.1),
            input.copy(erosionAllowanceMm = Double.NaN), input.copy(mechanicalAllowanceMm = 100.0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.calculate(it) }
        }
        val result = PipeThicknessCalculator.calculate(input)
        listOf(-1.0, 100.0, Double.NaN).forEach { tolerance ->
            assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.requiredNominalThicknessMm(result, tolerance) }
        }
        assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.checkNominalWall(result, 114.3, 57.15, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.defaultYCoefficient(MaterialClass.FERRITIC, -273.15) }
    }

    @Test fun reducedAllowableStressOrWeldFactorCannotReduceRequiredThickness() {
        val base = PipeThicknessCalculator.calculate(input).calculatedThicknessMm
        assertTrue(PipeThicknessCalculator.calculate(input.copy(allowableStressMPa = 80.0)).calculatedThicknessMm > base)
        assertTrue(PipeThicknessCalculator.calculate(input.copy(weldStrengthReductionW = 0.8)).calculatedThicknessMm > base)
    }

    @Test fun dimensionsMatchB3610_2022SiRows() {
        // OD, Sch 10, Sch 40, Sch 80, Sch 160, XXS. Table 2-1, printed pages 3-8.
        val rows = mapOf(
            "1/2\"" to listOf(21.34, 2.11, 2.77, 3.73, 4.78, 7.47),
            "3/4\"" to listOf(26.67, 2.11, 2.87, 3.91, 5.56, 7.82),
            "1\"" to listOf(33.40, 2.77, 3.38, 4.55, 6.35, 9.09),
            "1-1/4\"" to listOf(42.16, 2.77, 3.56, 4.85, 6.35, 9.70),
            "1-1/2\"" to listOf(48.26, 2.77, 3.68, 5.08, 7.14, 10.16),
            "2\"" to listOf(60.32, 2.77, 3.91, 5.54, 8.74, 11.07),
            "2-1/2\"" to listOf(73.02, 3.05, 5.16, 7.01, 9.52, 14.02),
            "3\"" to listOf(88.90, 3.05, 5.49, 7.62, 11.13, 15.24),
            "4\"" to listOf(114.30, 3.05, 6.02, 8.56, 13.49, 17.12),
            "5\"" to listOf(141.30, 3.40, 6.55, 9.52, 15.88, 19.05),
            "6\"" to listOf(168.28, 3.40, 7.11, 10.97, 18.26, 21.95),
            "8\"" to listOf(219.08, 3.76, 8.18, 12.70, 23.01, 22.22),
            "10\"" to listOf(273.0, 4.19, 9.27, 15.09, 28.58, 25.40)
        )
        val schedules = listOf(PipeSchedule.SCH_10, PipeSchedule.SCH_40, PipeSchedule.SCH_80, PipeSchedule.SCH_160, PipeSchedule.XXS)
        rows.forEach { (size, expected) ->
            assertEquals(size, expected[0], PipeScheduleData.outsideDiameterMm(size)!!, 1e-9)
            schedules.forEachIndexed { index, schedule ->
                assertEquals("$size ${schedule.label}", expected[index + 1], PipeScheduleData.wallThicknessMm(size, schedule)!!, 1e-9)
            }
        }
    }

    @Test fun pipeMassMatchesPublishedNominalMassesWithSiRounding() {
        val cases = listOf(Triple("2\"", PipeSchedule.SCH_40, 5.44), Triple("4\"", PipeSchedule.SCH_40, 16.08),
            Triple("10\"", PipeSchedule.SCH_80, 95.98), Triple("10\"", PipeSchedule.XXS, 155.10))
        cases.forEach { (size, schedule, kgm) ->
            val mass = PipeWeightCalculator.calculate(PipeWeightInput(PipeScheduleData.outsideDiameterMm(size)!!,
                PipeScheduleData.wallThicknessMm(size, schedule)!!, 7850.0, 1.0, false))
            assertEquals(kgm, mass.unitWeightKgM, 0.0051)
        }
    }
}
