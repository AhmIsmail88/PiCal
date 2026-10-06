package com.ahmedismail.flowtrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AddEntryUiEvent {
    data class Blocked(val message: String) : AddEntryUiEvent()
    data object Saved : AddEntryUiEvent()
}

class AddEntryViewModel(private val repository: FlowTrackRepository) : ViewModel() {

    fun tasks(projectId: Long) = repository.observeTasks(projectId)
    fun areas(projectId: Long) = repository.observeAreas(projectId)
    fun team(projectId: Long) = repository.observeTeam(projectId)

    private val _event = MutableStateFlow<AddEntryUiEvent?>(null)
    val event: StateFlow<AddEntryUiEvent?> = _event.asStateFlow()

    fun clearEvent() { _event.value = null }

    /** Pipe Inches = Diameter x Joint Quantity (spec §5), computed live for the result band. */
    fun computePipeInches(diameter: Double?, jointQuantity: Int?, unit: DiameterUnit = DiameterUnit.INCH): Double =
        com.ahmedismail.flowtrack.util.ProgressMath.pipeInches(diameter?.takeIf { it.isFinite() && it > 0 } ?: 0.0, unit, jointQuantity?.coerceAtLeast(0) ?: 0)

    fun submit(
        projectId: Long,
        task: TaskItem,
        area: Area,
        dateMillis: Long,
        diameter: Double,
        unit: DiameterUnit,
        jointQuantity: Int,
        performedBy: TeamMember?,
        supervisor: TeamMember?,
        photoUri: String? = null,
        hoursWorked: Double? = null
    ) {
        viewModelScope.launch {
            val result = repository.submitEntry(
                projectId = projectId, task = task, areaId = area.id, dateMillis = dateMillis,
                diameter = diameter, unit = unit, jointQuantity = jointQuantity,
                performedById = performedBy?.id, supervisorId = supervisor?.id, photoUri = photoUri, hoursWorked = hoursWorked
            )
            _event.value = when (result) {
                is FlowTrackRepository.SaveResult.Success -> AddEntryUiEvent.Saved
                is FlowTrackRepository.SaveResult.Blocked -> AddEntryUiEvent.Blocked(
                    "${result.previousStageJoints}|${result.previousTaskName}"
                )
            }
        }
    }
}
