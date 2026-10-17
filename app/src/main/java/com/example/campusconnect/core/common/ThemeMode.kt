package com.example.campusconnect.core.common

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.example.campusconnect.core.security.SessionManager

/**
 * The one place the app decides light/dark. By default it follows the phone's setting; the
 * Settings screen can turn that off and force dark or light instead. Applied once at app start
 * and again whenever Settings changes it.
 */
object ThemeMode {
    private const val PREFS = "campus_connect_prefs"
    const val KEY_FOLLOW_SYSTEM = "follow_system_theme"

    fun followsSystem(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_FOLLOW_SYSTEM, true)

    fun setFollowsSystem(context: Context, follow: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_FOLLOW_SYSTEM, follow).apply()
    }

    fun apply(context: Context) {
        val mode = when {
            followsSystem(context) -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            SessionManager.getInstance(context).isDarkModeEnabled() -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
