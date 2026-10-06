package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.reference.VerifiedAllowableStress
import org.junit.Assert.*
import org.junit.Test

class VerifiedAllowableStressTest {
    private fun material(grade: String) = "ASTM A106 Gr.$grade (Carbon Steel, Seamless)"
    private fun c(f: Double) = (f - 32.0) * 5.0 / 9.0
    @Test fun roomTemperatureUsesVerifiedGradeAndUnits() {
        for ((grade, ksi) in listOf("A" to 13.7, "B" to 17.1, "C" to 20.0)) {
            val value = VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, material(grade), 20.0)!!
            assertEquals(ksi * 6.894757293168, value.stressMPa, 1e-8)
            assertEquals(100.0, value.columnF, 0.0)
            assertEquals(1.0, value.qualityFactorE, 0.0)
        }
    }
    @Test fun selectsNextNotExceedingColumnAndDropsStressAtHighTemperature() {
        val at = VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, material("B"), c(650.0))!!
        val above = VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, material("B"), c(650.01))!!
        assertEquals(650.0, at.columnF, 0.0)
        assertEquals(17.1, at.stressKsi, 0.0)
        assertEquals(700.0, above.columnF, 0.0)
        assertEquals(15.6, above.stressKsi, 0.0)
        assertTrue(above.stressMPa < at.stressMPa)
        assertEquals(12.0, VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, material("C"), c(800.0))!!.stressKsi, 0.0)
    }
    @Test fun refusesOtherCodeProductAndUnsupportedTemperatures() {
        assertNull(VerifiedAllowableStress.lookup(PipingCode.B31_3_PROCESS, material("B"), 20.0))
        assertNull(VerifiedAllowableStress.lookup(null, material("B"), 20.0))
        assertNull(VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, "ASTM A53 Gr.B", 20.0))
        for (temp in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, 19.9, c(800.01)))
            assertNull(VerifiedAllowableStress.lookup(PipingCode.B31_1_POWER, material("B"), temp))
    }
}
