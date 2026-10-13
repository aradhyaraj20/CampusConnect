package com.example.campusconnect.core.common

import android.content.Context
import android.content.Intent
import com.example.campusconnect.activities.MainActivity
import com.example.campusconnect.activities.admin.AdminDashboardActivity
import com.example.campusconnect.activities.organizer.OrganizerDashboardActivity
import com.example.campusconnect.domain.model.Role

/**
 * Section 5 of the CampusConnect spec: after LOGIN -> SESSION CHECK, the dashboard shown
 * must be determined dynamically from the authenticated user's role - never hardcoded.
 * This is the one place that decision is made.
 */
object RoleRouter {

    fun dashboardIntentFor(context: Context, role: String): Intent {
        val resolvedRole = try {
            Role.valueOf(role)
        } catch (e: IllegalArgumentException) {
            Role.STUDENT
        }
        val destination = when (resolvedRole) {
            Role.STUDENT -> MainActivity::class.java
            Role.ORGANIZER -> OrganizerDashboardActivity::class.java
            Role.ADMIN -> AdminDashboardActivity::class.java
        }
        return Intent(context, destination)
    }

    fun routeToDashboard(context: Context, role: String) {
        val intent = dashboardIntentFor(context, role).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
    }
}
