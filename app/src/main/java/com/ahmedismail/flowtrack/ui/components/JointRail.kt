package com.ahmedismail.flowtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmedismail.flowtrack.ui.theme.*

data class RailStage(val label: String, val sublabel: String, val isDone: Boolean, val isCurrent: Boolean)

/**
 * Spec §2.3 "the joint rail": workflow stages drawn as a vertical rail of
 * circular joint nodes connected by segments, reusing the pipe-joint concept
 * the whole app is built around instead of a generic progress bar.
 *
 *  - empty node   = not started
 *  - filled steel = complete
 *  - amber node   = current stage
 *  - segment fills solid once the upstream stage is 100% complete
 */
@Composable
fun JointRail(stages: List<RailStage>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        stages.forEachIndexed { index, stage ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JointNode(done = stage.isDone, current = stage.isCurrent)
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stage.isDone || stage.isCurrent) Ink else Ink2,
                    modifier = Modifier.weight(1f)
                )
                Text(text = stage.sublabel, style = MaterialTheme.typography.labelSmall, fontFamily = MonoFontFamily, color = Ink2)
            }
            if (index != stages.lastIndex) {
                Segment(done = stage.isDone)
            }
        }
    }
}

@Composable
private fun JointNode(done: Boolean, current: Boolean) {
    val fill = when { done -> Steel; current -> Amber; else -> Color.White }
    val border = if (current) Amber else Steel
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, border, CircleShape)
    )
}

@Composable
private fun Segment(done: Boolean) {
    Box(
        modifier = Modifier
            .padding(start = 7.dp)
            .width(2.dp)
            .height(20.dp)
            .background(if (done) Steel else Line)
    )
}
