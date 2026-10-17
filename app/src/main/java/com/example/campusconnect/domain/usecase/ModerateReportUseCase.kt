package com.example.campusconnect.domain.usecase

import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Report

/** What an admin action did, so the UI can report the truth instead of assuming success. */
enum class ModerationOutcome { APPLIED, NOT_FOUND, PROTECTED_ADMIN, NO_CHANGE, NOT_ALLOWED }

/**
 * Admin moderation actions. Every action first re-reads [actorId] from the database and refuses
 * unless it is an active admin, so the check doesn't depend on which screen calls it.
 */
class ModerateReportUseCase(
    private val reportDao: ReportDao,
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val actorId: Int,
    private val transactions: TransactionRunner = TransactionRunner.None
) {
    private suspend fun actorIsAdmin(): Boolean {
        val actor = userDao.getUserById(actorId) ?: return false
        return AccessPolicy.canModerate(actor.role) && !actor.account_status.equals("SUSPENDED", ignoreCase = true)
    }

    suspend fun dismiss(report: Report): ModerationOutcome {
        if (!actorIsAdmin()) return ModerationOutcome.NOT_ALLOWED
        reportDao.updateReportStatus(report.id, "REJECTED")
        return ModerationOutcome.APPLIED
    }

    suspend fun resolve(report: Report): ModerationOutcome {
        if (!actorIsAdmin()) return ModerationOutcome.NOT_ALLOWED
        reportDao.updateReportStatus(report.id, "RESOLVED")
        return ModerationOutcome.APPLIED
    }

    /** Cancels the reported event or suspends the reported user, and resolves the report, atomically. */
    suspend fun removeContent(report: Report): ModerationOutcome = transactions.run {
        if (!actorIsAdmin()) return@run ModerationOutcome.NOT_ALLOWED
        when (report.targetType.uppercase()) {
            "EVENT" -> {
                if (eventDao.getEventById(report.targetId) == null) return@run ModerationOutcome.NOT_FOUND
                eventDao.updateEventStatus(report.targetId, "cancelled")
            }
            "USER" -> {
                val outcome = setStatus(report.targetId, "SUSPENDED")
                if (outcome != ModerationOutcome.APPLIED && outcome != ModerationOutcome.NO_CHANGE) return@run outcome
            }
        }
        reportDao.updateReportStatus(report.id, "RESOLVED")
        ModerationOutcome.APPLIED
    }

    /** A warning never overrides an existing suspension. */
    suspend fun warnUser(userId: Int): ModerationOutcome = setStatus(userId, "WARNED")

    suspend fun suspendUser(userId: Int): ModerationOutcome = setStatus(userId, "SUSPENDED")

    /** Only STUDENT <-> ORGANIZER; admins are never promoted, demoted or created here. */
    suspend fun changeRole(userId: Int, newRole: String): ModerationOutcome {
        if (!actorIsAdmin()) return ModerationOutcome.NOT_ALLOWED
        if (newRole != "STUDENT" && newRole != "ORGANIZER") return ModerationOutcome.NO_CHANGE
        val user = userDao.getUserById(userId) ?: return ModerationOutcome.NOT_FOUND
        if (user.role == "ADMIN") return ModerationOutcome.PROTECTED_ADMIN
        if (user.role == newRole) return ModerationOutcome.NO_CHANGE
        userDao.updateRole(userId, newRole)
        return ModerationOutcome.APPLIED
    }

    private suspend fun setStatus(userId: Int, status: String): ModerationOutcome {
        if (!actorIsAdmin()) return ModerationOutcome.NOT_ALLOWED
        val user = userDao.getUserById(userId) ?: return ModerationOutcome.NOT_FOUND
        if (user.role == "ADMIN") return ModerationOutcome.PROTECTED_ADMIN
        if (user.account_status.equals(status, ignoreCase = true) ||
            (status == "WARNED" && user.account_status.equals("SUSPENDED", ignoreCase = true))
        ) return ModerationOutcome.NO_CHANGE
        userDao.updateAccountStatus(userId, status)
        return ModerationOutcome.APPLIED
    }
}
