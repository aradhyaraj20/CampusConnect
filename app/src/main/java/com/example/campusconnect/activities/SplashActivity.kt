package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.campusconnect.R
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.core.common.RoleRouter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.utils.SeedData
import kotlinx.coroutines.launch
import com.example.campusconnect.data.repository.AuthRepository

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light/dark is applied once in CampusConnectApp (ThemeMode).

        setContentView(R.layout.activity_splash)

        // Section 5: LOGIN -> SESSION CHECK -> role dashboard. A returning, still-logged-in
        // user should never be sent back through Login.
        val sessionManager = SessionManager.getInstance(this)
        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            SeedData.seedIfEmpty(db)
            // Verify the stored role against the database rather than trusting prefs.
            val role = if (sessionManager.isLoggedIn()) {
                AuthRepository(db.userDao(), sessionManager).refreshSession()
            } else null
            if (role != null) {
                RoleRouter.routeToDashboard(this@SplashActivity, role)
            } else {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
            }
            finish()
        }
    }
}
