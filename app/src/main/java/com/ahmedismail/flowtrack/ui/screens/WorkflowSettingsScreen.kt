package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
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
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.AttendanceRecord
import com.ahmedismail.flowtrack.data.entity.AttendanceStatus
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TaskType
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.data.entity.TeamRole
import com.ahmedismail.flowtrack.ui.components.NumericReadout
import com.ahmedismail.flowtrack.ui.components.TimePickerDialog
import com.ahmedismail.flowtrack.ui.theme.*
import com.ahmedismail.flowtrack.util.DateUtils
import com.ahmedismail.flowtrack.viewmodel.WorkflowSettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SettingsTab { WORKFLOW, AREAS, TEAM, ATTENDANCE }

/** Localized label for a task type — the enum name itself is never shown to the user. */
private fun taskTypeLabelRes(type: TaskType): Int = when (type) {
    TaskType.FABRICATION -> R.string.type_fabrication
    TaskType.WELDING -> R.string.type_welding
    TaskType.INSPECTION -> R.string.type_inspection
    TaskType.TESTING -> R.string.type_testing
    TaskType.INSTALLATION -> R.string.type_installation
    TaskType.FINISHING -> R.string.type_finishing
    TaskType.HANDOVER -> R.string.type_handover
    TaskType.OTHER -> R.string.type_other
}

@Composable
fun WorkflowSettingsScreen(
    project: Project?,
    viewModel: WorkflowSettingsViewModel,
    modifier: Modifier = Modifier
) {
    if (project == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.open_project_first_settings), color = Ink2)
        }
        return
    }

    var tab by rememberSaveable(project.id) { mutableStateOf(SettingsTab.WORKFLOW) }

    Column(modifier = modifier.fillMaxSize().padding(14.dp)) {
        Text(project.name, style = MaterialTheme.typography.titleMedium, color = Navy, modifier = Modifier.padding(bottom = 12.dp))
        SettingsTabRow(tab, onSelect = { tab = it })
        Spacer(Modifier.height(12.dp))
        when (tab) {
            SettingsTab.WORKFLOW -> WorkflowTab(project, viewModel)
            SettingsTab.AREAS -> AreasTab(project, viewModel)
            SettingsTab.TEAM -> TeamTab(project, viewModel)
            SettingsTab.ATTENDANCE -> AttendanceTab(project, viewModel)
        }
    }
}

@Composable
private fun SettingsTabRow(selected: SettingsTab, onSelect: (SettingsTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 12.dp).padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TabChip(stringResource(R.string.tab_workflow), selected == SettingsTab.WORKFLOW) { onSelect(SettingsTab.WORKFLOW) }
        TabChip(stringResource(R.string.tab_areas), selected == SettingsTab.AREAS) { onSelect(SettingsTab.AREAS) }
        TabChip(stringResource(R.string.tab_team), selected == SettingsTab.TEAM) { onSelect(SettingsTab.TEAM) }
        TabChip(stringResource(R.string.tab_attendance), selected == SettingsTab.ATTENDANCE) { onSelect(SettingsTab.ATTENDANCE) }
    }
}

@Composable
private fun RowScope.TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Steel else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) CardWhite else Ink2, style = MaterialTheme.typography.bodyMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------------------------------------------------------------------
// Workflow tab (task list, reorder, add/edit/delete)
// ---------------------------------------------------------------------

private sealed class TaskDialogMode {
    data object Add : TaskDialogMode()
    data class Edit(val task: TaskItem) : TaskDialogMode()
}

@Composable
private fun WorkflowTab(project: Project, viewModel: WorkflowSettingsViewModel) {
    val taskFlow = remember(project.id, viewModel) { viewModel.tasks(project.id) }
    val persisted by taskFlow.collectAsState(initial = emptyList())
    var localTasks by remember { mutableStateOf(persisted) }
    LaunchedEffect(persisted) { localTasks = persisted }
    val orderBlocked by viewModel.orderBlocked.collectAsState()
    LaunchedEffect(orderBlocked) { if (orderBlocked) localTasks = persisted }
    if (orderBlocked) AlertDialog(
        onDismissRequest = viewModel::clearOrderError,
        title = { Text(stringResource(R.string.workflow_dependency_title)) },
        text = { Text(stringResource(R.string.workflow_order_blocked)) },
        confirmButton = { TextButton(onClick = viewModel::clearOrderError) { Text(stringResource(android.R.string.ok)) } }
    )

    var dialogMode by remember { mutableStateOf<TaskDialogMode?>(null) }

    val reorderState = rememberReorderableLazyListState(onMove = { from, to ->
        localTasks = localTasks.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }, onDragEnd = { _, _ -> viewModel.onReordered(localTasks) })

    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.workflow_settings_path),
            style = MaterialTheme.typography.bodySmall,
            color = Ink2,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyColumn(
            state = reorderState.listState,
            modifier = Modifier.weight(1f).reorderable(reorderState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(localTasks, key = { _, t -> t.id }) { index, task ->
                ReorderableItem(reorderState, key = task.id) { _ ->
                    TaskRow(
                        index = index + 1,
                        task = task,
                        dragModifier = Modifier.detectReorderAfterLongPress(reorderState),
                        onEdit = { dialogMode = TaskDialogMode.Edit(task) },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassActionButton(stringResource(R.string.add_task), onClick = { dialogMode = TaskDialogMode.Add })
    }

    dialogMode?.let { mode ->
        val initial = (mode as? TaskDialogMode.Edit)?.task
        TaskDialog(
            initialTask = initial,
            onDismiss = { dialogMode = null },
            onConfirm = { name, type, customTypeLabel ->
                if (initial != null) {
                    viewModel.updateTask(initial.copy(name = name, type = type, extraFieldValue = customTypeLabel))
                } else {
                    viewModel.addTask(project.id, name, type, customTypeLabel)
                }
                dialogMode = null
            }
        )
    }
}

@Composable
private fun TaskRow(index: Int, task: TaskItem, dragModifier: Modifier, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 10.dp).padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Full-size drag handle: reordering is a press-and-hold drag, which
        // needs a far bigger target than the 24dp glyph itself.
        Box(
            modifier = Modifier.size(48.dp).then(dragModifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.DragHandle, contentDescription = stringResource(R.string.drag_to_reorder), tint = Ink2)
        }
        Box(
            modifier = Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Navy2),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = CardWhite, fontFamily = MonoFontFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            task.name,
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            // Tapping the name opens the editor, as does the pencil button.
            modifier = Modifier.weight(1f).clickable(onClick = onEdit)
        )
        Text(
            text = if (task.type == TaskType.OTHER && !task.extraFieldValue.isNullOrBlank()) {
                task.extraFieldValue
            } else {
                stringResource(taskTypeLabelRes(task.type))
            },
            color = Steel,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(ChipBg).padding(7.dp, 3.dp)
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_task), tint = Steel)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_task), tint = Orange)
        }
    }
}

@Composable
private fun TaskDialog(initialTask: TaskItem?, onDismiss: () -> Unit, onConfirm: (name: String, type: TaskType, customTypeLabel: String?) -> Unit) {
    var name by remember { mutableStateOf(initialTask?.name ?: "") }
    var type by remember { mutableStateOf(initialTask?.type ?: TaskType.FABRICATION) }
    var customTypeLabel by remember { mutableStateOf(if (initialTask?.type == TaskType.OTHER) initialTask.extraFieldValue.orEmpty() else "") }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTask != null) stringResource(R.string.edit_task) else stringResource(R.string.add_task).removePrefix("+ ")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.task_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.task_type), style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
                Box {
                    // A plain clickable Row here (not a readOnly OutlinedTextField)
                    // — TextField's own touch handling swallows taps meant to open
                    // the menu, which is why this dropdown previously never opened
                    // and every task silently stayed "Fabrication".
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardWhite)
                            .border(1.dp, Line, RoundedCornerShape(8.dp))
                            .clickable { typeExpanded = true }
                            .padding(12.dp, 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(taskTypeLabelRes(type)), color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Ink2)
                    }
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        TaskType.values().forEach { t ->
                            DropdownMenuItem(
                                text = { Text(stringResource(taskTypeLabelRes(t))) },
                                onClick = { type = t; typeExpanded = false }
                            )
                        }
                    }
                }
                if (type == TaskType.OTHER) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customTypeLabel,
                        onValueChange = { customTypeLabel = it },
                        label = { Text(stringResource(R.string.custom_type_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val label = if (type == TaskType.OTHER) customTypeLabel.trim().ifBlank { null } else null
                    onConfirm(name.trim(), type, label)
                }
            }) { Text(stringResource(R.string.save_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------------
// Areas tab
// ---------------------------------------------------------------------

@Composable
private fun AreasTab(project: Project, viewModel: WorkflowSettingsViewModel) {
    val areas by viewModel.areas(project.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(areas, key = { it.id }) { area ->
                SimpleRow(label = area.name, onDelete = { viewModel.deleteArea(area) })
            }
            if (areas.isEmpty()) {
                item { EmptyHint(stringResource(R.string.no_areas_yet)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassActionButton(stringResource(R.string.add_area), onClick = { showAdd = true })
    }

    if (showAdd) {
        SingleFieldDialog(
            title = stringResource(R.string.add_area).removePrefix("+ "),
            fieldLabel = stringResource(R.string.area_name),
            onDismiss = { showAdd = false },
            onConfirm = { name -> viewModel.addArea(project.id, name); showAdd = false }
        )
    }
}

// ---------------------------------------------------------------------
// Team tab
// ---------------------------------------------------------------------

@Composable
private fun TeamTab(project: Project, viewModel: WorkflowSettingsViewModel) {
    val team by viewModel.team(project.id).collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var exportingMember by remember { mutableStateOf<TeamMember?>(null) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(team, key = { it.id }) { member ->
                TeamMemberRow(
                    member = member,
                    onExport = { exportingMember = member },
                    onDelete = { viewModel.deleteTeamMember(member) }
                )
            }
            if (team.isEmpty()) {
                item { EmptyHint(stringResource(R.string.no_team_yet)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassActionButton(stringResource(R.string.add_member), onClick = { showAdd = true })
    }

    if (showAdd) {
        AddMemberDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, role, customLabel -> viewModel.addTeamMember(project.id, name, role, customLabel); showAdd = false }
        )
    }

    exportingMember?.let { member ->
        EmployeeReportDialog(
            project = project,
            member = member,
            viewModel = viewModel,
            onDismiss = { exportingMember = null }
        )
    }
}

@Composable
private fun TeamMemberRow(member: TeamMember, onExport: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 10.dp).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${member.name}  ·  ${roleLabel(member)}", style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            IconButton(onClick = onExport) {
                Icon(Icons.Filled.Description, contentDescription = stringResource(R.string.export_employee_report), tint = Steel)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_label), tint = Orange)
            }
        }
    }
}

/** Shows the member's own role label, falling back to their typed-in job title when role == OTHER. */
@Composable
private fun roleLabel(member: TeamMember): String =
    if (member.role == TeamRole.OTHER && !member.customRoleLabel.isNullOrBlank()) member.customRoleLabel else roleLabel(member.role)

@Composable
private fun roleLabel(role: TeamRole): String = when (role) {
    TeamRole.ENGINEER -> stringResource(R.string.role_engineer)
    TeamRole.SUPERVISOR -> stringResource(R.string.role_supervisor)
    TeamRole.WELDER -> stringResource(R.string.role_welder)
    TeamRole.INSPECTOR -> stringResource(R.string.role_inspector)
    TeamRole.OTHER -> stringResource(R.string.role_other)
}

@Composable
private fun AddMemberDialog(onDismiss: () -> Unit, onConfirm: (String, TeamRole, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(TeamRole.ENGINEER) }
    var customLabel by remember { mutableStateOf("") }
    var roleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_member).removePrefix("+ ")) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.member_name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.member_role), style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardWhite)
                            .border(1.dp, Line, RoundedCornerShape(8.dp))
                            .clickable { roleExpanded = true }
                            .padding(12.dp, 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(roleLabel(role), color = Ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Ink2)
                    }
                    DropdownMenu(expanded = roleExpanded, onDismissRequest = { roleExpanded = false }) {
                        TeamRole.values().forEach { r ->
                            DropdownMenuItem(text = { Text(roleLabel(r)) }, onClick = { role = r; roleExpanded = false })
                        }
                    }
                }
                // "Other" reveals a free-text field so a job title not in the
                // fixed list (Engineer/Supervisor/Welder/Inspector) can still
                // be entered and is shown verbatim everywhere after.
                if (role == TeamRole.OTHER) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text(stringResource(R.string.custom_role_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    val label = if (role == TeamRole.OTHER) customLabel.trim().ifBlank { null } else null
                    onConfirm(name.trim(), role, label)
                }
            }) { Text(stringResource(R.string.save_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

/**
 * Date-range picker + export action for one employee. Filters the
 * project's entries to this member's "Performed By" rows within the chosen
 * period, builds the PDF via EmployeeReportGenerator, then shares it and
 * (permission allowing) posts the same "report ready" notification the
 * project-wide report uses.
 */
@Composable
private fun EmployeeReportDialog(
    project: Project,
    member: TeamMember,
    viewModel: WorkflowSettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var range by remember { mutableStateOf(com.ahmedismail.flowtrack.ui.components.DateRange()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US) }
    fun periodLabel(r: com.ahmedismail.flowtrack.ui.components.DateRange): String? = when {
        r.startMillis == null && r.endMillis == null -> null
        else -> {
            val from = r.startMillis?.let { dateFormat.format(java.util.Date(it)) } ?: "…"
            val to = r.endMillis?.let { dateFormat.format(java.util.Date(it)) } ?: "…"
            "$from – $to"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.export_employee_report)} — ${member.name}") },
        text = {
            Column {
                com.ahmedismail.flowtrack.ui.components.DateRangeField(range = range, onChange = { range = it }, modifier = Modifier.fillMaxWidth())
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.export_failed, it), color = Orange, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    working = true
                    scope.launch {
                        try {
                            val allEntries = viewModel.entries(project.id).first()
                            val entries = allEntries.filter { it.performedById == member.id && range.contains(it.date) }
                            val tasks = viewModel.tasks(project.id).first()
                            val areas = viewModel.areas(project.id).first()
                            val allAttendance = viewModel.attendanceForMember(member.id).first()
                            val attendance = allAttendance.filter { range.contains(it.date) }
                            val outputDir = java.io.File(context.getExternalFilesDir(null), "reports")
                            val file = com.ahmedismail.flowtrack.pdf.EmployeeReportGenerator.generate(
                                project = project, member = member, entries = entries,
                                tasks = tasks, areas = areas, attendanceRecords = attendance,
                                periodLabel = periodLabel(range), outputDir = outputDir
                            )
                            com.ahmedismail.flowtrack.pdf.ReportNotifier.notify(context, file)
                            context.startActivity(com.ahmedismail.flowtrack.pdf.ReportNotifier.shareIntent(context, file))
                            working = false
                            onDismiss()
                        } catch (t: Throwable) {
                            error = t.message
                            working = false
                        }
                    }
                }
            ) { Text(stringResource(R.string.export_pdf)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

// ---------------------------------------------------------------------
// Attendance tab
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttendanceTab(project: Project, viewModel: WorkflowSettingsViewModel) {
    var selectedDay by remember { mutableStateOf(DateUtils.startOfDay(System.currentTimeMillis())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val team by viewModel.team(project.id).collectAsState(initial = emptyList())
    val dayRecords by viewModel.attendanceForDate(project.id, selectedDay).collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        LabeledDropdown(
            label = stringResource(R.string.field_date),
            value = dateFormat.format(Date(selectedDay)),
            onClick = { showDatePicker = true }
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(team, key = { it.id }) { member ->
                val record = dayRecords.find { it.teamMemberId == member.id }
                AttendanceRow(
                    member = member,
                    record = record,
                    onStatusChange = { status ->
                        val base = record ?: AttendanceRecord(
                            projectId = project.id, teamMemberId = member.id, date = selectedDay, status = status
                        )
                        val checkIn = when {
                            status == AttendanceStatus.ABSENT -> null
                            base.checkInMillis == null -> System.currentTimeMillis()
                            else -> base.checkInMillis
                        }
                        val checkOut = if (status == AttendanceStatus.ABSENT) null else base.checkOutMillis
                        viewModel.setAttendance(base.copy(status = status, checkInMillis = checkIn, checkOutMillis = checkOut))
                    },
                    onCheckOutNow = { record?.let { viewModel.setAttendance(it.copy(checkOutMillis = System.currentTimeMillis())) } },
                    onEditCheckIn = { hour, minute ->
                        record?.let { viewModel.setAttendance(it.copy(checkInMillis = DateUtils.combineDateAndTime(selectedDay, hour, minute))) }
                    },
                    onEditCheckOut = { hour, minute ->
                        record?.let { viewModel.setAttendance(it.copy(checkOutMillis = DateUtils.combineDateAndTime(selectedDay, hour, minute))) }
                    }
                )
            }
            if (team.isEmpty()) {
                item { EmptyHint(stringResource(R.string.no_attendance_team_yet)) }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = selectedDay)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selectedDay = DateUtils.startOfDay(it) }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun AttendanceRow(
    member: TeamMember,
    record: AttendanceRecord?,
    onStatusChange: (AttendanceStatus) -> Unit,
    onCheckOutNow: () -> Unit,
    onEditCheckIn: (Int, Int) -> Unit,
    onEditCheckOut: (Int, Int) -> Unit
) {
    var editingCheckIn by remember { mutableStateOf(false) }
    var editingCheckOut by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

    Column(Modifier.fillMaxWidth().glassPanel(cornerRadius = 10.dp).padding(12.dp)) {
        Text("${member.name}  ·  ${roleLabel(member)}", style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusChip(stringResource(R.string.status_present), record?.status == AttendanceStatus.PRESENT, Green) { onStatusChange(AttendanceStatus.PRESENT) }
            StatusChip(stringResource(R.string.status_late), record?.status == AttendanceStatus.LATE, Amber) { onStatusChange(AttendanceStatus.LATE) }
            StatusChip(stringResource(R.string.status_absent), record?.status == AttendanceStatus.ABSENT, Orange) { onStatusChange(AttendanceStatus.ABSENT) }
        }
        // Only Present/Late carry check-in/out — Absent has nothing to time.
        if (record != null && record.status != AttendanceStatus.ABSENT) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TimeField(
                    label = stringResource(R.string.check_in_label),
                    value = record.checkInMillis?.let { timeFormat.format(Date(it)) },
                    onClick = { editingCheckIn = true }
                )
                if (record.checkOutMillis == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Steel)
                            .clickable(onClick = onCheckOutNow)
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.check_out_action), color = CardWhite, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    TimeField(
                        label = stringResource(R.string.check_out_label),
                        value = timeFormat.format(Date(record.checkOutMillis)),
                        onClick = { editingCheckOut = true }
                    )
                }
            }
        }
    }

    if (editingCheckIn) {
        val base = record?.checkInMillis ?: System.currentTimeMillis()
        TimePickerDialog(
            initialHour = DateUtils.hourOf(base), initialMinute = DateUtils.minuteOf(base),
            onDismiss = { editingCheckIn = false },
            onConfirm = { h, m -> onEditCheckIn(h, m); editingCheckIn = false }
        )
    }
    if (editingCheckOut) {
        val base = record?.checkOutMillis ?: System.currentTimeMillis()
        TimePickerDialog(
            initialHour = DateUtils.hourOf(base), initialMinute = DateUtils.minuteOf(base),
            onDismiss = { editingCheckOut = false },
            onConfirm = { h, m -> onEditCheckOut(h, m); editingCheckOut = false }
        )
    }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) activeColor else CardWhite.copy(alpha = 0.5f))
            .border(1.dp, if (selected) activeColor else Line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) CardWhite else Ink, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TimeField(label: String, value: String?, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).heightIn(min = 44.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Ink2)
        NumericReadout(text = value ?: "--:--", fontSize = 13.sp, color = Ink)
    }
}

// ---------------------------------------------------------------------

/** Short hint shown in place of a tab's list while it has no rows yet. */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Ink2,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun SimpleRow(label: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().glassPanel(cornerRadius = 10.dp).padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_label), tint = Orange)
        }
    }
}

@Composable
private fun GlassActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Steel.copy(alpha = 0.15f))
            .border(1.5.dp, Steel, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Steel, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SingleFieldDialog(title: String, fieldLabel: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                label = { Text(fieldLabel) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) { Text(stringResource(R.string.save_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

