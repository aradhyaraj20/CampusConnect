package com.example.campusconnect.core.security

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.data.repository.AuthRepository
import com.example.campusconnect.database.AppDatabase
import kotlinx.coroutines.launch

/**
 * The cached session role is only a hint. Privileged screens call [enforce] so the role
 * and account status are re-read from the database; a demoted, suspended or deleted
 * account (or a tampered SharedPreferences value) is sent back out of the screen.
 */
object RoleGuard {

    fun enforce(activity: AppCompatActivity, allowed: (String?) -> Boolean) {
        activity.lifecycleScope.launch {
            val db = AppDatabase.getDatabase(activity)
            val role = AuthRepository(db.userDao(), SessionManager.getInstance(activity)).refreshSession()
            if (role == null || !allowed(role)) {
                Toast.makeText(activity, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                activity.finish()
            }
        }
    }
}
