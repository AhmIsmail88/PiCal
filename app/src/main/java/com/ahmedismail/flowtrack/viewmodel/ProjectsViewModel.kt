package com.ahmedismail.flowtrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val repository: FlowTrackRepository) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createProject(
        name: String,
        number: String?,
        responsibleEngineer: String,
        contractor: String?,
        client: String?,
        onCreated: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.createProject(
                Project(
                    name = name, number = number, responsibleEngineer = responsibleEngineer,
                    contractor = contractor, client = client
                )
            )
            onCreated(id)
        }
    }

    /** Renames / edits a project's info fields, keeping its id (and every Area/Task/Team/Entry tied to it) intact. */
    fun updateProject(
        project: Project,
        name: String,
        number: String?,
        responsibleEngineer: String,
        contractor: String?,
        client: String?,
        onUpdated: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateProject(
                project.copy(
                    name = name, number = number, responsibleEngineer = responsibleEngineer,
                    contractor = contractor, client = client
                )
            )
            onUpdated()
        }
    }

    /** Deletes a project and, via Room's ON DELETE CASCADE, everything under it (Areas, Team, Workflow, entries). */
    fun deleteProject(project: Project, onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProject(project)
            onDeleted()
        }
    }
}
