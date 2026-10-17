package com.example.campusconnect.core.common

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.campusconnect.R
import com.example.campusconnect.activities.LoginActivity
import com.example.campusconnect.activities.SettingsActivity
import com.example.campusconnect.core.security.SessionManager

/**
 * One implementation of "sign out", used by every dashboard toolbar.
 *
 * Importantly this clears the SESSION only - an earlier version wiped all
 * SharedPreferences, which also destroyed the user's theme and notification choices.
 */
object AccountMenuHelper {

    fun handleMenuItem(activity: AppCompatActivity, itemId: Int): Boolean {
        return when (itemId) {
            R.id.action_settings -> {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
                true
            }
            R.id.action_logout -> {
                confirmLogout(activity)
                true
            }
            else -> false
        }
    }

    fun confirmLogout(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.logout) { _, _ ->
                SessionManager.getInstance(activity).clearSession()
                val intent = Intent(activity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                activity.startActivity(intent)
                activity.finish()
            }
            .show()
    }
}
