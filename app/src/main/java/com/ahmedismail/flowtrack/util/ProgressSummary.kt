package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import com.ahmedismail.flowtrack.data.entity.TaskItem

data class StageTotal(val task: TaskItem, val pipeInches: Double, val joints: Long)

/** Cumulative output per stage. Never add stages together: they describe the same scope. */
data class ProgressSummary(val stages: List<StageTotal>) {
    val finished: StageTotal? get() = stages.lastOrNull()
    val percentComplete: Int get() {
        val scope = stages.firstOrNull()?.pipeInches ?: 0.0
        return if (scope > 0) ((finished?.pipeInches ?: 0.0) * 100 / scope).toInt().coerceIn(0, 100) else 0
    }

    companion object {
        fun calculate(tasks: List<TaskItem>, entries: List<ProgressEntry>): ProgressSummary {
            val grouped = entries.groupBy { it.taskId }
            return ProgressSummary(tasks.sortedBy { it.orderIndex }.map { task ->
                val rows = grouped[task.id].orEmpty()
                StageTotal(task, rows.sumOf { ProgressMath.pipeInches(it.diameter, it.unit, it.jointQuantity) },
                    rows.sumOf { it.jointQuantity.toLong() })
            })
        }
    }
}
