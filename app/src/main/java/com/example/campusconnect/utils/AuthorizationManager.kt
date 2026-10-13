package com.example.campusconnect.utils

import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.models.Event
import kotlinx.coroutines.flow.first

object AuthorizationManager {

    suspend fun canAccessRestrictedContent(userId: Int, event: Event, rsvpDao: RsvpDao): Boolean {
        // 1. Organizer always has access
        if (event.creator_id == userId) return true

        // 2. Public events might still require RSVP for chat? 
        // Requirements say: "ONLY enrolled/authorized users should be allowed to access protected event resources."
        
        val rsvp = rsvpDao.getUserRSVPForEvent(userId, event.id)
        return rsvp != null && rsvp.status == "going"
    }

    /**
     * Gate for event chat and polls, checked by the destination screen itself:
     * the creator, an admin, or a student with a "going" RSVP.
     */
    suspend fun canOpenEventSpace(db: AppDatabase, userId: Int, eventId: Int): Boolean {
        if (userId <= 0) return false
        val event = db.eventDao().getEventById(eventId) ?: return false
        if (AccessPolicy.canAccessAdmin(db.userDao().getUserById(userId)?.role)) return true
        return canAccessRestrictedContent(userId, event, db.rsvpDao())
    }

    fun isOrganizer(userId: Int, event: Event): Boolean {
        return event.creator_id == userId
    }
}
