package com.example.campusconnect.utils

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

    fun isOrganizer(userId: Int, event: Event): Boolean {
        return event.creator_id == userId
    }
}
