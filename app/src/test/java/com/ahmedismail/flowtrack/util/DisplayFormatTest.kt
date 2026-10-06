package com.ahmedismail.flowtrack.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Display rounding is presentation only — the unit tests above it cover the unrounded calculation. */
class DisplayFormatTest {
    @Test fun trimsTrailingZerosWithoutLosingWholeNumbers() {
        assertEquals("100", DisplayFormat.number(100.0, 4))
        assertEquals("20", DisplayFormat.number(20.0, 2))
        assertEquals("1000", DisplayFormat.number(1000.0, 3))
        assertEquals("0", DisplayFormat.number(0.0, 4))
        assertEquals("5.44", DisplayFormat.number(5.4394174047119783, 2))
    }

    @Test fun keepsTheDigitsThatMatter() {
        assertEquals("1.4787", DisplayFormat.millimetres(1.4786545924967658))
        assertEquals("3.4787", DisplayFormat.millimetres(3.4786545924967658))
        assertEquals("4.4787", DisplayFormat.millimetres(4.4786545924967658))
        assertEquals("5.1185", DisplayFormat.millimetres(5.118462391424875))
        assertEquals("-0.0213", DisplayFormat.millimetres(-0.0213454075032342))
        assertEquals("0.0213", DisplayFormat.millimetres(0.0213454075032342))
    }

    @Test fun valuesTooSmallForThePrecisionDoNotPrintAsZero() {
        assertEquals("1.200e-05", DisplayFormat.millimetres(0.000012))
        assertEquals("0.0001", DisplayFormat.millimetres(0.0001))
        assertEquals("6.022", DisplayFormat.number(6.02214076, 3))
    }

    @Test fun nonFiniteValuesArePassedThroughUnchanged() {
        assertEquals("NaN", DisplayFormat.number(Double.NaN))
        assertEquals("Infinity", DisplayFormat.number(Double.POSITIVE_INFINITY))
        assertEquals("-Infinity", DisplayFormat.number(Double.NEGATIVE_INFINITY))
    }
}
