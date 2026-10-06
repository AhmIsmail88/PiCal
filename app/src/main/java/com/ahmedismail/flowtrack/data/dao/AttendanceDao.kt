package com.ahmedismail.flowtrack.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ahmedismail.flowtrack.data.entity.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE projectId = :projectId AND date = :date")
    fun observeForProjectAndDate(projectId: Long, date: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE teamMemberId = :memberId ORDER BY date DESC")
    fun observeForMember(memberId: Long): Flow<List<AttendanceRecord>>

    /** REPLACE + the unique (teamMemberId, date) index gives upsert-by-day behavior for free. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: AttendanceRecord): Long

    @Delete
    suspend fun delete(record: AttendanceRecord)
}
