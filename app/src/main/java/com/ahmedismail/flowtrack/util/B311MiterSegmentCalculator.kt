package com.ahmedismail.flowtrack.util

import kotlin.math.sqrt
import kotlin.math.tan

enum class MiterSpacing { CLOSE, WIDE }

data class B311MiterSegmentInput(
    val straightMinimumThicknessMm: Double,
    /** r and R must be established from the actual geometry using ASME B31J. */
    val meanRadiusMm: Double,
    val thetaDeg: Double,
    val spacing: MiterSpacing,
    val bendRadiusMm: Double? = null
)

/** A segment thickness requirement is not the qualification required by §104.7. */
object B311MiterSegmentCalculator {
    fun requiredThicknessMm(input: B311MiterSegmentInput): Double {
        val tm = input.straightMinimumThicknessMm
        val r = input.meanRadiusMm
        require(listOf(tm, r, input.thetaDeg).all { it.isFinite() })
        require(tm > 0.0 && r > 0.0 && input.thetaDeg > 0.0 && input.thetaDeg < 90.0)
        val ts = when (input.spacing) {
            MiterSpacing.CLOSE -> {
                val radius = requireNotNull(input.bendRadiusMm) { "R is required for closely spaced miters." }
                require(radius.isFinite() && radius > r) { "R must be greater than r." }
                val ratio = r / radius
                tm * (2.0 - ratio) / (2.0 * (1.0 - ratio))
            }
            MiterSpacing.WIDE -> {
                // §104.2.3(c)(3)(-b): ts = tm(1 + 0.64 sqrt(r/ts) tan θ).
                // f(ts) is strictly increasing for ts > 0. Bracket it with tm
                // and the RHS evaluated at tm, then solve without rearrangement guesses.
                val k = 0.64 * tan(Math.toRadians(input.thetaDeg))
                var low = tm
                var high = tm * (1.0 + k * sqrt(r / tm))
                require(high.isFinite()) { "Thickness exceeds the numeric range." }
                repeat(100) {
                    val mid = low + (high - low) / 2.0
                    val rhs = tm * (1.0 + k * sqrt(r / mid))
                    if (mid < rhs) low = mid else high = mid
                }
                high
            }
        }
        require(ts.isFinite() && ts >= tm) { "Thickness exceeds the numeric range." }
        return ts
    }
}
