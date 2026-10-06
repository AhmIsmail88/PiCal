package com.ahmedismail.flowtrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.ui.nav.Destination
import com.ahmedismail.flowtrack.ui.theme.*

private data class NavEntry(val destination: Destination, val icon: androidx.compose.ui.graphics.vector.ImageVector, val labelRes: Int)

private val bottomNavItems = listOf(
    NavEntry(Destination.Dashboard, Icons.Filled.Home, R.string.nav_home),
    NavEntry(Destination.Projects, Icons.Filled.Folder, R.string.nav_projects),
    NavEntry(Destination.AddEntry, Icons.Filled.AddCircle, R.string.nav_add),
    NavEntry(Destination.Calculators, Icons.Filled.Calculate, R.string.nav_calculators),
    NavEntry(Destination.Reports, Icons.Filled.Description, R.string.nav_reports),
)

@Composable
fun FlowTrackScaffold(
    screenTitleRes: Int,
    currentRoute: String,
    onNavigate: (Destination) -> Unit,
    onToggleLanguage: () -> Unit,
    onAbout: (() -> Unit)? = null,
    // Non-null only on screens reached by "pushing" on top of the 5 main
    // tabs (project management) — shows a back arrow
    // instead of the app logo so there's always an unambiguous way out,
    // regardless of whether any project is open or which tab you came from.
    onBack: (() -> Unit)? = null,
    onManageProject: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = { FlowTrackAppBar(screenTitleRes, onToggleLanguage, onBack, onManageProject, onAbout) },
        bottomBar = {
            Column {
                FlatBottomNav(currentRoute = currentRoute, onNavigate = onNavigate)
                SignatureFooter()
            }
        },
        containerColor = Canvas,
        // We handle status/navigation-bar insets manually inside the app bar
        // and footer below (so their backgrounds extend fully behind the
        // system bars while their content sits clear of them). Leaving
        // Scaffold's default safe-drawing inset on top of that would double
        // the padding and push content down twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            GlassBackdrop()
            content(Modifier.padding(padding))
        }
    }
}

@Composable
private fun FlowTrackAppBar(
    screenTitleRes: Int,
    onToggleLanguage: () -> Unit,
    onBack: (() -> Unit)?,
    onManageProject: (() -> Unit)?,
    onAbout: (() -> Unit)?
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Navy)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button), tint = CardWhite)
                }
            } else {
                Image(painterResource(R.drawable.pical_brand_mark), contentDescription = null, modifier = Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.app_name), color = CardWhite, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(stringResource(screenTitleRes), color = NavyMuted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (onManageProject != null) IconButton(onClick = onManageProject) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.manage_project), tint = CardWhite)
            }
            if (onAbout != null) IconButton(onClick = onAbout) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about_title), tint = CardWhite)
            }
            TextButton(onClick = onToggleLanguage) {
                Text(stringResource(R.string.language_switch), color = CardWhite, style = MaterialTheme.typography.labelMedium)
            }
        }
        // Thin amber strip along the header's bottom edge — ties it to the
        // safety-signage accent used elsewhere (stat card tops, the Add
        // FAB) without breaking the flat, solid-color design.
        Box(Modifier.fillMaxWidth().height(3.dp).background(Amber))
    }
}

@Composable
private fun FlatBottomNav(currentRoute: String, onNavigate: (Destination) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardWhite)
            .border(width = 1.dp, color = Line)
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.destination.route
            if (item.destination == Destination.AddEntry) {
                // The primary field action — an engineer on site adds
                // entries far more often than they touch any other tab —
                // gets a raised amber FAB instead of the same flat
                // treatment as the other four, so it reads as "the" button.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp).clickable { onNavigate(item.destination) }
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(46.dp)
                            .shadow(4.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Amber),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = null, tint = Navy, modifier = Modifier.size(26.dp))
                    }
                    Text(
                        stringResource(item.labelRes),
                        fontSize = 10.sp,
                        color = Navy,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ChipBg else CardWhite)
                        .clickable { onNavigate(item.destination) }
                        .padding(top = 4.dp)
                ) {
                    Icon(item.icon, contentDescription = null, tint = if (selected) Steel else Ink2)
                    Text(stringResource(item.labelRes), fontSize = 10.sp, color = if (selected) Steel else Ink2)
                }
            }
        }
    }
}

@Composable
fun SignatureFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Canvas)
            .border(width = 1.dp, color = Line)
            // Pads the actual text clear of the gesture/navigation bar while
            // the background above still extends all the way down.
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Text(
            text = stringResource(R.string.signature),
            textAlign = TextAlign.Center,
            fontFamily = MonoFontFamily,
            fontSize = 9.5.sp,
            color = Ink2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        )
    }
}


