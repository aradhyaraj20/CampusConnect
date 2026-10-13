package com.example.campusconnect.data.repository

import com.example.campusconnect.core.common.Result
import com.example.campusconnect.core.error.AppException
import com.example.campusconnect.core.security.CryptoUtils
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AuthRepository(
    private val userDao: UserDao,
    private val sessionManager: SessionManager,
    // Empty = organizer self-signup disabled.
    private val organizerInviteCode: String = ""
) {

    val organizerSignupEnabled: Boolean get() = organizerInviteCode.isNotBlank()

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val locked = sessionManager.lockoutRemainingMs(email)
            if (locked > 0) {
                val minutes = (locked + 59_999) / 60_000
                return Result.Error(AppException.AuthenticationException("Too many failed attempts. Try again in $minutes min."))
            }

            val user = userDao.getUserByEmail(email)
            if (user == null || !CryptoUtils.verifyPassword(password, user.password_hash)) {
                sessionManager.recordFailedLogin(email)
                return Result.Error(AppException.AuthenticationException("Invalid email or password"))
            }
            sessionManager.clearFailedLogins(email)

            // Upgrade legacy unsalted hashes to PBKDF2 now that we have the plaintext.
            if (CryptoUtils.needsRehash(user.password_hash)) {
                userDao.updatePassword(user.id, CryptoUtils.hashPassword(password))
            }

            if (user.account_status.equals("SUSPENDED", ignoreCase = true)) {
                return Result.Error(AppException.AuthenticationException("This account has been suspended"))
            }

            // Track activity so AccountCleanupWorker doesn't purge a still-active
            // student just because their course has formally ended.
            userDao.updateLastLogin(user.id, System.currentTimeMillis())

            sessionManager.saveUserSession(
                userId = user.id,
                userName = user.name,
                userEmail = user.email,
                role = user.role
            )

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(AppException.DatabaseException("Login error occurred", e))
        }
    }

    // role defaults to STUDENT; RegisterActivity passes ORGANIZER when that signup
    // path is chosen. ADMIN accounts are never self-registered - they're seeded/promoted.
    // course/courseDurationMonths are only meaningful for STUDENT accounts: they
    // scope the karma leaderboard to classmates on the same course, and drive the
    // 2-month post-course inactivity cleanup.
    suspend fun register(
        name: String,
        email: String,
        password: String,
        role: String = "STUDENT",
        course: String? = null,
        courseDurationMonths: Int? = null,
        inviteCode: String? = null
    ): Result<User> {
        return try {
            if (!EMAIL_REGEX.matches(email)) {
                return Result.Error(AppException.ValidationException("Enter a valid email address"))
            }
            if (password.length < MIN_PASSWORD_LENGTH) {
                return Result.Error(AppException.ValidationException("Password must be at least $MIN_PASSWORD_LENGTH characters"))
            }
            // Enforced here (not just in the UI): only STUDENT can be self-registered
            // unless a valid organizer invite code is supplied.
            if (role == "ORGANIZER" &&
                (!organizerSignupEnabled || inviteCode?.trim() != organizerInviteCode)
            ) {
                return Result.Error(AppException.ValidationException("Invalid organizer invite code"))
            }

            val existing = userDao.getUserByEmail(email)
            if (existing != null) {
                return Result.Error(AppException.ValidationException("User with this email already exists"))
            }

            val safeRole = if (role == "ORGANIZER") "ORGANIZER" else "STUDENT"

            val hashedPassword = CryptoUtils.hashPassword(password)
            val now = Date()
            val createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now)

            val courseEndAt: Long? = if (safeRole == "STUDENT" && courseDurationMonths != null && courseDurationMonths > 0) {
                Calendar.getInstance().apply {
                    time = now
                    add(Calendar.MONTH, courseDurationMonths)
                }.timeInMillis
            } else null

            val newUser = User(
                name = name,
                email = email,
                password_hash = hashedPassword,
                created_at = createdAt,
                role = safeRole,
                course = if (safeRole == "STUDENT") course else null,
                course_duration_months = if (safeRole == "STUDENT") courseDurationMonths else null,
                course_end_at = courseEndAt,
                last_login_at = System.currentTimeMillis()
            )

            userDao.insertUser(newUser)
            val createdUser = userDao.getUserByEmail(email)
                ?: return Result.Error(AppException.DatabaseException("Failed to retrieve created user"))

            sessionManager.saveUserSession(
                userId = createdUser.id,
                userName = createdUser.name,
                userEmail = createdUser.email,
                role = createdUser.role
            )

            Result.Success(createdUser)
        } catch (e: Exception) {
            Result.Error(AppException.DatabaseException("Registration error occurred", e))
        }
    }

    /**
     * Re-reads the role/status from the database so a tampered SharedPreferences
     * value can't grant access. Returns the verified role, or null (and clears
     * the session) if the account is gone or suspended.
     */
    suspend fun refreshSession(): String? {
        val id = sessionManager.getCurrentUserId()
        if (id <= 0) return null
        val user = userDao.getUserById(id)
        if (user == null || user.account_status.equals("SUSPENDED", ignoreCase = true)) {
            sessionManager.clearSession()
            return null
        }
        // Staying logged in counts as activity: AccountCleanupWorker must not purge
        // a student who opens the app daily but never re-types their password.
        userDao.updateLastLogin(user.id, System.currentTimeMillis())
        sessionManager.saveUserSession(user.id, user.name, user.email, user.role)
        return user.role
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun getCurrentUserId(): Int = sessionManager.getCurrentUserId()

    fun getCurrentUserRole(): String = sessionManager.getCurrentUserRole()

    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()

    private companion object {
        const val MIN_PASSWORD_LENGTH = 8
        val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
