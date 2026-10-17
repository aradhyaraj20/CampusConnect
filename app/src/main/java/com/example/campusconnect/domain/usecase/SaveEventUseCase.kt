package com.example.campusconnect.domain.usecase

import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.Notification
import com.example.campusconnect.utils.DateFormats
import java.util.Calendar
import java.util.Date

/** What the organizer typed, before validation. */
data class EventForm(
    val title: String,
    val description: String,
    val location: String,
    val maxAttendeesText: String,
    val type: String,
    val isFlash: Boolean,
    val dateTimestamp: Long,
    val imageUri: String?,
    // Extra details the AI assistant can fill in; ignored when editing an existing event.
    val category: String? = null,
    val tags: String? = null,
    val eligibility: String? = null
)

enum class SaveEventError {
    REQUIRED_FIELDS, INVALID_CAPACITY, PAST_DATE, CAPACITY_BELOW_ATTENDEES, NOT_ALLOWED, NOT_FOUND
}

sealed interface SaveEventResult {
    data class Saved(val event: Event, val isNew: Boolean) : SaveEventResult
    data class Invalid(val error: SaveEventError) : SaveEventResult
}

/**
 * Validates and saves an event the organizer created or edited. Authorization is re-checked
 * against the database (role and ownership), not taken from the screen.
 */
class SaveEventUseCase(
    private val eventDao: EventDao,
    private val userDao: UserDao,
    private val rsvpDao: RsvpDao,
    private val notificationDao: NotificationDao,
    private val transactions: TransactionRunner = TransactionRunner.None,
    private val now: () -> Long = System::currentTimeMillis
) {

    suspend fun save(actorUserId: Int, editingEventId: Int?, form: EventForm): SaveEventResult {
        val actor = userDao.getUserById(actorUserId)
        if (actor == null || !AccessPolicy.canCreateEvents(actor.role)) return invalid(SaveEventError.NOT_ALLOWED)

        val existing = editingEventId?.let { eventDao.getEventById(it) ?: return invalid(SaveEventError.NOT_FOUND) }
        if (existing != null && existing.creator_id != actorUserId && !AccessPolicy.canAccessAdmin(actor.role)) {
            return invalid(SaveEventError.NOT_ALLOWED)
        }

        val title = form.title.trim()
        val description = form.description.trim()
        val location = form.location.trim()
        if (title.isEmpty() || description.isEmpty() || location.isEmpty()) return invalid(SaveEventError.REQUIRED_FIELDS)

        // Blank capacity means unlimited; anything else must be a positive whole number.
        val capacityText = form.maxAttendeesText.trim()
        val maxAttendees = if (capacityText.isEmpty()) null else capacityText.toIntOrNull()?.takeIf { it > 0 }
            ?: return invalid(SaveEventError.INVALID_CAPACITY)

        // An event may be today, but not on an earlier day. Edits that keep the old date are allowed.
        val dateChanged = existing == null || existing.date_timestamp != form.dateTimestamp
        if (dateChanged && form.dateTimestamp < startOfDay(now())) return invalid(SaveEventError.PAST_DATE)

        if (existing != null && maxAttendees != null && maxAttendees < rsvpDao.getAttendeeCount(existing.id)) {
            return invalid(SaveEventError.CAPACITY_BELOW_ATTENDEES)
        }

        val type = if (form.type == "event") "event" else "study_group"
        val date = Date(form.dateTimestamp)
        val event = Event(
            id = existing?.id ?: 0,
            title = title,
            description = description,
            type = type,
            category = existing?.category ?: form.category?.trim()?.takeIf { it.isNotEmpty() }
                ?: (if (type == "event") "Social" else "Academic"),
            image_uri = form.imageUri ?: existing?.image_uri,
            date = DateFormats.eventDay(date),
            time = DateFormats.eventTime(date),
            location = location,
            map_lat = existing?.map_lat,
            map_lng = existing?.map_lng,
            max_attendees = maxAttendees,
            is_user_created = true,
            creator_id = existing?.creator_id ?: actorUserId,
            is_flash = form.isFlash,
            expiry_time = if (form.isFlash) form.dateTimestamp + FLASH_DURATION_MS else null,
            date_timestamp = form.dateTimestamp,
            visibility = existing?.visibility ?: "public",
            eligibility = existing?.eligibility ?: form.eligibility,
            tags = existing?.tags ?: form.tags,
            organizer_name = existing?.organizer_name,
            registration_deadline = existing?.registration_deadline,
            status = existing?.status ?: "active"
        )

        // insertEvent uses OnConflictStrategy.REPLACE, so passing the existing id updates in place.
        // The write and the attendee notifications succeed or fail together.
        return transactions.run {
            val rowId = eventDao.insertEvent(event)
            val saved = if (existing == null) event.copy(id = rowId.toInt()) else event
            if (existing != null && scheduleOrPlaceChanged(existing, saved)) notifyAttendees(saved, actorUserId)
            SaveEventResult.Saved(saved, isNew = existing == null)
        }
    }

    private fun scheduleOrPlaceChanged(old: Event, new: Event) =
        old.date_timestamp != new.date_timestamp || old.location != new.location

    /** Tells everyone going or waitlisted (except the editor) that the time or place changed. */
    private suspend fun notifyAttendees(event: Event, actorUserId: Int) {
        if (!event.status.equals("active", ignoreCase = true)) return
        val timestamp = now()
        rsvpDao.getActiveRsvpsForEvent(event.id)
            .filter { it.user_id != actorUserId }
            .forEach { rsvp ->
                notificationDao.insertNotification(
                    Notification(
                        user_id = rsvp.user_id,
                        title = "Event updated",
                        message = "'${event.title}' is now on ${event.date} at ${event.time}, ${event.location}.",
                        timestamp = timestamp,
                        type = "event_update",
                        event_id = event.id,
                        organizer_id = actorUserId
                    )
                )
            }
    }

    private fun invalid(error: SaveEventError) = SaveEventResult.Invalid(error)

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val FLASH_DURATION_MS = 3_600_000L
    }
}
