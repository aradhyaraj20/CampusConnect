package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Report
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<Report>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: Report)

    @Query("SELECT COUNT(*) FROM reports WHERE status = 'PENDING'")
    suspend fun getPendingReportCount(): Int

    @Query("UPDATE reports SET status = :status WHERE id = :reportId")
    suspend fun updateReportStatus(reportId: Int, status: String): Int
}
