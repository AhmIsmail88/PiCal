package com.ahmedismail.flowtrack.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.ui.theme.*
import com.ahmedismail.flowtrack.viewmodel.ProjectsViewModel

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (Project) -> Unit,
    onProjectDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<Project?>(null) }
    var deletingProject by remember { mutableStateOf<Project?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(14.dp)) {
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(projects, key = { it.id }) { project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassPanel()
                        .clickable { onOpenProject(project) }
                        .padding(14.dp, 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        project.number?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = Ink2, fontFamily = MonoFontFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(project.responsibleEngineer, style = MaterialTheme.typography.bodySmall, color = Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { editingProject = project }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_project), tint = Steel)
                        }
                        IconButton(onClick = { deletingProject = project }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_project), tint = Orange)
                        }
                    }
                }
            }
            if (projects.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_projects_yet),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink2,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showCreate = true },
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth().height(46.dp)
        ) {
            Text(stringResource(R.string.new_project))
        }
    }

    if (showCreate) {
        ProjectDialog(
            initial = null,
            onDismiss = { showCreate = false },
            onConfirm = { name, number, engineer, contractor, client ->
                viewModel.createProject(name, number, engineer, contractor, client) { showCreate = false }
            }
        )
    }

    editingProject?.let { project ->
        ProjectDialog(
            initial = project,
            onDismiss = { editingProject = null },
            onConfirm = { name, number, engineer, contractor, client ->
                viewModel.updateProject(project, name, number, engineer, contractor, client) { editingProject = null }
            }
        )
    }

    deletingProject?.let { project ->
        AlertDialog(
            onDismissRequest = { deletingProject = null },
            title = { Text(stringResource(R.string.delete_project_confirm_title)) },
            text = { Text(stringResource(R.string.delete_project_confirm_message, project.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project) { onProjectDeleted(project.id) }
                    deletingProject = null
                }) { Text(stringResource(R.string.delete_project), color = Orange) }
            },
            dismissButton = { TextButton(onClick = { deletingProject = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun ProjectDialog(
    initial: Project?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, number: String?, engineer: String, contractor: String?, client: String?) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var number by remember { mutableStateOf(initial?.number ?: "") }
    var engineer by remember { mutableStateOf(initial?.responsibleEngineer ?: "") }
    var contractor by remember { mutableStateOf(initial?.contractor ?: "") }
    var client by remember { mutableStateOf(initial?.client ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) stringResource(R.string.edit_project) else stringResource(R.string.new_project)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.project_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(number, { number = it }, label = { Text(stringResource(R.string.project_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(engineer, { engineer = it }, label = { Text(stringResource(R.string.responsible_engineer)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(contractor, { contractor = it }, label = { Text(stringResource(R.string.contractor)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(client, { client = it }, label = { Text(stringResource(R.string.client)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && engineer.isNotBlank()) {
                    onConfirm(name.trim(), number.trim().ifBlank { null }, engineer.trim(), contractor.trim().ifBlank { null }, client.trim().ifBlank { null })
                }
            }) { Text(stringResource(R.string.save_entry)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}
