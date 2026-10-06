package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.entity.*
import org.junit.Assert.*
import org.junit.Test

class ProgressSummaryTest {
    private val tasks = listOf("Fit-up", "Weld", "Handover").mapIndexed { i, name ->
        TaskItem(i.toLong() + 1, 1, name, TaskType.WELDING, i)
    }
    private fun entry(stage: Int, count: Int, diameter: Double = 10.0, unit: DiameterUnit = DiameterUnit.INCH, area: Long = 1) =
        ProgressEntry(projectId = 1, taskId = tasks[stage].id, areaId = area, date = 0,
            diameter = diameter, unit = unit, jointQuantity = count, pipeInches = 999.0,
            performedById = null, supervisorId = null)

    @Test fun stagesAreCumulativeAndOnlyLastCountsAsFinished() {
        val summary = ProgressSummary.calculate(tasks.reversed(), listOf(entry(0, 10), entry(1, 8), entry(2, 6)))
        assertEquals(listOf(100.0, 80.0, 60.0), summary.stages.map { it.pipeInches })
        assertEquals(60.0, summary.finished!!.pipeInches, 0.000001)
        assertEquals(6L, summary.finished!!.joints)
        assertEquals(60, summary.percentComplete)
    }

    @Test fun unrecordedFinalStageMeansNothingIsFullyFinished() {
        val summary = ProgressSummary.calculate(tasks, listOf(entry(0, 10), entry(1, 10)))
        assertEquals(0.0, summary.finished!!.pipeInches, 0.0)
        assertEquals(0, summary.percentComplete)
    }

    @Test fun usesDiameterInchesAcrossUnitsAreasAndEntries() {
        val rows = listOf(entry(0, 2, 50.8, DiameterUnit.MM), entry(0, 3, 2.0, area = 2), entry(2, 1, 2.0))
        val summary = ProgressSummary.calculate(tasks, rows)
        assertEquals(10.0, summary.stages.first().pipeInches, 0.000001)
        assertEquals(20, summary.percentComplete)
    }

    @Test fun percentIsWeightedByDiameterNotJointCount() {
        val summary = ProgressSummary.calculate(tasks, listOf(entry(0, 1, 10.0), entry(0, 1, 2.0), entry(2, 1, 2.0)))
        assertEquals(16, summary.percentComplete)
    }

    @Test fun emptyAndSingleStageWorkflowsHaveDefinedResults() {
        assertNull(ProgressSummary.calculate(emptyList(), emptyList()).finished)
        assertEquals(0, ProgressSummary.calculate(tasks, emptyList()).percentComplete)
        assertEquals(100, ProgressSummary.calculate(tasks.take(1), listOf(entry(0, 1))).percentComplete)
    }
}
