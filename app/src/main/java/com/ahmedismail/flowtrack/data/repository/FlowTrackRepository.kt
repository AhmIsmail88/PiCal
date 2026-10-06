package com.ahmedismail.flowtrack.data.repository

import com.ahmedismail.flowtrack.data.AppDatabase
import com.ahmedismail.flowtrack.data.entity.Area
import com.ahmedismail.flowtrack.data.entity.Project
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import com.ahmedismail.flowtrack.data.entity.TeamMember
import com.ahmedismail.flowtrack.util.WorkflowValidator
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction
import com.ahmedismail.flowtrack.util.ProgressMath

class FlowTrackRepository(private val db: AppDatabase) {

    private val validator = WorkflowValidator(db.taskDao(), db.progressEntryDao())

    // Projects
    fun observeProjects(): Flow<List<Project>> = db.projectDao().observeAll()
    suspend fun createProject(project: Project): Long = db.projectDao().insert(project)
    suspend fun updateProject(project: Project) = db.projectDao().update(project)
    suspend fun deleteProject(project: Project) = db.projectDao().delete(project)

    // Areas
    fun observeAreas(projectId: Long): Flow<List<Area>> = db.areaDao().observeForProject(projectId)
    suspend fun addArea(area: Area): Long = db.areaDao().insert(area)
    suspend fun deleteArea(area: Area) = db.areaDao().delete(area)

    // Team
    fun observeTeam(projectId: Long): Flow<List<TeamMember>> = db.teamMemberDao().observeForProject(projectId)
    suspend fun addTeamMember(member: TeamMember): Long = db.teamMemberDao().insert(member)
    suspend fun deleteTeamMember(member: TeamMember) = db.teamMemberDao().delete(member)

    // Workflow / Tasks
    fun observeTasks(projectId: Long): Flow<List<TaskItem>> = db.taskDao().observeForProject(projectId)

    suspend fun addTask(projectId: Long, name: String, type: com.ahmedismail.flowtrack.data.entity.TaskType, extraFieldValue: String?) {
        val nextIndex = db.taskDao().getForProject(projectId).size
        db.taskDao().insert(TaskItem(projectId = projectId, name = name, type = type, orderIndex = nextIndex, extraFieldValue = extraFieldValue))
    }

    /** Renames a task / changes its type or custom "Other" label without touching its position in the Workflow. */
    suspend fun updateTask(task: TaskItem) = db.taskDao().update(task)

    suspend fun deleteTask(task: TaskItem) {
        db.taskDao().delete(task)
        // Re-pack orderIndex so the Workflow stays contiguous after a deletion.
        val remaining = db.taskDao().getForProject(task.projectId)
        db.taskDao().updateAll(remaining.mapIndexed { i, t -> t.copy(orderIndex = i) })
    }

    /** Persists a full drag-to-reorder result from the Workflow Settings screen. */
    suspend fun reorderTasks(reordered: List<TaskItem>): Boolean = db.withTransaction {
        val projectId = reordered.firstOrNull()?.projectId ?: return@withTransaction true
        val current = db.taskDao().getForProject(projectId)
        if (current.map { it.id }.toSet() != reordered.map { it.id }.toSet() || current.size != reordered.size) return@withTransaction false
        val ordered = reordered.map { row -> current.first { it.id == row.id } }
        if (validator.validateOrder(projectId, ordered) is WorkflowValidator.Result.Rejected) return@withTransaction false
        db.taskDao().updateAll(ordered.mapIndexed { i, t -> t.copy(orderIndex = i) })
        true
    }

    // Progress entries
    fun observeEntries(projectId: Long): Flow<List<ProgressEntry>> = db.progressEntryDao().observeForProject(projectId)
    fun observeTotalPipeInches(projectId: Long): Flow<Double> = db.progressEntryDao().observeTotalPipeInches(projectId)
    fun observeTotalJoints(projectId: Long): Flow<Int> = db.progressEntryDao().observeTotalJoints(projectId)

    sealed class SaveResult {
        data class Success(val entryId: Long) : SaveResult()
        data class Blocked(val previousTaskName: String, val previousStageJoints: Int) : SaveResult()
    }

    /**
     * Validates against spec §5.1 before writing. Pipe Inches (§5: Diameter x
     * Joint Quantity) is computed here so every write path stores it consistently.
     */
    suspend fun submitEntry(
        projectId: Long,
        task: TaskItem,
        areaId: Long,
        dateMillis: Long,
        diameter: Double,
        unit: com.ahmedismail.flowtrack.data.entity.DiameterUnit,
        jointQuantity: Int,
        performedById: Long?,
        supervisorId: Long?,
        photoUri: String? = null,
        hoursWorked: Double? = null
    ): SaveResult = db.withTransaction {
        require(diameter.isFinite() && diameter > 0 && jointQuantity > 0)
        require(hoursWorked == null || (hoursWorked.isFinite() && hoursWorked >= 0))
        require(task.projectId == projectId && db.taskDao().getById(task.id)?.projectId == projectId)
        val result = validator.validate(projectId, task, areaId, diameter, jointQuantity, unit)
        if (result is WorkflowValidator.Result.Rejected) {
            return@withTransaction SaveResult.Blocked(result.previousTaskName, result.previousStageJoints)
        }
        val pipeInches = ProgressMath.pipeInches(diameter, unit, jointQuantity)
        val id = db.progressEntryDao().insert(
            ProgressEntry(
                projectId = projectId, taskId = task.id, areaId = areaId, date = dateMillis,
                diameter = diameter, unit = unit, jointQuantity = jointQuantity, pipeInches = pipeInches,
                performedById = performedById, supervisorId = supervisorId, photoUri = photoUri, hoursWorked = hoursWorked
            )
        )
        SaveResult.Success(id)
    }

    /**
     * Re-validates and saves an edit to an existing entry — same §5.1 rule
     * as a new entry, but excluding this entry's own current joint count
     * from the running total it's checked against (see WorkflowValidator).
     * Task and Area are intentionally not editable here (see AddEntryScreen/
     * Dashboard edit dialog) to keep the validation re-check well-scoped.
     */
    suspend fun updateEntry(entry: ProgressEntry, task: TaskItem): SaveResult = db.withTransaction {
        require(entry.diameter.isFinite() && entry.diameter > 0 && entry.jointQuantity > 0)
        require(entry.hoursWorked == null || (entry.hoursWorked.isFinite() && entry.hoursWorked >= 0))
        val existing = requireNotNull(db.progressEntryDao().getById(entry.id))
        require(existing.projectId == entry.projectId && existing.taskId == entry.taskId && existing.areaId == entry.areaId && task.id == entry.taskId)
        val result = validator.validate(
            entry.projectId, task, entry.areaId, entry.diameter, entry.jointQuantity, unit = entry.unit, excludeEntryId = entry.id
        )
        if (result is WorkflowValidator.Result.Rejected) {
            return@withTransaction SaveResult.Blocked(result.previousTaskName, result.previousStageJoints)
        }
        val pipeInches = ProgressMath.pipeInches(entry.diameter, entry.unit, entry.jointQuantity)
        db.progressEntryDao().update(entry.copy(pipeInches = pipeInches))
        SaveResult.Success(entry.id)
    }

    suspend fun deleteEntry(entry: ProgressEntry): SaveResult = db.withTransaction {
        val existing = db.progressEntryDao().getById(entry.id) ?: return@withTransaction SaveResult.Success(entry.id)
        when (val result = validator.validateDeletion(existing)) {
            is WorkflowValidator.Result.Rejected -> SaveResult.Blocked(result.previousTaskName, result.previousStageJoints)
            WorkflowValidator.Result.Accepted -> {
                db.progressEntryDao().delete(existing)
                SaveResult.Success(entry.id)
            }
        }
    }

    // Attendance
    fun observeAttendanceForDate(projectId: Long, dateMillis: Long) = db.attendanceDao().observeForProjectAndDate(projectId, dateMillis)
    fun observeAttendanceForMember(memberId: Long) = db.attendanceDao().observeForMember(memberId)
    suspend fun upsertAttendance(record: com.ahmedismail.flowtrack.data.entity.AttendanceRecord) = db.attendanceDao().upsert(record)
    suspend fun deleteAttendance(record: com.ahmedismail.flowtrack.data.entity.AttendanceRecord) = db.attendanceDao().delete(record)
}
