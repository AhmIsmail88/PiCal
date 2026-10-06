package com.ahmedismail.flowtrack.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ahmedismail.flowtrack.R
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.TaskType
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import com.ahmedismail.flowtrack.pdf.ReportGenerator
import com.ahmedismail.flowtrack.pdf.ReportNotifier
import com.ahmedismail.flowtrack.pdf.WeldInchesReportGenerator
import com.ahmedismail.flowtrack.ui.components.DateRange
import com.ahmedismail.flowtrack.ui.components.DateRangeField
import com.ahmedismail.flowtrack.ui.theme.CardWhite
import com.ahmedismail.flowtrack.ui.theme.Ink2
import com.ahmedismail.flowtrack.ui.theme.Steel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ReportsScreen(
    project: Project?,
    repository: FlowTrackRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Survives tab switches (the destination is popped with saveState and
    // restored later), so a chosen period and the last exported file are not
    // lost when the user checks another tab. File is stored by path because
    // only Bundle-able values can be saved.
    var lastFilePath by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf(false) }
    var rangeStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    val range = DateRange(rangeStart, rangeEnd)
    // Holds whichever export was tapped, so it can run once the
    // notification-permission result comes back (or immediately if
    // already granted) — shared by both report buttons below.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.US) }
    fun periodLabel(r: DateRange): String? = when {
        r.startMillis == null && r.endMillis == null -> null
        else -> {
            val from = r.startMillis?.let { dateFormat.format(Date(it)) } ?: "…"
            val to = r.endMillis?.let { dateFormat.format(Date(it)) } ?: "…"
            "$from – $to"
        }
    }

    fun runExport(target: Project, selectedRange: DateRange) {
        scope.launch {
            try {
                val areas = repository.observeAreas(target.id).first()
                val tasks = repository.observeTasks(target.id).first()
                val allEntries = repository.observeEntries(target.id).first()
                val entries = allEntries.filter { selectedRange.contains(it.date) }
                val outputDir = File(context.getExternalFilesDir(null), "reports")
                val file = ReportGenerator.generate(
                    context = context, project = target, areas = areas, tasks = tasks, entries = entries,
                    periodLabel = periodLabel(selectedRange),
                    outputDir = outputDir
                )
                lastFilePath = file.absolutePath
                error = null
                ReportNotifier.notify(context, file)
            } catch (t: Throwable) {
                error = t.message
            }
        }
    }

    /** Scoped to entries whose Task is of type WELDING only — Fit-up, Inspection, and every other task type are excluded, since welders are typically different people from that crew with their own output to track. */
    fun runWeldExport(target: Project, selectedRange: DateRange) {
        scope.launch {
            try {
                val tasks = repository.observeTasks(target.id).first()
                val weldingTaskIds = tasks.filter { it.type == TaskType.WELDING }.map { it.id }.toSet()
                val team = repository.observeTeam(target.id).first()
                val allEntries = repository.observeEntries(target.id).first()
                val weldingEntries = allEntries.filter { it.taskId in weldingTaskIds && selectedRange.contains(it.date) }
                val outputDir = File(context.getExternalFilesDir(null), "reports")
                val file = WeldInchesReportGenerator.generate(
                    project = target, weldingEntries = weldingEntries, tasks = tasks, team = team,
                    periodLabel = periodLabel(selectedRange),
                    outputDir = outputDir
                )
                lastFilePath = file.absolutePath
                error = null
                ReportNotifier.notify(context, file)
            } catch (t: Throwable) {
                error = t.message
            }
        }
    }

    // Android 13+ requires this permission before we can post the "report
    // ready" notification; the export itself and the in-screen Open/Share
    // buttons still work even if it's denied.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingAction?.invoke()
        pendingAction = null
        pendingExport = false
    }

    fun exportTapped(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingExport = true
            pendingAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (project == null) {
            Text(stringResource(R.string.open_project_first_reports), color = Ink2)
            return@Column
        }

        DateRangeField(
            range = range,
            onChange = { rangeStart = it.startMillis; rangeEnd = it.endMillis },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { exportTapped { runExport(project, range) } },
            enabled = !pendingExport,
            colors = ButtonDefaults.buttonColors(containerColor = Steel, contentColor = CardWhite),
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.export_pdf))
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { exportTapped { runWeldExport(project, range) } },
            enabled = !pendingExport,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.export_weld_inches_report))
        }

        lastFilePath?.let { path ->
            val file = File(path)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.report_saved_notice),
                color = Ink2,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { context.startActivity(ReportNotifier.viewIntent(context, file)) }) {
                    Text(stringResource(R.string.report_open))
                }
                OutlinedButton(onClick = { context.startActivity(ReportNotifier.shareIntent(context, file)) }) {
                    Text(stringResource(R.string.report_share))
                }
            }
        }
        error?.let {
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.export_failed, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
