package com.ahmedismail.flowtrack.util

/** Distinguishes a pressure verdict from a result that still needs qualification or verification. */
enum class CalculationStatus {
    CALCULATED, ACCEPTABLE, NOT_ACCEPTABLE, QUALIFICATION_REQUIRED, OUT_OF_CODE_LIMITS,
    NOT_IMPLEMENTED, PRELIMINARY
}
