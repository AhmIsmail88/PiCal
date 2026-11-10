package com.ahmedismail.flowtrack

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.withTransaction
import com.ahmedismail.flowtrack.data.AppDatabase
import com.ahmedismail.flowtrack.data.entity.*
import com.ahmedismail.flowtrack.data.repository.FlowTrackRepository
import com.ahmedismail.flowtrack.pdf.*
import com.ahmedismail.flowtrack.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Calendar

class AcceptanceInstrumentation : Instrumentation() {
    private var arguments = Bundle()
    private val checks = mutableListOf<String>()
    private fun verify(name: String, condition: Boolean) { check(condition) { name }; checks += name }
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            runBlocking(Dispatchers.IO) { runScenario(arguments.getString("phase") ?: "full") }
            result.putString("stream", "PASS ${checks.size} acceptance checks\n" + checks.joinToString("\n"))
            finish(Activity.RESULT_OK, result)
        } catch (error: Throwable) {
            result.putString("stream", "FAIL after ${checks.size} checks\n${error.stackTraceToString()}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
    private suspend fun runScenario(phase: String) {
        val ctx = targetContext
        check(ctx.packageName in setOf("com.ahmedismail.flowtrack.acceptance", "com.ahmedismail.flowtrack.acceptance.release"))
        val db = AppDatabase.get(ctx)
        val repo = FlowTrackRepository(db)
        val dir = File(ctx.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 21, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        if (phase == "full") {
            check(repo.observeProjects().first().isEmpty()) { "Use a fresh isolated acceptance installation." }
            val projectId = repo.createProject(Project(name="Acceptance Cooling Water", number="UAT-2026-001", responsibleEngineer="Test Engineer", client="Fictional Client", contractor="Test Contractor"))
            val project = requireNotNull(db.projectDao().getById(projectId))
            repo.updateProject(project.copy(name="Acceptance Cooling Water — verified"))
            verify("project create/edit", db.projectDao().getById(projectId)?.name?.endsWith("verified") == true)
            val area = repo.addArea(Area(projectId=projectId, name="Pump Hall"))
            val area2 = repo.addArea(Area(projectId=projectId, name="Utility Area"))
            val supervisor = repo.addTeamMember(TeamMember(projectId=projectId,name="Test Supervisor",role=TeamRole.SUPERVISOR))
            val welder = repo.addTeamMember(TeamMember(projectId=projectId,name="Test Welder",role=TeamRole.WELDER))
            repo.addTeamMember(TeamMember(projectId=projectId,name="Test Inspector",role=TeamRole.INSPECTOR))
            listOf("Fit-up" to TaskType.FABRICATION,"Welding" to TaskType.WELDING,"Inspection" to TaskType.INSPECTION,"Handover" to TaskType.HANDOVER)
                .forEach { (name,type) -> repo.addTask(projectId,name,type,if(type==TaskType.WELDING) "TIG" else null) }
            val tasks = db.taskDao().getForProject(projectId)
            verify("areas/team/workflow created", repo.observeAreas(projectId).first().size==2 && repo.observeTeam(projectId).first().size==3 && tasks.size==4)
            suspend fun add(stage:Int, qty:Int, unit:DiameterUnit=DiameterUnit.INCH, diameter:Double=4.0, location:Long=area) =
                repo.submitEntry(projectId,tasks[stage],location,date,diameter,unit,qty,welder,supervisor,hoursWorked=2.0)
            verify("downstream before upstream blocked", add(1,1) is FlowTrackRepository.SaveResult.Blocked)
            verify("fit-up accepted", add(0,10) is FlowTrackRepository.SaveResult.Success)
            verify("upstream overflow blocked", add(1,11) is FlowTrackRepository.SaveResult.Blocked)
            verify("mixed mm/inch same diameter accepted", add(1,8,DiameterUnit.MM,101.6) is FlowTrackRepository.SaveResult.Success)
            verify("wrong area cannot consume other area scope", add(1,1,location=area2) is FlowTrackRepository.SaveResult.Blocked)
            verify("inspection accepted", add(2,6) is FlowTrackRepository.SaveResult.Success)
            verify("handover accepted", add(3,4) is FlowTrackRepository.SaveResult.Success)
            val before = db.progressEntryDao().getForProject(projectId)
            val fit = before.first { it.taskId==tasks[0].id }
            val weld = before.first { it.taskId==tasks[1].id }
            val hand = before.first { it.taskId==tasks[3].id }
            verify("dependent edit blocked", repo.updateEntry(fit.copy(jointQuantity=7),tasks[0]) is FlowTrackRepository.SaveResult.Blocked)
            verify("dependent deletion blocked", repo.deleteEntry(weld) is FlowTrackRepository.SaveResult.Blocked)
            verify("upstream edit preserving downstream accepted", repo.updateEntry(fit.copy(jointQuantity=12),tasks[0]) is FlowTrackRepository.SaveResult.Success)
            verify("incompatible reorder blocked", !repo.reorderTasks(tasks.reversed()))
            verify("unchanged reorder accepted", repo.reorderTasks(tasks))
            verify("terminal deletion accepted", repo.deleteEntry(hand) is FlowTrackRepository.SaveResult.Success)
            verify("terminal restoration accepted", add(3,4) is FlowTrackRepository.SaveResult.Success)
            val count = db.progressEntryDao().getForProject(projectId).size
            val bad = runCatching { add(0,-1) }
            verify("invalid input rejected atomically", bad.isFailure && db.progressEntryDao().getForProject(projectId).size==count)
            repo.upsertAttendance(AttendanceRecord(projectId=projectId,teamMemberId=welder,date=date,status=AttendanceStatus.PRESENT,checkInMillis=date+8*3600000,checkOutMillis=date+16*3600000))
            repo.upsertAttendance(AttendanceRecord(projectId=projectId,teamMemberId=welder,date=date,status=AttendanceStatus.LATE,checkInMillis=date+9*3600000,checkOutMillis=date+16*3600000))
            repo.upsertAttendance(AttendanceRecord(projectId=projectId,teamMemberId=welder,date=date-86400000,status=AttendanceStatus.ABSENT))
            verify("attendance upsert unique per day", repo.observeAttendanceForDate(projectId,date).first().single().status==AttendanceStatus.LATE)
            verify("attendance history", repo.observeAttendanceForMember(welder).first().size==2)
            val scratch = repo.createProject(Project(name="Scratch deletion", responsibleEngineer="Test"))
            val foreignArea = repo.addArea(Area(projectId=scratch,name="Delete me"))
            val foreignMember = repo.addTeamMember(TeamMember(projectId=scratch,name="Other-project member",role=TeamRole.WELDER))
            verify("cross-project area rejected", runCatching {
                repo.submitEntry(projectId,tasks[0],foreignArea,date,4.0,DiameterUnit.INCH,1,welder,supervisor)
            }.isFailure)
            verify("cross-project performer rejected", runCatching {
                repo.submitEntry(projectId,tasks[0],area,date,4.0,DiameterUnit.INCH,1,foreignMember,supervisor)
            }.isFailure)
            verify("cross-project supervisor rejected on edit", runCatching {
                repo.updateEntry(fit.copy(supervisorId=foreignMember),tasks[0])
            }.isFailure)
            repo.deleteProject(requireNotNull(db.projectDao().getById(scratch)))
            verify("project cascade deletion isolated", db.projectDao().getById(scratch)==null && repo.observeAreas(scratch).first().isEmpty())
            CurrentProjectStore.set(ctx,projectId)
        }
        val project = repo.observeProjects().first().single()
        val tasks = db.taskDao().getForProject(project.id)
        val entries = db.progressEntryDao().getForProject(project.id)
        val areas = repo.observeAreas(project.id).first()
        val team = repo.observeTeam(project.id).first()
        val summary = ProgressSummary.calculate(tasks,entries)
        verify("persisted entries count", entries.size==4)
        verify("final stage is 4 joints / 16 diameter-inches", summary.finished?.joints==4L && summary.finished?.pipeInches==16.0)
        verify("completion ratio excludes duplicate stage counts", summary.percentComplete==33)
        verify("welding totals are 8 joints / 32 diameter-inches", entries.filter { it.taskId==tasks[1].id }.let { rows -> rows.sumOf { it.jointQuantity }==8 && rows.sumOf { ProgressMath.pipeInches(it.diameter,it.unit,it.jointQuantity) }==32.0 })
        verify("empty reporting period has no records", entries.none { it.date > date+86400000 })
        val photo = File(ctx.filesDir,"acceptance-photo.png")
        val bitmap = Bitmap.createBitmap(320,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(24,110,126)) }
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        val illustrated = entries.mapIndexed { index,e -> if(index==0)e.copy(photoUri=android.net.Uri.fromFile(photo).toString()) else e }
        ReportGenerator.generate(ctx,project,areas,tasks,illustrated,"Acceptance 21 October 2026",dir).copyTo(File(dir,"project.pdf"),true)
        ReportGenerator.generate(ctx,project,areas,tasks,emptyList(),"Empty period",dir).copyTo(File(dir,"project-empty.pdf"),true)
        val welding = entries.filter { e -> tasks.first { it.id==e.taskId }.type==TaskType.WELDING }
        WeldInchesReportGenerator.generate(project,welding,tasks,team,"Acceptance period",dir).copyTo(File(dir,"welding.pdf"),true)
        val welder = team.first { it.role==TeamRole.WELDER }
        EmployeeReportGenerator.generate(project,welder,entries.filter { it.performedById==welder.id },tasks,areas,
            repo.observeAttendanceForMember(welder.id).first(),"Acceptance period",dir).copyTo(File(dir,"employee.pdf"),true)
        EmployeeReportGenerator.generate(project,team.first { it.role==TeamRole.INSPECTOR },emptyList(),tasks,areas,emptyList(),"Empty activity",dir).copyTo(File(dir,"employee-empty.pdf"),true)
        CalculatorReportFixtures.generate(File(dir,"calculators").apply { mkdirs() })
        verify("13 calculator PDF fixtures produced", File(dir,"calculators").listFiles { f -> f.extension=="pdf" && !f.name.startsWith("PiCal_") }?.size==13)
        verify("project, empty, welding and employee PDFs produced", listOf("project","project-empty","welding","employee","employee-empty").all { File(dir,"$it.pdf").length()>1000 })
        val sharedReport = File(ctx.getExternalFilesDir(null),"reports/acceptance.pdf").apply { parentFile?.mkdirs() }
        File(dir,"project.pdf").copyTo(sharedReport,true)
        val intent = ReportNotifier.shareIntent(ctx,sharedReport)
        verify("report sharing uses Android chooser", intent.action==android.content.Intent.ACTION_CHOOSER)
        val view = ReportNotifier.viewIntent(ctx,sharedReport)
        verify("report open uses a grantable content URI", view.data?.scheme=="content" && view.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        verify("notification handles missing permission", runCatching { ReportNotifier.notify(ctx,sharedReport) }.isSuccess)
        File(dir,"checks-$phase.txt").writeText(checks.joinToString("\n"))
    }
}
