package com.example.campusconnect.domain.usecase

import android.content.Context
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.domain.model.EventStatus
import com.example.campusconnect.models.Notification
import com.example.campusconnect.utils.ReminderManager
import kotlinx.coroutines.flow.first

class EventCancellationUseCase(
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val notificationDao: NotificationDao
) {

    suspend fun cancelEvent(context: Context, eventId: Int, organizerUserId: Int): Boolean {
        val event = eventDao.getEventById(eventId) ?: return false

        // Check if user is organizer or admin
        if (event.creator_id != organizerUserId) {
            return false
        }

        // 1. Update event status to CANCELLED
        eventDao.updateEventStatus(eventId, EventStatus.CANCELLED.name)

        // 2. Cancel WorkManager reminders
        ReminderManager.cancelReminders(context, eventId)

        // 3. Notify all enrolled attendees
        val attendees = rsvpDao.getAttendeesForEvent(eventId).first()
        val timestamp = System.currentTimeMillis()

        attendees.forEach { user ->
            notificationDao.insertNotification(
                Notification(
                    user_id = user.id,
                    title = "Event Cancelled",
                    message = "The event '${event.title}' scheduled for ${event.date} has been cancelled by the organizer.",
                    timestamp = timestamp,
                    type = "event_update",
                    event_id = eventId
                )
            )
        }

        return true
    }
}
