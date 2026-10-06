package com.ahmedismail.flowtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Single light scheme built entirely from the FlowTrack blueprint/steel
// palette (spec §2.1) — intentionally NOT the default Material color set.
private val FlowTrackColorScheme = lightColorScheme(
    primary = Steel,
    onPrimary = CardWhite,
    primaryContainer = ChipBg,
    onPrimaryContainer = Navy,
    secondary = Amber,
    onSecondary = Navy,
    secondaryContainer = WarnBandBg,
    onSecondaryContainer = Navy,
    tertiary = Green,
    onTertiary = CardWhite,
    error = Orange,
    onError = CardWhite,
    background = Canvas,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = Canvas,
    onSurfaceVariant = Ink2,
    outline = Ink2,
    outlineVariant = Line,
)

@Composable
fun FlowTrackTheme(
    // Dark theme intentionally not branched per spec v1.0 (site-app, single
    // high-contrast light theme for outdoor legibility). Hook left in place
    // for a future v2 dark/navy-first variant.
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = FlowTrackColorScheme,
        typography = FlowTrackTypography,
        content = content
    )
}
