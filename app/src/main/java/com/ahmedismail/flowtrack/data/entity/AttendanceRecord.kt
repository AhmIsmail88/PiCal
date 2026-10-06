package com.ahmedismail.flowtrack.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AttendanceStatus { PRESENT, LATE, ABSENT }

/**
 * One attendance mark per Team Member per day, entered by the supervisor/
 * engineer (not a self-check-in system — see spec's single-recorder model).
 * Present/Late carry an optional check-in and check-out timestamp, each
 * auto-stamped to "now" the first time that state is set and freely
 * editable afterward for late/backdated entry. Absent carries neither.
 */
@Entity(
    tableName = "attendance_records",
    foreignKeys = [
        ForeignKey(entity = Project::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TeamMember::class, parentColumns = ["id"], childColumns = ["teamMemberId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [
        Index("projectId"),
        Index(value = ["teamMemberId", "date"], unique = true)
    ]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val teamMemberId: Long,
    val date: Long, // normalized to start-of-day millis — one record per member per day
    val status: AttendanceStatus,
    val checkInMillis: Long? = null,
    val checkOutMillis: Long? = null
)
