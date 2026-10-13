package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.RSVP
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RSVPUseCases(
    private val rsvpDao: RsvpDao,
    private val eventDao: EventDao,
    private val notificationDao: NotificationDao
) {

    suspend fun rsvpOrWaitlist(userId: Int, eventId: Int): String {
        val event = eventDao.getEventById(eventId) ?: return "EVENT_NOT_FOUND"
        val count = rsvpDao.getAttendeeCount(eventId)
        val maxAttendees = event.max_attendees ?: 0
        val isFull = maxAttendees > 0 && count >= maxAttendees
        val status = if (isFull) "waitlisted" else "going"

        val rsvp = RSVP(
            user_id = userId,
            event_id = eventId,
            status = status,
            checked_in = false,
            joined_at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )

        rsvpDao.insertRSVP(rsvp)
        return status
    }

    suspend fun cancelRsvpAndPromoteWaitlist(userId: Int, eventId: Int) {
        val existingRsvp = rsvpDao.getUserRSVPForEvent(userId, eventId) ?: return
        val wasGoing = existingRsvp.status == "going"

        // Cancel RSVP
        rsvpDao.deleteRsvp(existingRsvp)

        // If user was GOING, check if someone on waitlist can be promoted
        if (wasGoing) {
            val nextWaitlisted = rsvpDao.getEarliestWaitlistedUser(eventId)
            if (nextWaitlisted != null) {
                val promotedRsvp = nextWaitlisted.copy(status = "going")
                rsvpDao.updateRsvp(promotedRsvp)

                val event = eventDao.getEventById(eventId)
                val eventTitle = event?.title ?: "an event"

                // Notify promoted user
                notificationDao.insertNotification(
                    Notification(
                        user_id = promotedRsvp.user_id,
                        title = "Spot Available! 🎉",
                        message = "You have been promoted from the waitlist for '$eventTitle'!",
                        timestamp = System.currentTimeMillis(),
                        type = "event_update",
                        event_id = eventId
                    )
                )
            }
        }
    }
}
