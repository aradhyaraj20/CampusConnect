package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Attendance
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE eventId = :eventId")
    fun getAttendanceForEvent(eventId: Int): Flow<List<Attendance>>

    @Query("SELECT * FROM attendance WHERE eventId = :eventId AND userId = :userId LIMIT 1")
    suspend fun getAttendanceForUser(eventId: Int, userId: Int): Attendance?

    @Query("SELECT COUNT(*) FROM attendance WHERE eventId = :eventId")
    suspend fun getCheckedInCount(eventId: Int): Int

    // Admin dashboard: platform-wide attendance total
    @Query("SELECT COUNT(*) FROM attendance")
    suspend fun getTotalAttendanceCount(): Int

    // Organizer dashboard: check-ins across all events this organizer created
    @Query("SELECT COUNT(*) FROM attendance WHERE eventId IN (SELECT id FROM events WHERE creator_id = :creatorId)")
    suspend fun getAttendanceCountForOrganizer(creatorId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordAttendance(attendance: Attendance)
}
