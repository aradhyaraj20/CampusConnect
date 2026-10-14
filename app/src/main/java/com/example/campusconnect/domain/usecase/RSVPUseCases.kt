package com.example.campusconnect.domain.usecase

import com.example.campusconnect.utils.DateFormats
import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.domain.event.RsvpPolicy
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.RSVP

class RSVPUseCases(
    private val rsvpDao: RsvpDao,
    private val eventDao: EventDao,
    private val notificationDao: NotificationDao,
    private val transactions: TransactionRunner = TransactionRunner.None
) {

    /**
     * Returns the user's RSVP status ("going" or "waitlisted"). The seat count and the insert
     * run in one transaction, and an existing RSVP is returned as-is instead of being re-decided.
     */
    suspend fun rsvpOrWaitlist(userId: Int, eventId: Int): String = transactions.run {
        val event = eventDao.getEventById(eventId) ?: return@run "EVENT_NOT_FOUND"
        rsvpDao.getUserRSVPForEvent(userId, eventId)?.let { return@run it.status }

        val count = rsvpDao.getAttendeeCount(eventId)
        val status = RsvpPolicy.statusLabel(RsvpPolicy.decideJoin(count, event.max_attendees))

        rsvpDao.insertRSVP(
            RSVP(
                user_id = userId,
                event_id = eventId,
                status = status,
                checked_in = false,
                joined_at = DateFormats.storedTimestamp()
            )
        )
        status
    }

    /** Deletes the RSVP and, if a seat opened, promotes the earliest waitlisted user, all atomically. */
    suspend fun cancelRsvpAndPromoteWaitlist(userId: Int, eventId: Int) = transactions.run {
        val existing = rsvpDao.getUserRSVPForEvent(userId, eventId) ?: return@run
        rsvpDao.deleteRsvp(existing)
        if (existing.status != "going") return@run

        val next = rsvpDao.getEarliestWaitlistedUser(eventId) ?: return@run
        rsvpDao.updateRsvp(next.copy(status = "going"))
        val title = eventDao.getEventById(eventId)?.title ?: "an event"
        notificationDao.insertNotification(
            Notification(
                user_id = next.user_id,
                title = "You're off the waitlist",
                message = "A seat opened for '$title'.",
                timestamp = System.currentTimeMillis(),
                type = "waitlist",
                event_id = eventId
            )
        )
    }
}
