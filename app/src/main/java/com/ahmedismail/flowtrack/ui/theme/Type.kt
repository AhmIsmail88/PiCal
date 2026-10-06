package com.ahmedismail.flowtrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.R

/**
 * Spec §2.2's original picks (Barlow Semi Condensed / IBM Plex Sans /
 * IBM Plex Mono) — actually bundled now, not a system-font placeholder.
 *
 * IBM Plex Sans was swapped for plain **Barlow** (same width, matching
 * family as Barlow Semi Condensed) because IBM Plex Sans has moved to a
 * variable-font-only distribution with no static per-weight .ttf files to
 * fetch; Barlow's regular width pairs with Barlow Semi Condensed as one
 * coherent type family instead of two unrelated ones, which reads more
 * intentional anyway. IBM Plex Mono kept its original static files.
 *
 * A real bundled font — not a generic alias like FontFamily.SansSerif or
 * even FontFamily.Default — is the only fully reliable fix for a bug seen
 * on a real test device where BOTH of those aliases were resolving to a
 * decorative serif italic via some device/OEM-level font substitution;
 * bundled font files bypass that substitution entirely since they don't
 * go through named-alias lookup at all.
 *
 * Arabic text isn't covered by any of these Latin-only fonts, so it falls
 * back to the system's own Arabic-capable font automatically (standard
 * Android multi-script fallback) — a normal, clean render, just not a
 * custom-matched one; adding IBM Plex Sans Arabic later is possible but
 * Compose's basic FontFamily(Font...) list doesn't script-switch on its
 * own, so it would need a per-locale FontFamily swap, not just more files.
 */
val DisplayFontFamily = FontFamily(
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold)
)
val BodyFontFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold)
)
val MonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
)

val FlowTrackTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.3.sp
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = 0.3.sp
    ),
    titleSmall = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp
    ),
    // Numeric / data readouts: apply MonoFontFamily directly where displaying
    // diameters, joint counts, Pipe Inches, dates — see NumericReadout composable.
)
