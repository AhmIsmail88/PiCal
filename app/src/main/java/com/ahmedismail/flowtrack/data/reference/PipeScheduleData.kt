package com.ahmedismail.flowtrack.data.reference

data class NominalPipeSize(val label: String, val outsideDiameterMm: Double)

enum class PipeSchedule(val label: String) {
    SCH_10("Sch 10"),
    SCH_40("Sch 40"),
    SCH_80("Sch 80"),
    SCH_160("Sch 160"),
    XXS("XXS")
}

/** ASME B36.10-2022 Table 2-1, SI dimensions. Do not substitute STD/XS or B36.19 S-schedules. */
object PipeScheduleData {
    val sizes = listOf(
        NominalPipeSize("1/2\"", 21.34),
        NominalPipeSize("3/4\"", 26.67),
        NominalPipeSize("1\"", 33.4),
        NominalPipeSize("1-1/4\"", 42.16),
        NominalPipeSize("1-1/2\"", 48.26),
        NominalPipeSize("2\"", 60.32),
        NominalPipeSize("2-1/2\"", 73.02),
        NominalPipeSize("3\"", 88.9),
        NominalPipeSize("4\"", 114.3),
        NominalPipeSize("5\"", 141.3),
        NominalPipeSize("6\"", 168.28),
        NominalPipeSize("8\"", 219.08),
        NominalPipeSize("10\"", 273.0)
    )

    // Wall thickness in mm, by NPS label -> schedule.
    private val thicknessTable: Map<String, Map<PipeSchedule, Double>> = mapOf(
        "1/2\"" to mapOf(PipeSchedule.SCH_10 to 2.11, PipeSchedule.SCH_40 to 2.77, PipeSchedule.SCH_80 to 3.73, PipeSchedule.SCH_160 to 4.78, PipeSchedule.XXS to 7.47),
        "3/4\"" to mapOf(PipeSchedule.SCH_10 to 2.11, PipeSchedule.SCH_40 to 2.87, PipeSchedule.SCH_80 to 3.91, PipeSchedule.SCH_160 to 5.56, PipeSchedule.XXS to 7.82),
        "1\"" to mapOf(PipeSchedule.SCH_10 to 2.77, PipeSchedule.SCH_40 to 3.38, PipeSchedule.SCH_80 to 4.55, PipeSchedule.SCH_160 to 6.35, PipeSchedule.XXS to 9.09),
        "1-1/4\"" to mapOf(PipeSchedule.SCH_10 to 2.77, PipeSchedule.SCH_40 to 3.56, PipeSchedule.SCH_80 to 4.85, PipeSchedule.SCH_160 to 6.35, PipeSchedule.XXS to 9.70),
        "1-1/2\"" to mapOf(PipeSchedule.SCH_10 to 2.77, PipeSchedule.SCH_40 to 3.68, PipeSchedule.SCH_80 to 5.08, PipeSchedule.SCH_160 to 7.14, PipeSchedule.XXS to 10.16),
        "2\"" to mapOf(PipeSchedule.SCH_10 to 2.77, PipeSchedule.SCH_40 to 3.91, PipeSchedule.SCH_80 to 5.54, PipeSchedule.SCH_160 to 8.74, PipeSchedule.XXS to 11.07),
        "2-1/2\"" to mapOf(PipeSchedule.SCH_10 to 3.05, PipeSchedule.SCH_40 to 5.16, PipeSchedule.SCH_80 to 7.01, PipeSchedule.SCH_160 to 9.52, PipeSchedule.XXS to 14.02),
        "3\"" to mapOf(PipeSchedule.SCH_10 to 3.05, PipeSchedule.SCH_40 to 5.49, PipeSchedule.SCH_80 to 7.62, PipeSchedule.SCH_160 to 11.13, PipeSchedule.XXS to 15.24),
        "4\"" to mapOf(PipeSchedule.SCH_10 to 3.05, PipeSchedule.SCH_40 to 6.02, PipeSchedule.SCH_80 to 8.56, PipeSchedule.SCH_160 to 13.49, PipeSchedule.XXS to 17.12),
        "5\"" to mapOf(PipeSchedule.SCH_10 to 3.40, PipeSchedule.SCH_40 to 6.55, PipeSchedule.SCH_80 to 9.52, PipeSchedule.SCH_160 to 15.88, PipeSchedule.XXS to 19.05),
        "6\"" to mapOf(PipeSchedule.SCH_10 to 3.40, PipeSchedule.SCH_40 to 7.11, PipeSchedule.SCH_80 to 10.97, PipeSchedule.SCH_160 to 18.26, PipeSchedule.XXS to 21.95),
        "8\"" to mapOf(PipeSchedule.SCH_10 to 3.76, PipeSchedule.SCH_40 to 8.18, PipeSchedule.SCH_80 to 12.70, PipeSchedule.SCH_160 to 23.01, PipeSchedule.XXS to 22.22),
        "10\"" to mapOf(PipeSchedule.SCH_10 to 4.19, PipeSchedule.SCH_40 to 9.27, PipeSchedule.SCH_80 to 15.09, PipeSchedule.SCH_160 to 28.58, PipeSchedule.XXS to 25.40)
        // NPS 10 XXS is listed separately and equals Sch 140, not Sch 160.
    )

    fun outsideDiameterMm(npsLabel: String): Double? = sizes.find { it.label == npsLabel }?.outsideDiameterMm

    fun wallThicknessMm(npsLabel: String, schedule: PipeSchedule): Double? = thicknessTable[npsLabel]?.get(schedule)

    fun availableSchedules(npsLabel: String): List<PipeSchedule> = thicknessTable[npsLabel]?.keys?.toList().orEmpty()
}


