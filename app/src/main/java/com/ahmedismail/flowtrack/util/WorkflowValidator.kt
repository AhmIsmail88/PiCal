package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.dao.ProgressEntryDao
import com.ahmedismail.flowtrack.data.dao.TaskDao
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem
import kotlin.math.abs

/** Compare cumulative joints per area and physical diameter, including downstream dependencies. */
class WorkflowValidator(private val taskDao: TaskDao, private val progressEntryDao: ProgressEntryDao) {
    sealed class Result {
        data object Accepted : Result()
        data class Rejected(val previousTaskName: String, val previousStageJoints: Int) : Result()
    }

    suspend fun validate(
        projectId: Long, task: TaskItem, areaId: Long, diameter: Double,
        newJointQuantity: Int, unit: DiameterUnit = DiameterUnit.INCH, excludeEntryId: Long? = null
    ): Result {
        val entries = progressEntryDao.getForProject(projectId)
        val proposed = entries.filterNot { it.id == excludeEntryId } + ProgressEntry(
            projectId = projectId, taskId = task.id, areaId = areaId, date = 0,
            diameter = diameter, unit = unit, jointQuantity = newJointQuantity,
            pipeInches = ProgressMath.pipeInches(diameter, unit, newJointQuantity),
            performedById = null, supervisorId = null
        )
        return checkAffected(projectId, proposed, listOfNotNull(
            Triple(areaId, diameter, unit),
            entries.find { it.id == excludeEntryId }?.let { Triple(it.areaId, it.diameter, it.unit) }
        ))
    }

    suspend fun validateDeletion(entry: ProgressEntry): Result = checkAffected(
        entry.projectId, progressEntryDao.getForProject(entry.projectId).filterNot { it.id == entry.id },
        listOf(Triple(entry.areaId, entry.diameter, entry.unit))
    )

    suspend fun validateOrder(projectId: Long, orderedTasks: List<TaskItem>): Result {
        val entries = progressEntryDao.getForProject(projectId)
        return check(orderedTasks, entries, entries.map { Triple(it.areaId, it.diameter, it.unit) }.distinct())
    }

    private suspend fun checkAffected(
        projectId: Long, proposed: List<ProgressEntry>, affected: List<Triple<Long, Double, DiameterUnit>>
    ): Result {
        return check(taskDao.getForProject(projectId).sortedBy { it.orderIndex }, proposed, affected)
    }

    private fun check(tasks: List<TaskItem>, proposed: List<ProgressEntry>, affected: List<Triple<Long, Double, DiameterUnit>>): Result {
        for ((areaId, diameter, unit) in affected) {
            val inches = ProgressMath.diameterInches(diameter, unit)
            val totals = proposed.filter {
                it.areaId == areaId && abs(ProgressMath.diameterInches(it.diameter, it.unit) - inches) < 0.000001
            }.groupBy { it.taskId }.mapValues { (_, rows) -> rows.sumOf { it.jointQuantity.toLong() } }
            for ((previous, current) in tasks.zipWithNext()) {
                val upstream = totals[previous.id] ?: 0L
                if ((totals[current.id] ?: 0L) > upstream) {
                    return Result.Rejected(previous.name, upstream.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
            }
        }
        return Result.Accepted
    }
}
