package com.ahmedismail.flowtrack.data

import androidx.room.TypeConverter
import com.ahmedismail.flowtrack.data.entity.AttendanceStatus
import com.ahmedismail.flowtrack.data.entity.DiameterUnit
import com.ahmedismail.flowtrack.data.entity.TaskType
import com.ahmedismail.flowtrack.data.entity.TeamRole

class Converters {
    @TypeConverter
    fun fromTaskType(value: TaskType): String = value.name
    @TypeConverter
    fun toTaskType(value: String): TaskType = TaskType.valueOf(value)

    @TypeConverter
    fun fromTeamRole(value: TeamRole): String = value.name
    @TypeConverter
    fun toTeamRole(value: String): TeamRole = TeamRole.valueOf(value)

    @TypeConverter
    fun fromDiameterUnit(value: DiameterUnit): String = value.name
    @TypeConverter
    fun toDiameterUnit(value: String): DiameterUnit = DiameterUnit.valueOf(value)

    @TypeConverter
    fun fromAttendanceStatus(value: AttendanceStatus): String = value.name
    @TypeConverter
    fun toAttendanceStatus(value: String): AttendanceStatus = AttendanceStatus.valueOf(value)
}
