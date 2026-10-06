package com.ahmedismail.flowtrack.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DiameterUnit { INCH, MM }

@Entity(
    tableName = "progress_entries",
    foreignKeys = [
        ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskItem::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Area::class, parentColumns = ["id"], childColumns = ["areaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TeamMember::class, parentColumns = ["id"], childColumns = ["performedById"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = TeamMember::class, parentColumns = ["id"], childColumns = ["supervisorId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("projectId"), Index("taskId"), Index("areaId"),
        Index("performedById"), Index("supervisorId"),
        Index(value = ["projectId", "areaId", "diameter", "taskId"]) // validation lookup path
    ]
)
data class ProgressEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val taskId: Long,
    val areaId: Long,
    val date: Long,               // epoch millis, defaults to "today" in the UI
    val diameter: Double,
    val unit: DiameterUnit,
    val jointQuantity: Int,
    val pipeInches: Double,       // computed = diameter * jointQuantity, stored for fast reporting
    val performedById: Long?,
    val supervisorId: Long?,
    val photoUri: String? = null,  // content:// or file:// path to an attached photo (camera or gallery)
    val hoursWorked: Double? = null // optional — powers the per-employee report's total hours
)
