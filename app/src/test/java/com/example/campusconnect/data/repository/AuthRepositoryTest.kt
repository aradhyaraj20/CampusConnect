package com.example.campusconnect.data.repository

import com.example.campusconnect.core.common.Result
import com.example.campusconnect.core.security.CryptoUtils
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.User
import com.example.campusconnect.testutil.FakeContext
import com.example.campusconnect.testutil.fakeDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryTest {

    private class Fixture(invite: String = "", seed: List<User> = emptyList()) {
        val users = seed.toMutableList()
        val session = SessionManager(FakeContext())
        private var nextId = 100
        private val dao = fakeDao<UserDao>(
            mapOf(
                "getUserByEmail" to { a -> users.firstOrNull { it.email == a[0] } },
                "getUserById" to { a -> users.firstOrNull { it.id == a[0] } },
                "insertUser" to { a -> users += (a[0] as User).copy(id = nextId++); 1L }
            )
        )
        val repo = AuthRepository(dao, session, invite)
    }

    private fun user(email: String, password: String, role: String = "STUDENT", status: String = "ACTIVE") = User(
        id = 1, name = "N", email = email, password_hash = CryptoUtils.hashPassword(password),
        created_at = "t", role = role, account_status = status
    )

    private fun <T> Result<T>.errorMessage(): String? = (this as? Result.Error)?.exception?.message

    // ---- register ----

    @Test
    fun register_rejectsBadEmail() = runBlocking {
        val r = Fixture().repo.register("A", "not-an-email", "password1")
        assertEquals("Enter a valid email address", r.errorMessage())
    }

    @Test
    fun register_rejectsShortPassword() = runBlocking {
        val r = Fixture().repo.register("A", "a@x.com", "short")
        assertTrue(r.errorMessage()!!.contains("at least 8"))
    }

    @Test
    fun register_rejectsDuplicateEmail() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1")))
        assertEquals("User with this email already exists", f.repo.register("A", "a@x.com", "password1").errorMessage())
    }

    @Test
    fun register_studentGetsSessionAndHashedPassword() = runBlocking {
        val f = Fixture()
        val r = f.repo.register("Asha", "asha@x.com", "password1", course = "BCA", courseDurationMonths = 36)
        val created = (r as Result.Success).data
        assertEquals("STUDENT", created.role)
        assertNotEquals("password1", created.password_hash)
        assertTrue(CryptoUtils.verifyPassword("password1", created.password_hash))
        assertNotNull(created.course_end_at)
        assertTrue(f.repo.isLoggedIn())
    }

    @Test
    fun register_organizerBlockedWhenInviteDisabled() = runBlocking {
        val f = Fixture(invite = "")
        val r = f.repo.register("O", "o@x.com", "password1", role = "ORGANIZER", inviteCode = "")
        assertEquals("Invalid organizer invite code", r.errorMessage())
        assertFalse(f.repo.organizerSignupEnabled)
    }

    @Test
    fun register_organizerNeedsCorrectInviteCode() = runBlocking {
        val f = Fixture(invite = "SECRET")
        val bad = f.repo.register("O", "o@x.com", "password1", "ORGANIZER", inviteCode = "wrong")
        assertEquals("Invalid organizer invite code", bad.errorMessage())

        val ok = f.repo.register("O", "o@x.com", "password1", "ORGANIZER", inviteCode = " SECRET ")
        assertEquals("ORGANIZER", (ok as Result.Success).data.role)
        assertNull(ok.data.course_end_at)
    }

    @Test
    fun register_adminRoleCanNeverBeSelfAssigned() = runBlocking {
        val r = Fixture().repo.register("X", "x@x.com", "password1", role = "ADMIN")
        assertEquals("STUDENT", (r as Result.Success).data.role)
    }

    // ---- login ----

    @Test
    fun login_success_savesSession() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1", role = "ORGANIZER")))
        assertTrue(f.repo.login("a@x.com", "password1") is Result.Success)
        assertEquals(1, f.repo.getCurrentUserId())
        assertEquals("ORGANIZER", f.repo.getCurrentUserRole())
    }

    @Test
    fun login_wrongPasswordAndUnknownEmail_shareOneMessage() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1")))
        assertEquals("Invalid email or password", f.repo.login("a@x.com", "nope").errorMessage())
        assertEquals("Invalid email or password", f.repo.login("ghost@x.com", "nope").errorMessage())
        assertFalse(f.repo.isLoggedIn())
    }

    @Test
    fun login_suspendedAccountIsRejected() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1", status = "SUSPENDED")))
        assertEquals("This account has been suspended", f.repo.login("a@x.com", "password1").errorMessage())
        assertFalse(f.repo.isLoggedIn())
    }

    @Test
    fun login_locksOutAfterFiveFailures_evenWithCorrectPassword() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1")))
        repeat(5) { f.repo.login("a@x.com", "bad") }
        assertTrue(f.repo.login("a@x.com", "password1").errorMessage()!!.startsWith("Too many failed attempts"))
        assertTrue(f.session.lockoutRemainingMs("A@x.com") > 0)
    }

    @Test
    fun login_successClearsFailureCounter() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1")))
        repeat(4) { f.repo.login("a@x.com", "bad") }
        assertTrue(f.repo.login("a@x.com", "password1") is Result.Success)
        repeat(4) { f.repo.login("a@x.com", "bad") }
        assertEquals(0L, f.session.lockoutRemainingMs("a@x.com"))
    }

    // ---- refreshSession ----

    @Test
    fun refreshSession_restoresRoleFromDatabase_notFromPrefs() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1", role = "STUDENT")))
        f.session.saveUserSession(1, "N", "a@x.com", role = "ADMIN") // tampered prefs
        assertEquals("STUDENT", f.repo.refreshSession())
        assertEquals("STUDENT", f.repo.getCurrentUserRole())
    }

    @Test
    fun refreshSession_clearsSessionWhenAccountSuspendedOrGone() = runBlocking {
        val f = Fixture(seed = listOf(user("a@x.com", "password1", status = "SUSPENDED")))
        f.session.saveUserSession(1, "N", "a@x.com")
        assertNull(f.repo.refreshSession())
        assertFalse(f.repo.isLoggedIn())

        f.session.saveUserSession(42, "Ghost", "g@x.com")
        assertNull(f.repo.refreshSession())
        assertFalse(f.repo.isLoggedIn())
    }

    // ---- changePassword ----

    private class ChangeFixture(u: User) {
        val updated = mutableListOf<String>()
        val session = SessionManager(FakeContext())
        val repo = AuthRepository(
            fakeDao<UserDao>(
                mapOf(
                    "getUserById" to { a -> if (a[0] == u.id) u else null },
                    "updatePassword" to { a -> updated += a[1] as String }
                )
            ),
            session
        )
    }

    @Test
    fun changePassword_wrongCurrent_countsTowardLockout() = runBlocking {
        val f = ChangeFixture(user("a@x.com", "password1"))
        repeat(5) { assertEquals(PasswordChangeResult.WRONG_CURRENT, f.repo.changePassword(1, "nope", "newpassword1")) }
        assertEquals(PasswordChangeResult.LOCKED, f.repo.changePassword(1, "password1", "newpassword1"))
        assertTrue(f.updated.isEmpty())
    }

    @Test
    fun changePassword_success_storesAVerifiableHash() = runBlocking {
        val f = ChangeFixture(user("a@x.com", "password1"))
        assertEquals(PasswordChangeResult.SUCCESS, f.repo.changePassword(1, "password1", "newpassword1"))
        assertTrue(CryptoUtils.verifyPassword("newpassword1", f.updated.single()))
    }

    @Test
    fun changePassword_rejectsShortSameAndUnknownUser() = runBlocking {
        val f = ChangeFixture(user("a@x.com", "password1"))
        assertEquals(PasswordChangeResult.TOO_SHORT, f.repo.changePassword(1, "password1", "short"))
        assertEquals(PasswordChangeResult.SAME, f.repo.changePassword(1, "password1", "password1"))
        assertEquals(PasswordChangeResult.NO_USER, f.repo.changePassword(99, "password1", "newpassword1"))
        assertTrue(f.updated.isEmpty())
    }
}
