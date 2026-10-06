package com.ahmedismail.flowtrack.ui.nav

import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext
import com.ahmedismail.flowtrack.FlowTrackApplication
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.ui.components.FlowTrackScaffold
import com.ahmedismail.flowtrack.ui.screens.AddEntryScreen
import com.ahmedismail.flowtrack.ui.screens.DashboardScreen
import com.ahmedismail.flowtrack.ui.screens.CalculatorsScreen
import com.ahmedismail.flowtrack.ui.screens.AboutScreen
import com.ahmedismail.flowtrack.ui.screens.ProjectsScreen
import com.ahmedismail.flowtrack.ui.screens.ReportsScreen
import com.ahmedismail.flowtrack.ui.screens.WorkflowSettingsScreen
import com.ahmedismail.flowtrack.util.CurrentProjectStore
import com.ahmedismail.flowtrack.viewmodel.*

/** Primary bottom navigation destinations. Project management is a secondary screen. */
private val TAB_ROUTES = setOf(
    Destination.Dashboard.route, Destination.Projects.route, Destination.AddEntry.route,
    Destination.Calculators.route, Destination.Reports.route
)

@Composable
fun FlowTrackNavHost(app: FlowTrackApplication, onToggleLanguage: () -> Unit) {
    val navController: NavHostController = rememberNavController()
    val factory = remember { FlowTrackViewModelFactory(app) }
    val context = LocalContext.current

    var currentProjectId by remember { mutableStateOf<Long?>(null) }
    val projectFlow = remember(app.repository) { app.repository.observeProjects() }
    val allProjects by projectFlow.collectAsState(initial = emptyList())
    // Derived from the live list (not a stored snapshot) so editing the open
    // project's info anywhere in the app is reflected immediately here too —
    // a stored Project value would otherwise keep showing stale name/fields
    // after an edit until the project was manually reopened.
    val currentProject = allProjects.find { it.id == currentProjectId }

    // Restore whichever project was open last time (or fall back to the most
    // recent one) so Dashboard/Add/Reports/Settings don't show "no project
    // open" on every fresh launch when projects already exist.
    LaunchedEffect(allProjects) {
        if (currentProjectId == null && allProjects.isNotEmpty()) {
            val savedId = CurrentProjectStore.get(context)
            currentProjectId = allProjects.find { it.id == savedId }?.id ?: allProjects.first().id
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Destination.Dashboard.route

    val screenTitleRes = when (currentRoute) {
        Destination.Projects.route -> R.string.projects_title
        Destination.AddEntry.route -> R.string.add_entry_title
        Destination.Reports.route -> R.string.reports_title
        Destination.WorkflowSettings.route -> R.string.manage_project
        Destination.Calculators.route -> R.string.calculators_title
        Destination.About.route -> R.string.about_title
        else -> R.string.dashboard_title
    }

    FlowTrackScaffold(
        screenTitleRes = screenTitleRes,
        currentRoute = currentRoute,
        onNavigate = { dest ->
            navController.navigate(dest.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // A tab press must display that tab, even when its saved stack
            // contains project settings opened above it.
            navController.popBackStack(dest.route, inclusive = false)
        },
        onToggleLanguage = onToggleLanguage,
        onAbout = if (currentRoute != Destination.About.route) ({
            navController.navigate(Destination.About.route) { launchSingleTop = true }
        }) else null,
        // Only screens reached by pushing on top of a tab (not the tabs
        // themselves) get a back arrow — popBackStack() always returns to
        // whichever tab/screen was open before, from anywhere in the app.
        onBack = if (currentRoute !in TAB_ROUTES) ({ navController.popBackStack() }) else null,
        onManageProject = if (currentProject != null && currentRoute != Destination.WorkflowSettings.route) ({
            navController.navigate(Destination.WorkflowSettings.route) { launchSingleTop = true }
        }) else null
    ) { paddingModifier ->
        NavHost(navController = navController, startDestination = Destination.Dashboard.route) {
            composable(Destination.About.route) { AboutScreen(modifier = paddingModifier) }
            composable(Destination.Dashboard.route) {
                val vm: DashboardViewModel = viewModel(factory = factory)
                key(currentProject?.id) { DashboardScreen(project = currentProject, viewModel = vm, modifier = paddingModifier, onProjects = { navController.navigate(Destination.Projects.route) { launchSingleTop = true } }, onManageProject = { navController.navigate(Destination.WorkflowSettings.route) { launchSingleTop = true } }) }
            }
            composable(Destination.Projects.route) {
                val vm: ProjectsViewModel = viewModel(factory = factory)
                ProjectsScreen(
                    viewModel = vm,
                    onOpenProject = { project ->
                        currentProjectId = project.id
                        CurrentProjectStore.set(context, project.id)
                        navController.navigate(Destination.Dashboard.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                        }
                    },
                    onProjectDeleted = { deletedId ->
                        // If the project open elsewhere in the app was the one just
                        // deleted, clear it so Dashboard/Add/Reports/Settings fall
                        // back to "no project open" instead of holding a dangling
                        // reference to data that no longer exists.
                        if (currentProjectId == deletedId) {
                            currentProjectId = null
                            CurrentProjectStore.clear(context)
                        }
                    },
                    modifier = paddingModifier
                )
            }
            composable(Destination.AddEntry.route) {
                val vm: AddEntryViewModel = viewModel(factory = factory)
                key(currentProject?.id) { AddEntryScreen(project = currentProject, viewModel = vm, modifier = paddingModifier) }
            }
            composable(Destination.Reports.route) {
                ReportsScreen(project = currentProject, repository = app.repository, modifier = paddingModifier)
            }
            composable(Destination.WorkflowSettings.route) {
                val vm: WorkflowSettingsViewModel = viewModel(factory = factory)
                key(currentProject?.id) { WorkflowSettingsScreen(project = currentProject, viewModel = vm, modifier = paddingModifier) }
            }
            // Standalone — deliberately does NOT take currentProject. See
            // CalculatorsScreen's doc comment for why.
            composable(Destination.Calculators.route) {
                CalculatorsScreen(modifier = paddingModifier)
            }
        }
    }
}



