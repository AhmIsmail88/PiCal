package com.ahmedismail.flowtrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import com.ahmedismail.flowtrack.util.ProgressSummary
import com.ahmedismail.flowtrack.util.StageTotal
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StageProgress(val task: TaskItem, val percentComplete: Int, val isCurrent: Boolean, val isDone: Boolean, val pipeInches: Double)

/** One row of the Dashboard's "Joints by Diameter" card — e.g. 16" (Inch): 3 joints. Final-stage only — see buildState(). */
data class DiameterBreakdown(val diameter: Double, val unit: DiameterUnit, val totalJoints: Long)

data class DashboardUiState(
    val totalPipeInches: Double = 0.0,
    val totalJoints: Long = 0,
    val stageCount: Int = 0,
    val stageTotals: List<StageTotal> = emptyList(),
    val percentComplete: Int = 0,
    val areas: List<Area> = emptyList(),
    val stagesByArea: Map<Long, List<StageProgress>> = emptyMap(),
    val jointsByDiameter: List<DiameterBreakdown> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val entries: List<ProgressEntry> = emptyList()
)

sealed class EntryEditEvent {
    data class Blocked(val previousTaskName: String, val previousStageJoints: Int) : EntryEditEvent()
    data object Saved : EntryEditEvent()
}

class DashboardViewModel(private val repository: FlowTrackRepository) : ViewModel() {

    fun uiState(projectId: Long): StateFlow<DashboardUiState> =
        combine(
            repository.observeTasks(projectId),
            repository.observeAreas(projectId),
            repository.observeEntries(projectId)
        ) { tasks, areas, entries ->
            buildState(tasks, areas, entries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    /** For the Performed By / Supervisor pickers in the entry-edit dialog. */
    fun team(projectId: Long) = repository.observeTeam(projectId)

    private val _editEvent = MutableStateFlow<EntryEditEvent?>(null)
    val editEvent: StateFlow<EntryEditEvent?> = _editEvent.asStateFlow()
    fun clearEditEvent() { _editEvent.value = null }

    /** Task and Area are intentionally not editable — see FlowTrackRepository.updateEntry. */
    fun updateEntry(entry: ProgressEntry, task: TaskItem) {
        viewModelScope.launch {
            val result = repository.updateEntry(entry, task)
            _editEvent.value = when (result) {
                is FlowTrackRepository.SaveResult.Success -> EntryEditEvent.Saved
                is FlowTrackRepository.SaveResult.Blocked -> EntryEditEvent.Blocked(result.previousTaskName, result.previousStageJoints)
            }
        }
    }

    fun deleteEntry(entry: ProgressEntry) {
        viewModelScope.launch {
            _editEvent.value = when (val result = repository.deleteEntry(entry)) {
                is FlowTrackRepository.SaveResult.Success -> EntryEditEvent.Saved
                is FlowTrackRepository.SaveResult.Blocked -> EntryEditEvent.Blocked(result.previousTaskName, result.previousStageJoints)
            }
        }
    }

    private fun buildState(
        tasks: List<TaskItem>,
        areas: List<Area>,
        entries: List<ProgressEntry>
    ): DashboardUiState {
        val summary = ProgressSummary.calculate(tasks, entries)
        val ordered = summary.stages.map { it.task }
        val finalStageEntries = entries.filter { it.taskId == summary.finished?.task?.id }
        val jointsByDiameter = finalStageEntries.groupBy { it.diameter to it.unit }
            .map { (key, rows) -> DiameterBreakdown(key.first, key.second, rows.sumOf { it.jointQuantity.toLong() }) }
            .sortedByDescending { com.ahmedismail.flowtrack.util.ProgressMath.diameterInches(it.diameter, it.unit) }
        val stagesByArea = areas.associate { area ->
            val areaSummary = ProgressSummary.calculate(ordered, entries.filter { it.areaId == area.id })
            val scope = areaSummary.stages.firstOrNull()?.pipeInches ?: 0.0
            val stages = areaSummary.stages.map { stage ->
                val pct = if (scope > 0) (stage.pipeInches * 100 / scope).toInt().coerceIn(0, 100) else 0
                StageProgress(stage.task, pct, false, pct >= 100, stage.pipeInches)
            }
            val currentIndex = stages.indexOfFirst { !it.isDone }
            area.id to stages.mapIndexed { index, stage -> stage.copy(isCurrent = index == currentIndex) }
        }
        return DashboardUiState(
            totalPipeInches = summary.finished?.pipeInches ?: 0.0,
            totalJoints = summary.finished?.joints ?: 0,
            stageCount = ordered.size,
            stageTotals = summary.stages,
            percentComplete = summary.percentComplete,
            areas = areas,
            stagesByArea = stagesByArea,
            jointsByDiameter = jointsByDiameter,
            tasks = ordered,
            entries = entries
        )
    }
}
