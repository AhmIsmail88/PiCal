package com.ahmedismail.flowtrack.util

/** The same formula text is used in the calculator and the report. P is in MPa. */
object EngineeringEquations {
    fun pipe(code: PipingCode): String = when (code) {
        PipingCode.B31_3_PROCESS -> "ASME B31.3-2018, 304.1.1 / 304.1.2, (2), (3a)\nt = P D / [2 (S E W + P Y)]\ntm = t + c; c = corrosion + erosion + mechanical"
        PipingCode.B31_1_POWER -> "ASME B31.1-2022, 104.1.2(a), (7)\ntm = P Do / [2 (S E W + P y)] + A\nA = corrosion + erosion + mechanical"
    }
    const val WEIGHT = "Bare-pipe mass (geometry, not a pressure-design rule)\nm/L = pi t (D - t) rho / 1,000,000 [kg/m]\nm = (m/L) L; ID = D - 2t\nm_water = pi ID^2 L / 4,000 [kg], rho_water = 1000 kg/m^3"
    const val BEND = "ASME B31.3-2018, 304.2.1, (3c)-(3e)\nt = P D / [2 (S E W / I + P Y)]; tm = t + c\nI_in = [4(R1/D) - 1] / [4(R1/D) - 2]\nI_out = [4(R1/D) + 1] / [4(R1/D) + 2]"
    const val MITER_313 = "ASME B31.3-2018, 304.2.3\nq = T - c; r2 = (D - T)/2; theta = angle/(2 joints)\n(4a) Pm = S E W q/r2 * q/[q + 0.643 tan(theta) sqrt(r2 q)]\n(4b) Pm = S E W q/r2 * (R1-r2)/(R1-0.5 r2)\nMultiple: min(4a,4b), theta <= 22.5 deg\nSingle: (4a) if theta <= 22.5 deg; otherwise (4c)\n(4c): use 1.25 instead of 0.643 in (4a)\n(5) R1 >= A/tan(theta) + D/2 (empirical A, not allowance c)"
    const val MITER_311 = "ASME B31.1-2022, 104.2.3(a)-(c)\nSimplified paths: 10 psi or 100 psi + all applicable conditions\nOtherwise: qualification under 104.7\n(c)(3), close: ts = tm (2-r/R)/[2(1-r/R)]\n(c)(3), wide: ts = tm [1 + 0.64 sqrt(r/ts) tan(theta)]\nSegment thickness does not replace qualification."
    fun vessel(part: VesselPart): String {
        val rule = when (part) {
            VesselPart.CYLINDRICAL_SHELL -> "UG-27(c)(1),(2)\nt_h = P R/(S E - 0.6P); t_l = P R/(2 S E + 0.4P)\nt = max(t_h,t_l)"
            VesselPart.ELLIPSOIDAL_HEAD_2_1 -> "UG-32(d) / Appendix 1-4\nt = P D K/(2 S E - 0.2P)\nK = [2 + (D/2h)^2]/6"
            VesselPart.TORISPHERICAL_HEAD -> "UG-32(e) / Appendix 1-4(d)\nt = P L M/(2 S E - 0.2P)\nM = [3 + sqrt(L/r)]/4"
            VesselPart.HEMISPHERICAL_HEAD -> "UG-32(f)\nt = P L/(2 S E - 0.2P)"
            VesselPart.CONICAL_HEAD -> "UG-32(g)\nt = P D/[2 cos(alpha) (S E - 0.6P)]"
        }
        return "ASME BPVC VIII Division 1, internal pressure\n$rule\nt_order = max(t, 1.5 mm) + CA"
    }
}
