package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.Report
import com.example.campusconnect.models.User
import com.example.campusconnect.testutil.fakeDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModerateReportUseCaseTest {

    private class Fixture(user: User?, actor: User? = ADMIN) {
        val statuses = mutableListOf<Pair<Int, String>>()
        val roles = mutableListOf<Pair<Int, String>>()
        val reportUpdates = mutableListOf<Pair<Int, String>>()
        val useCase = ModerateReportUseCase(
            reportDao = fakeDao<ReportDao>(
                mapOf("updateReportStatus" to { a -> reportUpdates += (a[0] as Int) to (a[1] as String) })
            ),
            userDao = fakeDao<UserDao>(
                mapOf(
                    "getUserById" to { a -> if (a[0] == ACTOR_ID) actor else user },
                    "updateAccountStatus" to { a -> statuses += (a[0] as Int) to (a[1] as String) },
                    "updateRole" to { a -> roles += (a[0] as Int) to (a[1] as String) }
                )
            ),
            eventDao = fakeDao<EventDao>(emptyMap()),
            actorId = ACTOR_ID
        )
    }

    private companion object {
        const val ACTOR_ID = 1
        val ADMIN = User(id = ACTOR_ID, name = "A", email = "a@x.com", password_hash = "h", created_at = "c", role = "ADMIN")
    }

    @Test
    fun nonAdminActor_cannotModerate() = runBlocking {
        val organizer = ADMIN.copy(role = "ORGANIZER")
        val f = Fixture(user(), actor = organizer)
        assertEquals(ModerationOutcome.NOT_ALLOWED, f.useCase.suspendUser(5))
        assertEquals(ModerationOutcome.NOT_ALLOWED, f.useCase.changeRole(5, "ORGANIZER"))
        assertEquals(ModerationOutcome.NOT_ALLOWED, f.useCase.dismiss(Report(id = 9, reportedBy = 2, targetType = "USER", targetId = 5, reason = "r", timestamp = 0)))
        assertTrue(f.statuses.isEmpty() && f.roles.isEmpty() && f.reportUpdates.isEmpty())
    }

    @Test
    fun suspendedAdminActor_cannotModerate() = runBlocking {
        val f = Fixture(user(), actor = ADMIN.copy(account_status = "SUSPENDED"))
        assertEquals(ModerationOutcome.NOT_ALLOWED, f.useCase.warnUser(5))
    }

    private fun user(role: String = "STUDENT", status: String = "ACTIVE") =
        User(id = 5, name = "N", email = "n@x.com", password_hash = "h", created_at = "c", role = role, account_status = status)

    @Test
    fun suspendingAnAdmin_isRefused() = runBlocking {
        val f = Fixture(user(role = "ADMIN"))
        assertEquals(ModerationOutcome.PROTECTED_ADMIN, f.useCase.suspendUser(5))
        assertTrue(f.statuses.isEmpty())
    }

    @Test
    fun warningASuspendedUser_doesNotLiftTheSuspension() = runBlocking {
        val f = Fixture(user(status = "SUSPENDED"))
        assertEquals(ModerationOutcome.NO_CHANGE, f.useCase.warnUser(5))
        assertTrue(f.statuses.isEmpty())
    }

    @Test
    fun suspendingMissingUser_reportsNotFound() = runBlocking {
        assertEquals(ModerationOutcome.NOT_FOUND, Fixture(null).useCase.suspendUser(5))
    }

    @Test
    fun suspendingActiveStudent_applies() = runBlocking {
        val f = Fixture(user())
        assertEquals(ModerationOutcome.APPLIED, f.useCase.suspendUser(5))
        assertEquals(5 to "SUSPENDED", f.statuses.single())
    }

    @Test
    fun changeRole_neverTouchesAdminsOrCreatesAdmins() = runBlocking {
        assertEquals(ModerationOutcome.PROTECTED_ADMIN, Fixture(user(role = "ADMIN")).useCase.changeRole(5, "STUDENT"))
        val f = Fixture(user())
        assertEquals(ModerationOutcome.NO_CHANGE, f.useCase.changeRole(5, "ADMIN"))
        assertEquals(ModerationOutcome.APPLIED, f.useCase.changeRole(5, "ORGANIZER"))
        assertEquals(5 to "ORGANIZER", f.roles.single())
    }

    @Test
    fun removingContentOfAdminUser_leavesReportOpen() = runBlocking {
        val f = Fixture(user(role = "ADMIN"))
        val report = Report(id = 1, reportedBy = 2, targetType = "USER", targetId = 5, reason = "r", status = "PENDING", timestamp = 0)
        assertEquals(ModerationOutcome.PROTECTED_ADMIN, f.useCase.removeContent(report))
        assertTrue(f.reportUpdates.isEmpty())
    }
}
