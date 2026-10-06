package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.entity.DiameterUnit

/** Diameter-inches, independent of the unit used to enter the diameter. */
object ProgressMath {
    fun diameterInches(diameter: Double, unit: DiameterUnit): Double =
        if (unit == DiameterUnit.MM) diameter / 25.4 else diameter

    fun pipeInches(diameter: Double, unit: DiameterUnit, joints: Int): Double =
        diameterInches(diameter, unit) * joints
}
