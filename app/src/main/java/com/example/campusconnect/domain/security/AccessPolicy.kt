package com.example.campusconnect.domain.security

/**
 * Role checks used by UI, ViewModel and tests. Hiding a button is not sufficient —
 * callers must still consult this before organizer/admin operations.
 */
object AccessPolicy {

    fun normalize(role: String?): String = role?.uppercase()?.trim().orEmpty().ifBlank { "STUDENT" }

    fun canAccessStudentHome(role: String?): Boolean = true

    fun canAccessOrganizerDashboard(role: String?): Boolean =
        normalize(role) in setOf("ORGANIZER", "ADMIN")

    fun canAccessAdmin(role: String?): Boolean = normalize(role) == "ADMIN"

    fun canCreateEvents(role: String?): Boolean = canAccessOrganizerDashboard(role)

    fun canScanAttendance(role: String?): Boolean = canAccessOrganizerDashboard(role)

    fun canModerate(role: String?): Boolean = canAccessAdmin(role)

    fun canManageUsers(role: String?): Boolean = canAccessAdmin(role)

    fun studentBlockedFromAdmin(role: String?): Boolean = !canAccessAdmin(role)

    fun studentBlockedFromOrganizerOps(role: String?): Boolean = !canCreateEvents(role)
}
