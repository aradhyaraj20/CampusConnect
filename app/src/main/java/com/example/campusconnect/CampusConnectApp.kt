package com.example.campusconnect

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.campusconnect.core.ai.AIConfig
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.worker.AccountCleanupWorker

/**
 * Single startup hook for process-wide setup.
 *
 * Previously each Activity re-read the dark-mode pref and called setDefaultNightMode()
 * itself, which caused a visible theme flip on some screens. Doing it once here means
 * every Activity starts in the right theme.
 */
class CampusConnectApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Must run before any AI call so the admin-entered runtime key is visible.
        AIConfig.init(this)

        val session = SessionManager.getInstance(this)
        AppCompatDelegate.setDefaultNightMode(
            if (session.isDarkModeEnabled()) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // Daily sweep: removes STUDENT accounts whose course ended 2+ months ago
        // and who haven't logged back in since (see AccountCleanupWorker).
        AccountCleanupWorker.schedule(this)
    }
}
