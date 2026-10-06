package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.util.toInputDoubleOrNull
import com.ahmedismail.flowtrack.util.toInputIntOrNull
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.ui.components.PhotoPickerField
import com.ahmedismail.flowtrack.viewmodel.EntryEditEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full edit of an existing progress entry — everything except Task and Area
 * (those define which stage/area the entry belongs to and reworking the
 * workflow-validation cascade across a task/area change is out of scope;
 * delete + re-add covers that rare case). Re-runs the same §5.1 validation
 * as a new entry when Diameter or Joint Quantity change, excluding this
 * entry's own current contribution from the running total (see
 * FlowTrackRepository.updateEntry / WorkflowValidator).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryDialog(
    entry: ProgressEntry,
    task: TaskItem,
    area: Area,
    team: List<TeamMember>,
    editEvent: EntryEditEvent?,
    onClearEvent: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProgressEntry) -> Unit
) {
    var dateMillis by remember { mutableStateOf(entry.date) }
    var diameterText by remember { mutableStateOf(formatNumber(entry.diameter)) }
    var unit by remember { mutableStateOf(entry.unit) }
    var jointQtyText by remember { mutableStateOf(entry.jointQuantity.toString()) }
    var performedBy by remember { mutableStateOf(team.find { it.id == entry.performedById }) }
    var supervisor by remember { mutableStateOf(team.find { it.id == entry.supervisorId }) }
    var hoursText by remember { mutableStateOf(entry.hoursWorked?.let { formatNumber(it) } ?: "") }
    var photoUri by remember { mutableStateOf(entry.photoUri) }
    var showDatePicker by remember { mutableStateOf(false) }
    var blockedMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    LaunchedEffect(editEvent) {
        when (val e = editEvent) {
            is EntryEditEvent.Saved -> { onClearEvent(); onDismiss() }
            is EntryEditEvent.Blocked -> {
                blockedMessage = context.getString(R.string.validation_error, e.previousStageJoints, e.previousTaskName)
                onClearEvent()
            }
            null -> Unit
        }
    }

    val diameter = diameterText.toInputDoubleOrNull()
    val jointQty = jointQtyText.toInputIntOrNull()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }
    val unitLabels = mapOf(
        DiameterUnit.INCH to stringResource(R.string.unit_inch),
        DiameterUnit.MM to stringResource(R.string.unit_mm)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.edit_entry)} — ${task.name} · ${area.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LabeledDropdown(
                    label = stringResource(R.string.field_date),
                    value = dateFormat.format(Date(dateMillis)),
                    onClick = { showDatePicker = true }
                )
                NumberField(label = stringResource(R.string.field_diameter), value = diameterText, onChange = { diameterText = it })
                DropdownField(
                    label = stringResource(R.string.field_unit),
                    options = listOf(DiameterUnit.INCH, DiameterUnit.MM),
                    selected = unit,
                    display = { unitLabels.getValue(it) },
                    onSelect = { unit = it }
                )
                NumberField(label = stringResource(R.string.field_joint_qty), value = jointQtyText, onChange = { jointQtyText = it })
                NumberField(label = stringResource(R.string.field_hours_worked), value = hoursText, onChange = { hoursText = it })
                blockedMessage?.let {
                    WarnBand(text = it)
                    Spacer(Modifier.height(8.dp))
                }
                DropdownField(
                    label = stringResource(R.string.field_performed_by),
                    options = team,
                    selected = performedBy,
                    display = { it.name },
                    onSelect = { performedBy = it }
                )
                DropdownField(
                    label = stringResource(R.string.field_supervisor),
                    options = team,
                    selected = supervisor,
                    display = { it.name },
                    onSelect = { supervisor = it }
                )
                PhotoPickerField(photoUri = photoUri, onPhotoChanged = { photoUri = it })
            }
        },
        confirmButton = {
            val canSave = diameter != null && diameter.isFinite() && diameter > 0 && jointQty != null && jointQty > 0 &&
                (hoursText.isBlank() || hoursText.toInputDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        entry.copy(
                            date = dateMillis, diameter = diameter!!, unit = unit, jointQuantity = jointQty!!,
                            performedById = performedBy?.id, supervisorId = supervisor?.id,
                            photoUri = photoUri, hoursWorked = hoursText.toInputDoubleOrNull()
                        )
                    )
                }
            ) { Text(stringResource(R.string.save_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }
}

/** Trims a trailing ".0" so whole numbers read as "16" not "16.0" when pre-filling a field. */
private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

