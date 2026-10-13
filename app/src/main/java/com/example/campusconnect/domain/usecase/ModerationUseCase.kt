package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.models.Report

class ModerationUseCase(private val reportDao: ReportDao) {

    suspend fun submitReport(reporterUserId: Int, targetType: String, targetId: Int, reason: String) {
        val report = Report(
            reportedBy = reporterUserId,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            status = "PENDING",
            timestamp = System.currentTimeMillis()
        )
        reportDao.insertReport(report)
    }

    suspend fun resolveReport(reportId: Int, action: String) {
        // action: "RESOLVED", "DISMISSED"
        reportDao.updateReportStatus(reportId, action)
    }
}
