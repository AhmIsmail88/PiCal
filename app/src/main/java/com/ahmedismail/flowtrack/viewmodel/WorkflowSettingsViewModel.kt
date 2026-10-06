package com.ahmedismail.flowtrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TaskType
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.data.entity.TeamRole
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkflowSettingsViewModel(private val repository: FlowTrackRepository) : ViewModel() {
    private val _orderBlocked = MutableStateFlow(false)
    val orderBlocked = _orderBlocked.asStateFlow()
    fun clearOrderError() { _orderBlocked.value = false }

    // Workflow / Tasks
    fun tasks(projectId: Long) = repository.observeTasks(projectId)

    fun addTask(projectId: Long, name: String, type: TaskType, extraFieldValue: String?) {
        viewModelScope.launch { repository.addTask(projectId, name, type, extraFieldValue) }
    }

    fun updateTask(task: TaskItem) {
        viewModelScope.launch { repository.updateTask(task) }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch { repository.deleteTask(task) }
    }

    /** Called after the user drops a dragged row — persists the new orderIndex for every task. */
    fun onReordered(newOrder: List<TaskItem>) {
        viewModelScope.launch { _orderBlocked.value = !repository.reorderTasks(newOrder) }
    }

    // Areas
    fun areas(projectId: Long) = repository.observeAreas(projectId)

    fun addArea(projectId: Long, name: String) {
        viewModelScope.launch { repository.addArea(Area(projectId = projectId, name = name)) }
    }

    fun deleteArea(area: Area) {
        viewModelScope.launch { repository.deleteArea(area) }
    }

    // Team members
    fun team(projectId: Long) = repository.observeTeam(projectId)

    /** Used by the per-employee report export to pull this project's full entry list, then filter client-side. */
    fun entries(projectId: Long) = repository.observeEntries(projectId)

    fun addTeamMember(projectId: Long, name: String, role: TeamRole, customRoleLabel: String? = null) {
        viewModelScope.launch {
            repository.addTeamMember(TeamMember(projectId = projectId, name = name, role = role, customRoleLabel = customRoleLabel))
        }
    }

    fun deleteTeamMember(member: TeamMember) {
        viewModelScope.launch { repository.deleteTeamMember(member) }
    }

    // Attendance
    fun attendanceForDate(projectId: Long, dateMillis: Long) = repository.observeAttendanceForDate(projectId, dateMillis)

    /** Used by the per-employee report to pull one member's full attendance history, then filter by period client-side. */
    fun attendanceForMember(memberId: Long) = repository.observeAttendanceForMember(memberId)

    fun setAttendance(record: com.ahmedismail.flowtrack.data.entity.AttendanceRecord) {
        viewModelScope.launch { repository.upsertAttendance(record) }
    }
}
