package com.ahmedismail.flowtrack.util

/** Accept Arabic/Persian decimal digits and the Arabic decimal separator. */
fun String.toInputDoubleOrNull(): Double? = buildString {
    for (character in this@toInputDoubleOrNull.trim()) {
        val digit = Character.digit(character, 10)
        append(when {
            digit >= 0 -> ('0'.code + digit).toChar()
            character == '٫' -> '.'
            else -> character
        })
    }
}.toDoubleOrNull()?.takeIf { it.isFinite() }

fun String.toInputIntOrNull(): Int? {
    val value = toInputDoubleOrNull() ?: return null
    return value.takeIf { it >= Int.MIN_VALUE && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()
}
