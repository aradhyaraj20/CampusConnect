package com.example.campusconnect.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessPolicyTest {

    @Test
    fun studentCannotAccessAdminOrCreateEvents() {
        assertTrue(AccessPolicy.studentBlockedFromAdmin("STUDENT"))
        assertTrue(AccessPolicy.studentBlockedFromOrganizerOps("STUDENT"))
        assertFalse(AccessPolicy.canAccessAdmin("STUDENT"))
        assertFalse(AccessPolicy.canCreateEvents("STUDENT"))
        assertFalse(AccessPolicy.canScanAttendance("STUDENT"))
    }

    @Test
    fun organizerCanCreateButNotAdmin() {
        assertTrue(AccessPolicy.canCreateEvents("ORGANIZER"))
        assertTrue(AccessPolicy.canScanAttendance("ORGANIZER"))
        assertTrue(AccessPolicy.canAccessOrganizerDashboard("ORGANIZER"))
        assertFalse(AccessPolicy.canAccessAdmin("ORGANIZER"))
        assertFalse(AccessPolicy.canModerate("ORGANIZER"))
    }

    @Test
    fun adminCanAccessAdministrativeFunctions() {
        assertTrue(AccessPolicy.canAccessAdmin("ADMIN"))
        assertTrue(AccessPolicy.canModerate("ADMIN"))
        assertTrue(AccessPolicy.canManageUsers("ADMIN"))
        assertTrue(AccessPolicy.canCreateEvents("ADMIN"))
    }
}
