package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.ui.components.DateRange
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class DateRangeTest {
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun riyadhFilterIncludesTheWholeLocalDay() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Riyadh"))
            val selected = millis("2026-10-01T00:00:00Z")
            val range = DateRange(selected, selected)
            assertTrue(range.contains(millis("2026-09-30T21:00:00Z")))
            assertTrue(range.contains(millis("2026-10-01T20:59:59.999Z")))
            assertFalse(range.contains(millis("2026-09-30T20:59:59.999Z")))
            assertFalse(range.contains(millis("2026-10-01T21:00:00Z")))
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun daylightSavingDayUsesCalendarBoundaries() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val selected = millis("2026-03-08T00:00:00Z")
            val range = DateRange(selected, selected)
            assertTrue(range.contains(millis("2026-03-08T05:00:00Z")))
            assertTrue(range.contains(millis("2026-03-09T03:59:59.999Z")))
            assertFalse(range.contains(millis("2026-03-09T04:00:00Z")))
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun unboundedRangeIncludesAnyTimestamp() {
        assertTrue(DateRange().contains(0))
        assertTrue(DateRange().contains(System.currentTimeMillis()))
    }
}
