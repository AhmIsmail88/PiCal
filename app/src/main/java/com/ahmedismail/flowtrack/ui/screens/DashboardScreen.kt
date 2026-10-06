package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.ui.components.JointRail
import com.ahmedismail.flowtrack.ui.components.NumericReadout
import com.ahmedismail.flowtrack.ui.components.RailStage
import com.ahmedismail.flowtrack.ui.theme.*
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.viewmodel.DashboardUiState
import com.ahmedismail.flowtrack.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    project: Project?,
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier,
    onProjects: () -> Unit,
    onManageProject: () -> Unit
) {
    if (project == null) {
        EmptyState(modifier, onProjects)
        return
    }
    val stateFlow = remember(project.id, viewModel) { viewModel.uiState(project.id) }
    val state by stateFlow.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AccentCard(accentColor = Steel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.current_project), style = MaterialTheme.typography.labelLarge, color = Steel, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(project.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onManageProject) { Text(stringResource(R.string.manage_project)) }
                        TextButton(onClick = onProjects) { Text(stringResource(R.string.switch_project)) }
                    }
                }
            }
        }
        item { FinishedCard(state) }
        item { StageTotalsCard(state, onManageProject) }
        if (state.jointsByDiameter.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().glassPanel().padding(12.dp)) {
                    Text(
                        stringResource(R.string.joints_by_diameter),
                        style = MaterialTheme.typography.labelLarge,
                        color = Steel,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    state.jointsByDiameter.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val unitLabel = stringResource(if (row.unit == DiameterUnit.INCH) R.string.unit_inch else R.string.unit_mm)
                            NumericReadout(text = "${formatDiameter(row.diameter)} $unitLabel", fontSize = 13.5.sp, color = Ink)
                            NumericReadout(text = stringResource(R.string.joints_count, row.totalJoints), fontSize = 13.5.sp, color = Steel, weight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        items(state.areas) { area ->
            Column(Modifier.fillMaxWidth().glassPanel().padding(12.dp)) {
                Text(
                    stringResource(R.string.workflow_progress, area.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = Steel,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                val stages = state.stagesByArea[area.id].orEmpty().map {
                    RailStage(
                        label = it.task.name,
                        sublabel = stringResource(R.string.stage_inches_value, formatInches(it.pipeInches)),
                        isDone = it.isDone,
                        isCurrent = it.isCurrent
                    )
                }
                JointRail(stages = stages)
            }
        }
        item {
            EntriesSection(project = project, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun EntriesSection(project: Project, state: DashboardUiState, viewModel: DashboardViewModel) {
    val teamFlow = remember(project.id, viewModel) { viewModel.team(project.id) }
    val team by teamFlow.collectAsState(initial = emptyList())
    val editEvent by viewModel.editEvent.collectAsState()
    var editingEntry by remember { mutableStateOf<ProgressEntry?>(null) }
    var deletingEntry by remember { mutableStateOf<ProgressEntry?>(null) }
    LaunchedEffect(editEvent, editingEntry) {
        if (editingEntry == null && editEvent is com.ahmedismail.flowtrack.viewmodel.EntryEditEvent.Saved) viewModel.clearEditEvent()
    }

    if (editingEntry == null && editEvent is com.ahmedismail.flowtrack.viewmodel.EntryEditEvent.Blocked) {
        val blocked = editEvent as com.ahmedismail.flowtrack.viewmodel.EntryEditEvent.Blocked
        AlertDialog(
            onDismissRequest = { viewModel.clearEditEvent() },
            title = { Text(stringResource(R.string.workflow_dependency_title)) },
            text = { Text(stringResource(R.string.workflow_dependency_hint, blocked.previousTaskName, blocked.previousStageJoints)) },
            confirmButton = { TextButton(onClick = { viewModel.clearEditEvent() }) { Text(stringResource(android.R.string.ok)) } }
        )
    }

    Column(Modifier.fillMaxWidth().glassPanel().padding(12.dp)) {
        Text(
            stringResource(R.string.entries_title),
            style = MaterialTheme.typography.labelLarge,
            color = Steel,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (state.entries.isEmpty()) {
            Text(stringResource(R.string.no_entries_yet), style = MaterialTheme.typography.bodySmall, color = Ink2)
        } else {
            val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }
            state.entries.forEach { entry ->
                val task = state.tasks.find { it.id == entry.taskId }
                val area = state.areas.find { it.id == entry.areaId }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${task?.name ?: "—"} · ${area?.name ?: "—"}",
                            style = MaterialTheme.typography.bodyMedium, color = Ink,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        NumericReadout(
                            text = stringResource(
                                R.string.entry_line,
                                dateFormat.format(Date(entry.date)),
                                "${formatDiameter(entry.diameter)} " + stringResource(
                                    if (entry.unit == DiameterUnit.MM) R.string.unit_mm else R.string.unit_inch_short
                                ),
                                entry.jointQuantity.toString()
                            ),
                            fontSize = 11.sp, color = Ink2
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { if (task != null) editingEntry = entry }, enabled = task != null) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_entry), tint = Steel)
                        }
                        IconButton(onClick = { deletingEntry = entry }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_label), tint = Orange)
                        }
                    }
                }
            }
        }
    }

    editingEntry?.let { entry ->
        val task = state.tasks.find { it.id == entry.taskId }
        val area = state.areas.find { it.id == entry.areaId }
        if (task != null && area != null) {
            EditEntryDialog(
                entry = entry, task = task, area = area, team = team,
                editEvent = editEvent,
                onClearEvent = { viewModel.clearEditEvent() },
                onDismiss = { editingEntry = null },
                onSave = { updated -> viewModel.updateEntry(updated, task) }
            )
        }
    }

    deletingEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { deletingEntry = null },
            title = { Text(stringResource(R.string.delete_entry_confirm_title)) },
            text = { Text(stringResource(R.string.delete_entry_confirm_message, entry.jointQuantity)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteEntry(entry); deletingEntry = null }) {
                    Text(stringResource(R.string.delete_label), color = Orange)
                }
            },
            dismissButton = { TextButton(onClick = { deletingEntry = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun FinishedCard(state: DashboardUiState) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Navy).padding(18.dp)) {
        Text(stringResource(R.string.fully_finished), color = CardWhite, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        NumericReadout(formatInches(state.totalPipeInches), color = CardWhite, fontSize = 36.sp, weight = FontWeight.Bold)
        Text(stringResource(R.string.final_stage_inches, state.stageTotals.lastOrNull()?.task?.name ?: "—"),
            color = NavyMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { state.percentComplete / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Amber, trackColor = Navy2)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.joints_count, state.totalJoints), color = CardWhite, style = MaterialTheme.typography.bodySmall)
            NumericReadout("${state.percentComplete}%", color = Amber, fontSize = 16.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.recorded_inches_percent_hint), color = NavyMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StageTotalsCard(state: DashboardUiState, onManageProject: () -> Unit) {
    Column(Modifier.fillMaxWidth().glassPanel().padding(16.dp)) {
        Text(stringResource(R.string.stage_inches_title), color = Ink, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.stage_inches_hint), color = Ink2, style = MaterialTheme.typography.bodySmall)
        if (state.stageTotals.isEmpty()) {
            TextButton(onClick = onManageProject) { Text(stringResource(R.string.setup_workflow)) }
        }
        val scope = state.stageTotals.firstOrNull()?.pipeInches ?: 0.0
        state.stageTotals.forEachIndexed { index, stage ->
            val final = index == state.stageTotals.lastIndex
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(if (final) Navy else ChipBg), contentAlignment = Alignment.Center) {
                    NumericReadout("${index + 1}", color = if (final) CardWhite else Steel, fontSize = 13.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(stage.task.name, style = MaterialTheme.typography.bodyMedium, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (final) stringResource(R.string.final_stage_label) else stringResource(R.string.joints_count, stage.joints), color = Ink2, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    NumericReadout(formatInches(stage.pipeInches), color = if (final) Green else Steel, fontSize = 20.sp, weight = FontWeight.SemiBold)
                    Text(stringResource(R.string.pipe_inches_unit), color = Ink2, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { if (scope > 0) (stage.pipeInches / scope).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = if (final) Green else Steel, trackColor = ChipBg)
        }
    }
}

private fun formatInches(value: Double): String = DisplayFormat.diameterInches(value)
@Composable
private fun EmptyState(modifier: Modifier, onProjects: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.dashboard_empty_title), style = MaterialTheme.typography.bodyLarge, color = Ink)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.dashboard_empty_hint), style = MaterialTheme.typography.bodySmall, color = Ink2)
        Button(onClick = onProjects, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.nav_projects)) }
    }
}

/** Trims a trailing ".0" so whole-number diameters (the common case) read as "16" not "16.0". */
private fun formatDiameter(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()



