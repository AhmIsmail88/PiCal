package com.ahmedismail.flowtrack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.ahmedismail.flowtrack.FlowTrackApplication

/** Small manual DI: builds ViewModels from the Application-scoped repository. */
class FlowTrackViewModelFactory(private val app: FlowTrackApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            DashboardViewModel::class.java -> DashboardViewModel(app.repository) as T
            AddEntryViewModel::class.java -> AddEntryViewModel(app.repository) as T
            WorkflowSettingsViewModel::class.java -> WorkflowSettingsViewModel(app.repository) as T
            ProjectsViewModel::class.java -> ProjectsViewModel(app.repository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
    }
}
