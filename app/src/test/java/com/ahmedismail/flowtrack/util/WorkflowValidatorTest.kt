package com.ahmedismail.flowtrack.util

import com.ahmedismail.flowtrack.data.dao.ProgressEntryDao
import com.ahmedismail.flowtrack.data.dao.TaskDao
import com.ahmedismail.flowtrack.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class WorkflowValidatorTest {
    private val first = TaskItem(1, 1, "Fit-up", TaskType.FABRICATION, 0)
    private val second = TaskItem(2, 1, "Welding", TaskType.WELDING, 1)
    private fun entry(id: Long, task: TaskItem, count: Int, diameter: Double = 2.0, unit: DiameterUnit = DiameterUnit.INCH, area: Long = 1) =
        ProgressEntry(id, 1, task.id, area, 0, diameter, unit, count, ProgressMath.pipeInches(diameter, unit, count), null, null)

    // DAO doubles supply snapshots; the tests exercise validation independently of SQLite.
    private fun validator(entries: List<ProgressEntry>): WorkflowValidator {
        val tasks = Proxy.newProxyInstance(TaskDao::class.java.classLoader, arrayOf(TaskDao::class.java)) { _, method, _ ->
            when (method.name) { "getForProject" -> listOf(second, first); else -> error(method.name) }
        } as TaskDao
        val progress = Proxy.newProxyInstance(ProgressEntryDao::class.java.classLoader, arrayOf(ProgressEntryDao::class.java)) { _, method, _ ->
            when (method.name) { "getForProject" -> entries; else -> error(method.name) }
        } as ProgressEntryDao
        return WorkflowValidator(tasks, progress)
    }

    @Test fun equivalentUnitsShareUpstreamCapacity() = runBlocking {
        val v = validator(listOf(entry(1, first, 10)))
        assertEquals(WorkflowValidator.Result.Accepted, v.validate(1, second, 1, 50.8, 10, DiameterUnit.MM))
        assertTrue(v.validate(1, second, 1, 50.8, 11, DiameterUnit.MM) is WorkflowValidator.Result.Rejected)
    }

    @Test fun equalNumbersInDifferentUnitsAreDifferentDiameters() = runBlocking {
        assertTrue(validator(listOf(entry(1, first, 10))).validate(1, second, 1, 2.0, 1, DiameterUnit.MM) is WorkflowValidator.Result.Rejected)
    }

    @Test fun areaCapacityIsIsolated() = runBlocking {
        assertTrue(validator(listOf(entry(1, first, 10))).validate(1, second, 2, 2.0, 1) is WorkflowValidator.Result.Rejected)
    }

    @Test fun editingDoesNotDoubleCountTheExistingEntry() = runBlocking {
        val v = validator(listOf(entry(1, first, 10), entry(2, second, 5)))
        assertEquals(WorkflowValidator.Result.Accepted, v.validate(1, second, 1, 2.0, 10, excludeEntryId = 2))
    }

    @Test fun reducingOrMovingUpstreamCannotOrphanDownstream() = runBlocking {
        val v = validator(listOf(entry(1, first, 10), entry(2, second, 8)))
        assertTrue(v.validate(1, first, 1, 2.0, 7, excludeEntryId = 1) is WorkflowValidator.Result.Rejected)
        assertTrue(v.validate(1, first, 1, 4.0, 10, excludeEntryId = 1) is WorkflowValidator.Result.Rejected)
        assertEquals(WorkflowValidator.Result.Accepted, v.validate(1, first, 1, 2.0, 8, excludeEntryId = 1))
    }

    @Test fun upstreamDeletionIsBlockedButDownstreamDeletionIsAllowed() = runBlocking {
        val upstream = entry(1, first, 10)
        val downstream = entry(2, second, 8)
        val v = validator(listOf(upstream, downstream))
        assertTrue(v.validateDeletion(upstream) is WorkflowValidator.Result.Rejected)
        assertEquals(WorkflowValidator.Result.Accepted, v.validateDeletion(downstream))
    }

    @Test fun firstStageHasNoUpstreamLimit() = runBlocking {
        assertEquals(WorkflowValidator.Result.Accepted, validator(emptyList()).validate(1, first, 1, 2.0, 100))
    }

    @Test fun reorderCannotPutLowerOutputBeforeHigherOutput() = runBlocking {
        val v = validator(listOf(entry(1, first, 10), entry(2, second, 8)))
        assertTrue(v.validateOrder(1, listOf(second, first)) is WorkflowValidator.Result.Rejected)
        assertEquals(WorkflowValidator.Result.Accepted, v.validateOrder(1, listOf(first, second)))
    }

    @Test fun reorderValidatesEachDiameterEvenIfGrandTotalsMatch() = runBlocking {
        val v = validator(listOf(entry(1, first, 10, 2.0), entry(2, second, 10, 4.0)))
        assertTrue(v.validateOrder(1, listOf(second, first)) is WorkflowValidator.Result.Rejected)
    }

    @Test fun equalOutputOrEmptyWorkflowCanBeReordered() = runBlocking {
        val v = validator(listOf(entry(1, first, 10), entry(2, second, 10, 50.8, DiameterUnit.MM)))
        assertEquals(WorkflowValidator.Result.Accepted, v.validateOrder(1, listOf(second, first)))
        assertEquals(WorkflowValidator.Result.Accepted, validator(emptyList()).validateOrder(1, listOf(second, first)))
    }
}
