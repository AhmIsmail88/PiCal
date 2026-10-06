package com.ahmedismail.flowtrack.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TeamRole { ENGINEER, SUPERVISOR, WELDER, INSPECTOR, OTHER }

@Entity(
    tableName = "team_members",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class TeamMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val role: TeamRole,
    val customRoleLabel: String? = null // user-typed job title when role == OTHER
)
