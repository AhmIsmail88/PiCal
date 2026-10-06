package com.ahmedismail.flowtrack.data.dao

import androidx.room.*
import com.ahmedismail.flowtrack.data.entity.Area
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {
    @Query("SELECT * FROM areas WHERE projectId = :projectId ORDER BY id")
    fun observeForProject(projectId: Long): Flow<List<Area>>

    @Insert
    suspend fun insert(area: Area): Long

    @Update
    suspend fun update(area: Area)

    @Delete
    suspend fun delete(area: Area)
}
