package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.Report

class ModerateReportUseCase(
    private val reportDao: ReportDao,
    private val userDao: UserDao,
    private val eventDao: EventDao
) {
    suspend fun dismiss(report: Report) = reportDao.updateReportStatus(report.id, "REJECTED")
    suspend fun resolve(report: Report) = reportDao.updateReportStatus(report.id, "RESOLVED")
    suspend fun removeContent(report: Report) {
        when (report.targetType.uppercase()) {
            "EVENT" -> eventDao.updateEventStatus(report.targetId, "cancelled")
            "USER" -> userDao.updateAccountStatus(report.targetId, "SUSPENDED")
        }
        reportDao.updateReportStatus(report.id, "RESOLVED")
    }
    suspend fun warnUser(userId: Int) = userDao.updateAccountStatus(userId, "WARNED")
    suspend fun suspendUser(userId: Int) = userDao.updateAccountStatus(userId, "SUSPENDED")
}
