package com.ahmedismail.flowtrack.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.ui.theme.Ink
import com.ahmedismail.flowtrack.ui.theme.MonoFontFamily

/** Every measured number (diameters, joint counts, Pipe Inches, dates) reads via IBM Plex Mono — spec §2.2. */
@Composable
fun NumericReadout(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    fontSize: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Medium
) {
    Text(text = text, modifier = modifier, color = color, fontFamily = MonoFontFamily, fontSize = fontSize, fontWeight = weight,
        style = TextStyle(textDirection = TextDirection.ContentOrLtr))
}
