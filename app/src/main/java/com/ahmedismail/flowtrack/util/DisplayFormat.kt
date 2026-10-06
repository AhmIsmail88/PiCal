package com.ahmedismail.flowtrack.util

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow

/**
 * One place for turning a number into text, so the same value reads the same
 * on screen and in the exported PDF, in both app languages.
 *
 * Calculations always keep the unrounded Double — comparisons and the stored
 * data are unaffected; only the presentation is trimmed here. Values too small
 * to show at the requested precision fall back to scientific notation instead
 * of a misleading "0".
 */
object DisplayFormat {
    /** Fixed-point text with trailing zeros removed, capped at [maxDecimals] decimals. */
    fun number(value: Double, maxDecimals: Int = 3): String {
        if (!value.isFinite()) return value.toString()
        if (value != 0.0 && abs(value) < 0.5 * 10.0.pow(-maxDecimals)) {
            return String.format(Locale.US, "%.3e", value)
        }
        val format = "%.${maxDecimals}f"
        return String.format(Locale.US, format, value).trimEnd('0').trimEnd('.')
    }

    /**
     * A wall thickness, allowance or margin in millimetres: 4 decimals is
     * 0.1 µm, far below any real measurement, and keeps the screen and the
     * calculation report identical without printing false precision.
     */
    fun millimetres(value: Double): String = number(value, 4)

    /**
     * Diameter-inches readout (diameter x joint count): grouped for large
     * totals, at most two decimals, trailing zeros trimmed — "508" not "508.00".
     */
    fun diameterInches(value: Double): String =
        if (!value.isFinite()) value.toString()
        else String.format(Locale.US, "%,.2f", value).trimEnd('0').trimEnd('.')
}
