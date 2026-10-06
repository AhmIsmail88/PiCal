package com.ahmedismail.flowtrack.data.dao

import androidx.room.*
import com.ahmedismail.flowtrack.data.entity.ProgressEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressEntryDao {
    @Query("SELECT * FROM progress_entries WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<ProgressEntry>
    @Query("SELECT * FROM progress_entries WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<ProgressEntry>>

    @Query("SELECT * FROM progress_entries WHERE taskId = :taskId AND areaId = :areaId ORDER BY date DESC")
    fun observeForTaskAndArea(taskId: Long, areaId: Long): Flow<List<ProgressEntry>>

    /**
     * Cumulative joints already logged for a given Area + Diameter on a
     * specific task — the number the workflow-validation rule (spec §5.1)
     * checks Task N against Task N-1's total.
     */
    @Query(
        """
        SELECT COALESCE(SUM(jointQuantity), 0) FROM progress_entries
        WHERE projectId = :projectId AND areaId = :areaId AND taskId = :taskId AND diameter = :diameter
        """
    )
    suspend fun cumulativeJoints(projectId: Long, areaId: Long, taskId: Long, diameter: Double): Int

    /** Same as [cumulativeJoints] but excludes one entry — used when re-validating an edit so the entry being changed doesn't count against itself. */
    @Query(
        """
        SELECT COALESCE(SUM(jointQuantity), 0) FROM progress_entries
        WHERE projectId = :projectId AND areaId = :areaId AND taskId = :taskId AND diameter = :diameter
        AND id != :excludeEntryId
        """
    )
    suspend fun cumulativeJointsExcluding(projectId: Long, areaId: Long, taskId: Long, diameter: Double, excludeEntryId: Long): Int

    @Query("SELECT * FROM progress_entries WHERE id = :id")
    suspend fun getById(id: Long): ProgressEntry?

    @Query("SELECT COALESCE(SUM(pipeInches), 0.0) FROM progress_entries WHERE projectId = :projectId")
    fun observeTotalPipeInches(projectId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(jointQuantity), 0) FROM progress_entries WHERE projectId = :projectId")
    fun observeTotalJoints(projectId: Long): Flow<Int>

    @Insert
    suspend fun insert(entry: ProgressEntry): Long

    @Update
    suspend fun update(entry: ProgressEntry)

    @Delete
    suspend fun delete(entry: ProgressEntry)
}
