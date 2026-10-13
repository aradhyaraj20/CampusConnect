package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.AttendanceDao
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao

data class SystemAnalytics(
    val totalUsers: Int,
    val totalEvents: Int,
    val totalRsvps: Int,
    val totalCheckedIn: Int,
    val attendanceRatePercent: Float
)

class AdminAnalyticsUseCase(
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val attendanceDao: AttendanceDao
) {

    suspend fun getSystemAnalytics(): SystemAnalytics {
        val totalUsers = userDao.getUserById(1)?.id ?: 1 // Placeholder query or aggregate
        val totalEvents = eventDao.getEventCount()
        
        // Compute metrics
        val totalRsvps = 120 // Example aggregated count
        val totalCheckedIn = 95
        val rate = if (totalRsvps > 0) (totalCheckedIn.toFloat() / totalRsvps) * 100f else 0f

        return SystemAnalytics(
            totalUsers = totalUsers,
            totalEvents = totalEvents,
            totalRsvps = totalRsvps,
            totalCheckedIn = totalCheckedIn,
            attendanceRatePercent = rate
        )
    }
}
