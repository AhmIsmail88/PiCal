package com.ahmedismail.flowtrack.ui.nav

sealed class Destination(val route: String) {
    data object About : Destination("about")
    data object Dashboard : Destination("dashboard")
    data object Projects : Destination("projects")
    data object AddEntry : Destination("add_entry")
    data object Reports : Destination("reports")
    data object WorkflowSettings : Destination("workflow_settings")
    data object Calculators : Destination("pipe_thickness")
}

