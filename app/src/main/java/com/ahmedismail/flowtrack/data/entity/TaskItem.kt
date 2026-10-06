package com.ahmedismail.flowtrack.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Task Type only controls which optional sub-field is shown at entry time
 * (spec §4). It never dictates order or validation — order comes purely
 * from [TaskItem.orderIndex], which the user sets by dragging in the
 * Workflow Settings screen.
 */
enum class TaskType {
    FABRICATION, WELDING, INSPECTION, TESTING, INSTALLATION, FINISHING, HANDOVER, OTHER
}

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,          // user-typed, kept as-typed regardless of display language
    val type: TaskType,
    val orderIndex: Int,       // 0-based position in this project's Workflow
    val extraFieldValue: String? = null // e.g. chosen Welding Process / Inspection Method
)
