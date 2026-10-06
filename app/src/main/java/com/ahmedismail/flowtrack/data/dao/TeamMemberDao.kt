package com.ahmedismail.flowtrack.data.dao

import androidx.room.*
import com.ahmedismail.flowtrack.data.entity.TeamMember
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamMemberDao {
    @Query("SELECT * FROM team_members WHERE projectId = :projectId ORDER BY name")
    fun observeForProject(projectId: Long): Flow<List<TeamMember>>

    @Insert
    suspend fun insert(member: TeamMember): Long

    @Update
    suspend fun update(member: TeamMember)

    @Delete
    suspend fun delete(member: TeamMember)
}
