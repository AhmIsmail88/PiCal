package com.ahmedismail.flowtrack.data.reference

/** Supported steel classes for automatic Y selection; not an allowable-stress table. */
enum class MaterialClass { FERRITIC, AUSTENITIC }

/**
 * Common piping materials with their ASTM-specified minimum yield and
 * tensile strengths (room temperature, as-published in the material
 * specs — NOT temperature-corrected). Sourced from ASTM A106, A53, A312
 * specification data. Density is standard published steel/stainless
 * density (7850 kg/m³ carbon/alloy steel, 8000 kg/m³ austenitic
 * stainless) — used only by the Pipe Weight calculator.
 */
data class PipeMaterial(
    val displayName: String,
    val materialClass: MaterialClass,
    val yieldStrengthMPa: Double,
    val tensileStrengthMPa: Double,
    val densityKgM3: Double
)

object PipeMaterialData {
    private const val CARBON_STEEL_DENSITY = 7850.0
    private const val STAINLESS_DENSITY = 8000.0

    val materials = listOf(
        PipeMaterial("ASTM A106 Gr.B (Carbon Steel, Seamless)", MaterialClass.FERRITIC, 240.0, 415.0, CARBON_STEEL_DENSITY),
        PipeMaterial("ASTM A106 Gr.A (Carbon Steel, Seamless)", MaterialClass.FERRITIC, 205.0, 330.0, CARBON_STEEL_DENSITY),
        PipeMaterial("ASTM A106 Gr.C (Carbon Steel, Seamless)", MaterialClass.FERRITIC, 275.0, 485.0, CARBON_STEEL_DENSITY),
        PipeMaterial("ASTM A53 Gr.B (Carbon Steel, Seamless/ERW)", MaterialClass.FERRITIC, 240.0, 415.0, CARBON_STEEL_DENSITY),
        PipeMaterial("ASTM A333 Gr.6 (Low-Temp Carbon Steel)", MaterialClass.FERRITIC, 240.0, 415.0, CARBON_STEEL_DENSITY),
        PipeMaterial("API 5L Gr.B (Line Pipe)", MaterialClass.FERRITIC, 240.0, 415.0, CARBON_STEEL_DENSITY),
        PipeMaterial("ASTM A312 TP304 (Stainless, Seamless)", MaterialClass.AUSTENITIC, 205.0, 515.0, STAINLESS_DENSITY),
        PipeMaterial("ASTM A312 TP304L (Stainless, Low-Carbon)", MaterialClass.AUSTENITIC, 170.0, 485.0, STAINLESS_DENSITY),
        PipeMaterial("ASTM A312 TP316 (Stainless, Seamless)", MaterialClass.AUSTENITIC, 205.0, 515.0, STAINLESS_DENSITY),
        PipeMaterial("ASTM A312 TP316L (Stainless, Low-Carbon)", MaterialClass.AUSTENITIC, 170.0, 485.0, STAINLESS_DENSITY)
    )
}

