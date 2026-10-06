package com.ahmedismail.flowtrack.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * "Industrial Flat" card system — approved design direction (2026-09, in
 * response to the translucent/blurred "glass" look reading as blotchy and
 * unprofessional on a real device screenshot). Flat white cards, a crisp
 * 1px border, tight corner radius, monospace numerics — reads as
 * engineering software rather than a lifestyle app. No blur, no
 * translucency, no decorative backdrop shapes: those were the actual
 * problems, not just a style preference.
 *
 * Kept the same function names (glassPanel/glassPanelDark) so every
 * screen that already calls them (Dashboard, Projects, Settings tabs,
 * Add Entry, Calculators, dialogs) picks up the new look automatically —
 * no per-screen changes needed. The `alpha` parameters are kept in the
 * signatures for source compatibility with existing call sites but are
 * intentionally unused: everything is fully opaque now.
 */

/** Flat white card — used for cards, dropdown fields, dialogs, list rows. */
fun Modifier.glassPanel(cornerRadius: Dp = 10.dp, alpha: Float = 1f): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(CardWhite)
    .border(1.dp, Line, RoundedCornerShape(cornerRadius))

/** Solid navy card — used for the app bar and the Pipe Inches / calculator result bands. */
fun Modifier.glassPanelDark(cornerRadius: Dp = 0.dp, alpha: Float = 1f): Modifier = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Navy)
    .border(1.dp, Navy2, RoundedCornerShape(cornerRadius))

/** Flat, solid background behind the whole app — no gradient, no blur. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(Canvas))
}

/**
 * A flat white card with a colored left-edge accent stripe — reserved for
 * a single "headline" card per screen (Dashboard's Current Project card)
 * so it reads as sparing emphasis, not a pattern every card gets.
 * Composable rather than a Modifier, since Compose's border() draws all
 * four edges uniformly. Built as a Row with height(IntrinsicSize.Min) —
 * NOT a Box with fillMaxHeight() on the stripe, which is a well-known
 * Compose trap: a fillMaxHeight() child inside a content-wrapping Box
 * fills whatever loose/unbounded height constraint the Box received from
 * ITS OWN parent (e.g. a full LazyColumn viewport), not the height its
 * sibling content actually renders at — IntrinsicSize.Min forces the Row
 * to measure content first so fillMaxHeight() then matches that real
 * resolved height.
 */
@Composable
fun AccentCard(accentColor: Color, cornerRadius: Dp = 10.dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .glassPanel(cornerRadius = cornerRadius)
            .height(IntrinsicSize.Min)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(topStart = cornerRadius, bottomStart = cornerRadius))
                .background(accentColor)
        )
        Box(Modifier.padding(start = 4.dp)) { content() }
    }
}
