package com.ahmedismail.flowtrack.util

data class PipeWeightInput(
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val densityKgM3: Double,
    val lengthM: Double,
    val includeWaterFill: Boolean
)

data class PipeWeightResult(
    /** Bare pipe weight per unit length — kg/m. */
    val unitWeightKgM: Double,
    /** unitWeightKgM * length. */
    val totalPipeWeightKg: Double,
    /** Weight of water filling the bore over the given length, if requested. */
    val waterFillWeightKg: Double?,
    /** totalPipeWeightKg + waterFillWeightKg (or just totalPipeWeightKg if water fill wasn't requested). */
    val totalWithWaterKg: Double
)

/**
 * Standard steel/stainless pipe weight formula: the pipe wall is an
 * annulus, so its cross-sectional area is π·t·(OD − t) (derived from
 * π/4·(OD² − ID²) with ID = OD − 2t) — weight per unit length is that
 * area times material density.
 *
 * Verified against published NPS 2"/4"/6" Sch 40 carbon steel pipe weight
 * tables (5.44 / 16.07 / 28.26 kg/m) before shipping — this is the same
 * formula behind the classic imperial constant "10.69 × (OD−t) × t" for
 * lb/ft (10.69 ≈ π × 12 × 0.2836 lb/in³ steel density), just kept in SI
 * units with density as an explicit input so it works for any material.
 */
object PipeWeightCalculator {
    private const val WATER_DENSITY_KG_M3 = 1000.0

    fun calculate(input: PipeWeightInput): PipeWeightResult {
        require(listOf(input.outsideDiameterMm, input.wallThicknessMm, input.densityKgM3, input.lengthM).all { it.isFinite() && it > 0.0 }) { "Dimensions, density and length must be finite and positive." }
        require(2.0 * input.wallThicknessMm < input.outsideDiameterMm) { "Wall thickness must leave a positive bore diameter." }
        val od = input.outsideDiameterMm
        val t = input.wallThicknessMm
        // Area in mm², density in kg/m³ → weight in kg per mm of length is
        // area(mm²) × density(kg/m³) / 1e9; per metre (×1000mm) that's /1e6.
        val unitWeightKgM = Math.PI * t * (od - t) * input.densityKgM3 / 1_000_000.0
        val totalPipeWeightKg = unitWeightKgM * input.lengthM

        val waterFillWeightKg = if (input.includeWaterFill) {
            val idMm = od - 2 * t
            val boreAreaMm2 = Math.PI / 4.0 * idMm * idMm
            boreAreaMm2 * WATER_DENSITY_KG_M3 * input.lengthM / 1_000_000.0
        } else null

        val totalWithWaterKg = totalPipeWeightKg + (waterFillWeightKg ?: 0.0)
        require(listOf(unitWeightKgM, totalPipeWeightKg, totalWithWaterKg).all { it.isFinite() }) { "Weight exceeds the supported numeric range." }

        return PipeWeightResult(
            unitWeightKgM = unitWeightKgM,
            totalPipeWeightKg = totalPipeWeightKg,
            waterFillWeightKg = waterFillWeightKg,
            totalWithWaterKg = totalWithWaterKg
        )
    }
}
