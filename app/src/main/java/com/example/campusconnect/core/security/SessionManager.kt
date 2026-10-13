package com.example.campusconnect.core.security

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "campus_connect_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_OFFLINE_MODE = "offline_mode"
        private const val MAX_LOGIN_ATTEMPTS = 5
        private const val LOCKOUT_MS = 5 * 60_000L

        @Volatile
        private var INSTANCE: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    fun saveUserSession(userId: Int, userName: String, userEmail: String, role: String = "STUDENT") {
        prefs.edit().apply {
            putInt(KEY_USER_ID, userId)
            putString(KEY_USER_NAME, userName)
            putString(KEY_USER_EMAIL, userEmail)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }

    fun getCurrentUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun getCurrentUserName(): String = prefs.getString(KEY_USER_NAME, "Campus Student") ?: "Campus Student"

    fun getCurrentUserEmail(): String = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    fun getCurrentUserRole(): String = prefs.getString(KEY_USER_ROLE, "STUDENT") ?: "STUDENT"

    fun isLoggedIn(): Boolean = prefs.contains(KEY_USER_ID) && prefs.getInt(KEY_USER_ID, -1) > 0

    fun isDarkModeEnabled(): Boolean = prefs.getBoolean(KEY_DARK_MODE, false)

    fun setDarkModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isOfflineModeEnabled(): Boolean = prefs.getBoolean(KEY_OFFLINE_MODE, false)

    fun setOfflineModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_MODE, enabled).apply()
    }

    // --- Login throttling (per email, persisted so restarting the app doesn't reset it) ---

    /** Milliseconds remaining on a lockout for this email, or 0 if not locked. */
    fun lockoutRemainingMs(email: String, now: Long = System.currentTimeMillis()): Long =
        (prefs.getLong("lock_until_${email.lowercase()}", 0L) - now).coerceAtLeast(0L)

    fun recordFailedLogin(email: String, now: Long = System.currentTimeMillis()) {
        val key = email.lowercase()
        val fails = prefs.getInt("login_fails_$key", 0) + 1
        prefs.edit().apply {
            if (fails >= MAX_LOGIN_ATTEMPTS) {
                putInt("login_fails_$key", 0)
                putLong("lock_until_$key", now + LOCKOUT_MS)
            } else {
                putInt("login_fails_$key", fails)
            }
            apply()
        }
    }

    fun clearFailedLogins(email: String) {
        val key = email.lowercase()
        prefs.edit().remove("login_fails_$key").remove("lock_until_$key").apply()
    }

    fun clearSession() {
        prefs.edit().apply {
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_ROLE)
            apply()
        }
    }
}
