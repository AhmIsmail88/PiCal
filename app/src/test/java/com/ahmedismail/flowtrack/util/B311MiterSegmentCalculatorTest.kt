package com.ahmedismail.flowtrack.util

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt
import kotlin.math.tan

class B311MiterSegmentCalculatorTest {
    @Test fun closeSegmentsFollowThePrintedRadiusRatio() {
        val input = B311MiterSegmentInput(5.0, 50.0, 22.5, MiterSpacing.CLOSE, 150.0)
        assertEquals(6.25, B311MiterSegmentCalculator.requiredThicknessMm(input), 1e-12)
    }

    @Test fun wideSegmentsSolveThePrintedImplicitEquation() {
        val input = B311MiterSegmentInput(5.0, 50.0, 22.5, MiterSpacing.WIDE)
        val ts = B311MiterSegmentCalculator.requiredThicknessMm(input)
        assertEquals(5.0 * (1.0 + 0.64 * sqrt(50.0 / ts) * tan(Math.PI / 8.0)), ts, 1e-10)
        assertTrue(ts > 5.0)
        val smaller = ts - 0.001
        assertTrue(smaller < 5.0 * (1.0 + 0.64 * sqrt(50.0 / smaller) * tan(Math.PI / 8.0)))
    }

    @Test fun invalidAndSingularSegmentGeometryIsRejected() {
        val input = B311MiterSegmentInput(5.0, 50.0, 22.5, MiterSpacing.CLOSE, 150.0)
        listOf(input.copy(bendRadiusMm = 50.0), input.copy(bendRadiusMm = null), input.copy(straightMinimumThicknessMm = 0.0),
            input.copy(meanRadiusMm = Double.NaN), input.copy(thetaDeg = 90.0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { B311MiterSegmentCalculator.requiredThicknessMm(it) }
        }
    }
}
