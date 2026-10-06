package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmedismail.flowtrack.data.entity.Project

/** Compatibility entry point; all calculation and validation live in one screen. */
@Deprecated("Use CalculatorsScreen")
@Suppress("UNUSED_PARAMETER")
@Composable
fun PipeThicknessScreen(project: Project?, modifier: Modifier = Modifier) {
    CalculatorsScreen(modifier)
}
