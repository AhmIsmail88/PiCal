package com.ahmedismail.flowtrack.data.dao

import androidx.room.*
import com.ahmedismail.flowtrack.data.entity.TaskItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY orderIndex")
    fun observeForProject(projectId: Long): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId ORDER BY orderIndex")
    suspend fun getForProject(projectId: Long): List<TaskItem>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskItem?

    /** The task immediately upstream of [orderIndex] in this project's Workflow, or null if it's stage 1. */
    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND orderIndex = :orderIndex - 1")
    suspend fun getPrevious(projectId: Long, orderIndex: Int): TaskItem?

    @Insert
    suspend fun insert(task: TaskItem): Long

    @Update
    suspend fun update(task: TaskItem)

    @Update
    suspend fun updateAll(tasks: List<TaskItem>)

    @Delete
    suspend fun delete(task: TaskItem)
}
