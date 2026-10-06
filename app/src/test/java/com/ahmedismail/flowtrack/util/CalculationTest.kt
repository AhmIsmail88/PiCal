package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.reference.MaterialClass
import org.junit.Assert.*
import org.junit.Test

class CalculationTest {
    @Test fun arabicAndPersianDigitsAndDecimalSeparators() {
        assertEquals(50.8, "٥٠٫٨".toInputDoubleOrNull()!!, 0.000001)
        assertEquals(-12.5, "-۱۲.۵".toInputDoubleOrNull()!!, 0.000001)
        assertEquals(10, "١٠".toInputIntOrNull())
        assertNull("1.5".toInputIntOrNull())
        assertNull("NaN".toInputDoubleOrNull())
        assertNull("Infinity".toInputDoubleOrNull())
    }
    private val thickness = PipeThicknessInput(10.0, 114.3, 138.3333333333, 1.0, 1.0, 0.4, 1.5)
    private val weight = PipeWeightInput(60.3, 3.91, 7850.0, 6.0, true)

    @Test fun pressureUnitsAndAllowance() {
        val result = PipeThicknessCalculator.calculate(thickness)
        assertEquals(0.4119413743, result.calculatedThicknessMm, 0.000001)
        assertEquals(result.calculatedThicknessMm + 1.5, result.minimumRequiredThicknessMm, 0.000001)
    }

    @Test fun zeroPressureRetainsAllowance() {
        val result = PipeThicknessCalculator.calculate(thickness.copy(designPressureBar = 0.0))
        assertEquals(0.0, result.calculatedThicknessMm, 0.0)
        assertEquals(1.5, result.minimumRequiredThicknessMm, 0.0)
    }

    @Test fun invalidThicknessInputsAreRejected() {
        listOf(thickness.copy(designPressureBar = -1.0), thickness.copy(allowableStressMPa = 0.0),
            thickness.copy(outsideDiameterMm = Double.NaN), thickness.copy(qualityFactorE = 1.1),
            thickness.copy(weldStrengthReductionW = 0.0), thickness.copy(corrosionAllowanceMm = -1.0),
            thickness.copy(yCoefficient = Double.POSITIVE_INFINITY), thickness.copy(designPressureBar = 1000.0)
        ).forEach { input -> assertThrows(IllegalArgumentException::class.java) { PipeThicknessCalculator.calculate(input) } }
    }

    @Test fun yInterpolationUsesTemperatureAndMaterialClass() {
        assertEquals(0.6, PipeThicknessCalculator.defaultYCoefficient(MaterialClass.FERRITIC, 524.0), 0.000001)
        assertEquals(0.4, PipeThicknessCalculator.defaultYCoefficient(MaterialClass.AUSTENITIC, 524.0), 0.000001)
    }

    @Test fun pipeAndWaterMassMatchIndependentAnnulusCalculation() {
        val result = PipeWeightCalculator.calculate(weight)
        val odM = 0.0603
        val idM = 0.05248
        val pipeKgM = Math.PI / 4 * (odM * odM - idM * idM) * 7850
        val waterKg = Math.PI / 4 * idM * idM * 1000 * 6
        assertEquals(5.44, result.unitWeightKgM, 0.01)
        assertEquals(pipeKgM, result.unitWeightKgM, 0.000001)
        assertEquals(pipeKgM * 6, result.totalPipeWeightKg, 0.000001)
        assertEquals(waterKg, result.waterFillWeightKg!!, 0.000001)
        assertEquals(pipeKgM * 6 + waterKg, result.totalWithWaterKg, 0.000001)
    }

    @Test fun waterExcludedAndLengthScalesMass() {
        val result = PipeWeightCalculator.calculate(weight.copy(includeWaterFill = false, lengthM = 12.0))
        assertNull(result.waterFillWeightKg)
        assertEquals(result.totalPipeWeightKg, result.totalWithWaterKg, 0.0)
        assertEquals(PipeWeightCalculator.calculate(weight).totalPipeWeightKg * 2, result.totalPipeWeightKg, 0.000001)
    }

    @Test fun impossibleGeometryAndNonFiniteMassAreRejected() {
        listOf(weight.copy(wallThicknessMm = 30.15), weight.copy(wallThicknessMm = 40.0),
            weight.copy(densityKgM3 = -1.0), weight.copy(lengthM = Double.NaN), weight.copy(lengthM = Double.MAX_VALUE)
        ).forEach { input -> assertThrows(IllegalArgumentException::class.java) { PipeWeightCalculator.calculate(input) } }
    }

    @Test fun diameterInchesAreUnitIndependent() {
        assertEquals(20.0, ProgressMath.pipeInches(50.8, DiameterUnit.MM, 10), 0.000001)
        assertEquals(20.0, ProgressMath.pipeInches(2.0, DiameterUnit.INCH, 10), 0.000001)
    }
}

