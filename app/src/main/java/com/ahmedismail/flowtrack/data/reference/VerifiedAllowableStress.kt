package com.ahmedismail.flowtrack.data.reference

import com.ahmedismail.flowtrack.util.PipingCode

data class VerifiedStressValue(val stressMPa: Double, val columnF: Double, val stressKsi: Double) {
    val qualityFactorE = 1.0 // These three source rows are seamless only.
    val reference: String get() = "B31.1-2022 Table A-1, pp.126-127, A106 seamless, note (2), column ${columnF.toInt()} F: $stressKsi ksi; E=1.0"
}

/** Small, visually verified subset of the user's TABLE A-1.pdf; never shared with B31.3/VIII. */
object VerifiedAllowableStress {
    const val ROOM_TEMPERATURE_C = 20.0
    private const val KSI_TO_MPA = 6.894757293168
    private val temperaturesF = listOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0, 650.0, 700.0, 750.0, 800.0)
    private val rows = mapOf(
        "ASTM A106 Gr.A (Carbon Steel, Seamless)" to listOf(13.7,13.7,13.7,13.7,13.7,13.7,13.7,12.5,10.7,9.3),
        "ASTM A106 Gr.B (Carbon Steel, Seamless)" to listOf(17.1,17.1,17.1,17.1,17.1,17.1,17.1,15.6,13.0,10.8),
        "ASTM A106 Gr.C (Carbon Steel, Seamless)" to listOf(20.0,20.0,20.0,20.0,20.0,20.0,19.8,18.3,14.8,12.0)
    )

    fun lookup(code: PipingCode?, materialName: String, temperatureC: Double?): VerifiedStressValue? {
        if (code != PipingCode.B31_1_POWER || temperatureC == null || !temperatureC.isFinite() || temperatureC < ROOM_TEMPERATURE_C) return null
        val row = rows[materialName] ?: return null
        val f = temperatureC * 9.0 / 5.0 + 32.0
        // Source headings say "not exceeding": select the next listed temperature,
        // without interpolation or extrapolation, and disclose that column to the user.
        val index = temperaturesF.indexOfFirst { f <= it + 1e-9 }
        if (index < 0) return null
        return VerifiedStressValue(row[index] * KSI_TO_MPA, temperaturesF[index], row[index])
    }
}
