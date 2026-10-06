package com.ahmedismail.flowtrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.util.DisplayFormat
import com.ahmedismail.flowtrack.util.toInputDoubleOrNull
import com.ahmedismail.flowtrack.util.toInputIntOrNull
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.ui.components.NumericReadout
import com.ahmedismail.flowtrack.ui.components.PhotoPickerField
import com.ahmedismail.flowtrack.ui.theme.*
import com.ahmedismail.flowtrack.viewmodel.AddEntryUiEvent
import com.ahmedismail.flowtrack.viewmodel.AddEntryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(
    project: Project?,
    viewModel: AddEntryViewModel,
    modifier: Modifier = Modifier
) {
    if (project == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.open_project_first_add), color = Ink2)
        }
        return
    }

    val tasks by remember(project.id, viewModel) { viewModel.tasks(project.id) }.collectAsState(initial = emptyList())
    val areas by remember(project.id, viewModel) { viewModel.areas(project.id) }.collectAsState(initial = emptyList())
    val team by remember(project.id, viewModel) { viewModel.team(project.id) }.collectAsState(initial = emptyList())
    val event by viewModel.event.collectAsState()

    // Every field here is rememberSaveable: switching tabs pops this
    // destination with saveState and restores it later, which only replays
    // rememberSaveable — plain remember() would silently discard a
    // half-typed entry. Room entities are not Bundle-able, so the dropdown
    // selections are held as ids and resolved against the live lists.
    var selectedTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAreaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var diameterText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(DiameterUnit.INCH) }
    var jointQtyText by rememberSaveable { mutableStateOf("") }
    var performedById by rememberSaveable { mutableStateOf<Long?>(null) }
    var supervisorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var blockedMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var hoursWorkedText by rememberSaveable { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val selectedTask = tasks.find { it.id == selectedTaskId } ?: tasks.firstOrNull()
    val selectedArea = areas.find { it.id == selectedAreaId } ?: areas.firstOrNull()
    val performedBy = team.find { it.id == performedById }
    val supervisor = team.find { it.id == supervisorId }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(event) {
        when (val e = event) {
            is AddEntryUiEvent.Saved -> {
                jointQtyText = ""
                diameterText = ""
                blockedMessage = null
                photoUri = null
                hoursWorkedText = ""
                viewModel.clearEvent()
            }
            is AddEntryUiEvent.Blocked -> {
                val (joints, name) = e.message.split("|", limit = 2)
                blockedMessage = context.getString(R.string.validation_error, joints.toInt(), name)
                viewModel.clearEvent()
            }
            null -> Unit
        }
    }

    val diameter = diameterText.toInputDoubleOrNull()
    val jointQty = jointQtyText.toInputIntOrNull()
    val pipeInches = viewModel.computePipeInches(diameter, jointQty, unit)
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }
    val unitLabels = mapOf(
        DiameterUnit.INCH to stringResource(R.string.unit_inch),
        DiameterUnit.MM to stringResource(R.string.unit_mm)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LabeledDropdown(
            label = stringResource(R.string.field_date),
            value = dateFormat.format(Date(dateMillis)),
            onClick = { showDatePicker = true }
        )
        DropdownField(
            label = stringResource(R.string.field_task),
            options = tasks,
            selected = selectedTask,
            display = { it.name },
            onSelect = { selectedTaskId = it.id }
        )
        DropdownField(
            label = stringResource(R.string.field_area),
            options = areas,
            selected = selectedArea,
            display = { it.name },
            onSelect = { selectedAreaId = it.id }
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
        NumberField(label = stringResource(R.string.field_hours_worked), value = hoursWorkedText, onChange = { hoursWorkedText = it })

        Spacer(Modifier.height(6.dp))
        ResultBand(pipeInchesLabel = stringResource(R.string.pipe_inches_result), value = pipeInches)

        blockedMessage?.let {
            Spacer(Modifier.height(10.dp))
            WarnBand(text = it)
        }

        Spacer(Modifier.height(6.dp))
        DropdownField(
            label = stringResource(R.string.field_performed_by),
            options = team,
            selected = performedBy,
            display = { it.name },
            onSelect = { performedById = it.id }
        )
        DropdownField(
            label = stringResource(R.string.field_supervisor),
            options = team,
            selected = supervisor,
            display = { it.name },
            onSelect = { supervisorId = it.id }
        )

        PhotoPickerField(photoUri = photoUri, onPhotoChanged = { photoUri = it })

        if (areas.isEmpty() || team.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.no_areas_or_team_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Ink2
            )
        }

        Spacer(Modifier.height(16.dp))
        val canSave = selectedTask != null && selectedArea != null && diameter != null && diameter.isFinite() && diameter > 0 && jointQty != null && jointQty > 0 &&
            (hoursWorkedText.isBlank() || hoursWorkedText.toInputDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
        Button(
            onClick = {
                val task = selectedTask ?: return@Button
                val area = selectedArea ?: return@Button
                viewModel.submit(
                    projectId = project.id, task = task, area = area, dateMillis = dateMillis,
                    diameter = diameter!!, unit = unit, jointQuantity = jointQty!!,
                    performedBy = performedBy, supervisor = supervisor, photoUri = photoUri,
                    hoursWorked = hoursWorkedText.toInputDoubleOrNull()
                )
            },
            enabled = canSave,
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.save_entry), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save_entry)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(android.R.string.cancel)) } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
internal fun LabeledDropdown(label: String, value: String, onClick: () -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(cornerRadius = 10.dp, alpha = 0.7f)
                .clickable(onClick = onClick)
                .padding(10.dp, 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NumericReadout(text = value, fontSize = 13.5.sp)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Ink2)
        }
    }
}

@Composable
internal fun <T> DropdownField(label: String, options: List<T>, selected: T?, display: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(cornerRadius = 10.dp, alpha = 0.7f)
                    .clickable { expanded = true }
                    .padding(10.dp, 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selected?.let(display) ?: "—", modifier = Modifier.weight(1f).padding(end = 8.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr), color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = Ink2)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (options.isEmpty()) {
                    DropdownMenuItem(text = { Text("—", color = Ink2) }, onClick = { expanded = false }, enabled = false)
                }
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(display(option), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.ContentOrLtr)) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Ink2, modifier = Modifier.padding(bottom = 5.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFontFamily, textDirection = TextDirection.Ltr),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Line, focusedBorderColor = Steel,
                unfocusedContainerColor = CardWhite.copy(alpha = 0.55f), focusedContainerColor = CardWhite.copy(alpha = 0.7f)
            )
        )
    }
}

@Composable
private fun ResultBand(pipeInchesLabel: String, value: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanelDark(cornerRadius = 10.dp)
            .padding(14.dp, 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(pipeInchesLabel, color = CardWhite, style = MaterialTheme.typography.bodyLarge)
        NumericReadout(text = "${DisplayFormat.diameterInches(value)}\"", color = Amber, fontSize = 18.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
internal fun WarnBand(text: String) {
    Text(
        text = text,
        color = WarnBandText,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(WarnBandBg)
            .border(1.dp, Orange, RoundedCornerShape(9.dp))
            .padding(11.dp, 9.dp)
    )
}

