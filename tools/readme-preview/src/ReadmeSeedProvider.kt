package com.ahmedismail.flowtrack

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.ahmedismail.flowtrack.data.AppDatabase
import com.ahmedismail.flowtrack.data.entity.*
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import com.ahmedismail.flowtrack.util.CurrentProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/** Fictional screenshot data, only ever compiled using the documentation init script. */
class ReadmeSeedProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val ctx = requireNotNull(context)
        check(ctx.packageName == "com.ahmedismail.flowtrack.readme") { "Documentation seeding requires the isolated package." }
        val prefs = ctx.getSharedPreferences("readme_seed", 0)
        if (prefs.getBoolean("complete", false)) return true
        runBlocking(Dispatchers.IO) {
            val db = AppDatabase.get(ctx)
            val repo = FlowTrackRepository(db)
            val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 6, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val project = repo.createProject(Project(name = "Cooling Water Network", number = "DEMO-2026-01",
                responsibleEngineer = "Demo Engineer", contractor = "Demo Engineering", client = "Example Utilities", createdAt = date))
            val utility = repo.addArea(Area(projectId = project, name = "Utility Building"))
            val pump = repo.addArea(Area(projectId = project, name = "Pump Station"))
            val members = listOf("Demo Engineer" to TeamRole.ENGINEER, "Demo Supervisor" to TeamRole.SUPERVISOR,
                "Demo Welder A" to TeamRole.WELDER, "Demo Welder B" to TeamRole.WELDER, "Demo Inspector" to TeamRole.INSPECTOR)
                .map { (name, role) -> repo.addTeamMember(TeamMember(projectId = project, name = name, role = role)) }
            listOf("Fit-up" to TaskType.FABRICATION, "Welding" to TaskType.WELDING, "Inspection" to TaskType.INSPECTION, "Handover" to TaskType.HANDOVER)
                .forEach { (name, type) -> repo.addTask(project, name, type, if (type == TaskType.WELDING) "TIG (GTAW)" else null) }
            val tasks = db.taskDao().getForProject(project)
            listOf(Triple(utility, 4.0, listOf(28,24,20,16)), Triple(utility, 6.0, listOf(12,10,8,6)),
                Triple(pump, 6.0, listOf(8,6,4,2))).forEach { (area, diameter, counts) ->
                tasks.forEachIndexed { index, task ->
                    check(repo.submitEntry(project, task, area, date, diameter, DiameterUnit.INCH, counts[index],
                        if (index == 1) members[2] else members[4], members[1], hoursWorked = 8.0) is FlowTrackRepository.SaveResult.Success)
                }
            }
            members.forEachIndexed { i, id -> repo.upsertAttendance(AttendanceRecord(projectId = project,
                teamMemberId = id, date = date, status = if (i == 3) AttendanceStatus.LATE else AttendanceStatus.PRESENT,
                checkInMillis = date + 8 * 3_600_000 + if (i == 3) 30 * 60_000 else 0,
                checkOutMillis = date + 16 * 3_600_000)) }
            CurrentProjectStore.set(ctx, project)
            prefs.edit().putBoolean("complete", true).apply()
        }
        return true
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
