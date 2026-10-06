package com.ahmedismail.flowtrack.util

/**
 * The governing code for pressure design.
 *
 * ASME B31.1 (Power Piping) and ASME B31.3 (Process Piping) are separate rule
 * sets: equations, coefficients, validity limits and acceptance criteria never
 * cross over, and a result is compliant only with the code it was calculated
 * under — never because it happens to be the thicker of the two.
 */
enum class PipingCode {
    /** ASME B31.3, Process Piping — §304.1.2 for straight pipe, §304.2.1 for bends. */
    B31_3_PROCESS,

    /** ASME B31.1, Power Piping — §104.1.2 for straight pipe, §104.2 for bends and miters. */
    B31_1_POWER
}
