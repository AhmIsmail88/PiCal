package com.ahmedismail.flowtrack.data.reference

/**
 * Material groups of ASME B31.1-2022 Table 104.1.2-1 (Values of y).
 * B31.3 groups the same rows differently (no nickel columns), so each code keeps
 * its own group list rather than sharing one enum that fits neither.
 */
enum class B311MaterialGroup(val displayName: String) {
    FERRITIC("Ferritic steels"),
    AUSTENITIC("Austenitic steels"),
    NICKEL_N06690("Nickel alloy UNS N06690"),
    NICKEL_ALLOY("Nickel alloys UNS N06617, N08800, N08810, N08825"),
    CAST_IRON("Cast iron"),
    OTHER("Other metals (Mandatory Appendix A)")
}

/**
 * ASME B31.1-2022 Table 104.1.2-1, transcribed at the SI temperatures printed in
 * the table (°C in parentheses, never rounded conversions of the °F headings).
 *
 * General Note (a): the value of y may be interpolated between the 50 °F (27.8 °C)
 * increments shown. General Note (b): for pipe with Do/tm < 6, y for ferritic and
 * austenitic steels designed for 900 °F (482 °C) and below is y = d/(d + Do).
 *
 * A null entry means the table prints "…" at that temperature: no value is
 * published, so the calculator must refuse rather than extrapolate.
 */
object B311YTable {
    /** Table headings, in °C as printed in the code. */
    val temperaturesC = doubleArrayOf(482.0, 510.0, 538.0, 566.0, 593.0, 621.0, 649.0, 677.0)

    private val rows: Map<B311MaterialGroup, List<Double?>> = mapOf(
        B311MaterialGroup.FERRITIC to listOf(0.4, 0.5, 0.7, 0.7, 0.7, 0.7, 0.7, 0.7),
        B311MaterialGroup.AUSTENITIC to listOf(0.4, 0.4, 0.4, 0.4, 0.5, 0.7, 0.7, 0.7),
        // UNS N06690: the 1250 °F (677 °C) and above column is "…" — not published.
        B311MaterialGroup.NICKEL_N06690 to listOf(0.4, 0.4, 0.4, 0.4, 0.5, 0.7, 0.7, null),
        B311MaterialGroup.NICKEL_ALLOY to listOf(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.5, 0.7),
        // Cast iron is tabulated only at 900 °F and below.
        B311MaterialGroup.CAST_IRON to listOf(0.0, null, null, null, null, null, null, null),
        B311MaterialGroup.OTHER to listOf(0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4, 0.4)
    )

    /** True when the table's low-temperature row applies to this group (see Note (b)). */
    fun thinWallRuleApplies(group: B311MaterialGroup, designTemperatureC: Double): Boolean =
        (group == B311MaterialGroup.FERRITIC || group == B311MaterialGroup.AUSTENITIC) && designTemperatureC <= 482.0

    /** General Note (b): y = d / (d + Do) for Do/tm < 6. */
    fun thinWallCoefficient(insideDiameterMm: Double, outsideDiameterMm: Double): Double {
        require(insideDiameterMm > 0.0 && outsideDiameterMm > insideDiameterMm) { "Inside diameter must be positive and smaller than the outside diameter." }
        return insideDiameterMm / (insideDiameterMm + outsideDiameterMm)
    }

    /**
     * y for the design temperature. Values below the first heading use that first
     * value ("900 °F and below"); values above the last use the last one where the
     * table says "and above" — otherwise this throws.
     */
    fun coefficient(group: B311MaterialGroup, designTemperatureC: Double): Double {
        require(designTemperatureC.isFinite() && designTemperatureC > -273.15) { "Temperature must be finite and above absolute zero." }
        val row = rows.getValue(group)
        if (designTemperatureC <= temperaturesC.first()) {
            return row.first() ?: throw B311YOutOfRangeException(group, designTemperatureC)
        }
        if (designTemperatureC >= temperaturesC.last()) {
            return row.last() ?: throw B311YOutOfRangeException(group, designTemperatureC)
        }
        for (i in 0 until temperaturesC.size - 1) {
            val t1 = temperaturesC[i]
            val t2 = temperaturesC[i + 1]
            if (designTemperatureC in t1..t2) {
                val y1 = row[i] ?: throw B311YOutOfRangeException(group, designTemperatureC)
                val y2 = row[i + 1] ?: throw B311YOutOfRangeException(group, designTemperatureC)
                val fraction = (designTemperatureC - t1) / (t2 - t1)
                return y1 + fraction * (y2 - y1)
            }
        }
        throw B311YOutOfRangeException(group, designTemperatureC)
    }
}

/** Table 104.1.2-1 publishes no y at this temperature for this material group. */
class B311YOutOfRangeException(group: B311MaterialGroup, temperatureC: Double) :
    IllegalArgumentException("Table 104.1.2-1 lists no y for ${group.displayName} at $temperatureC °C.")
