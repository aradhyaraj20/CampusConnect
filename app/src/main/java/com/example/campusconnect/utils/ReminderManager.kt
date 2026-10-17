package com.example.campusconnect.utils

import android.content.Context
import androidx.work.*
import com.example.campusconnect.models.Event
import com.example.campusconnect.worker.EventReminderWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reminders belong to a user, not just the device: each one carries the user id, the worker only
 * fires while that user is signed in and still booked (or organising), and leaving an event
 * cancels that user's reminders. Nothing is scheduled unless the user asks for it.
 */
object ReminderManager {

    private const val USER_TAG_PREFIX = "reminder_user_"

    private fun eventTag(eventId: Int) = "reminder_event_$eventId"

    fun scheduleReminders(context: Context, event: Event, userId: Int) {
        if (userId <= 0) return
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        listOf(
            Triple("24h", TimeUnit.HOURS.toMillis(24), "Event Tomorrow" to "Reminder: ${event.title} is happening in 24 hours."),
            Triple("1h", TimeUnit.HOURS.toMillis(1), "Event Starting Soon" to "${event.title} starts in 1 hour.")
        ).forEach { (key, before, text) ->
            val delay = event.date_timestamp - now - before
            if (delay <= 0) return@forEach
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(
                    workDataOf(
                        EventReminderWorker.KEY_TITLE to text.first,
                        EventReminderWorker.KEY_MESSAGE to text.second,
                        EventReminderWorker.KEY_EVENT_ID to event.id,
                        EventReminderWorker.KEY_USER_ID to userId
                    )
                )
                .addTag(eventTag(event.id))
                .addTag("$USER_TAG_PREFIX$userId")
                .build()
            workManager.enqueueUniqueWork(uniqueName(key, event.id, userId), ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Cancels one user's reminders for an event (for example after leaving it). */
    fun cancelReminders(context: Context, eventId: Int, userId: Int) {
        val workManager = WorkManager.getInstance(context)
        listOf("24h", "1h").forEach { workManager.cancelUniqueWork(uniqueName(it, eventId, userId)) }
    }

    /** Cancels every user's reminders for an event (event deleted). */
    fun cancelAllReminders(context: Context, eventId: Int) {
        WorkManager.getInstance(context).cancelAllWorkByTag(eventTag(eventId))
    }

    /** After the time changes: move the reminders of everyone who had them on this device. */
    suspend fun rescheduleForEvent(context: Context, event: Event) {
        val users = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).getWorkInfosByTag(eventTag(event.id)).get()
                .filter { !it.state.isFinished }
                .flatMap { info -> info.tags.mapNotNull { it.removePrefix(USER_TAG_PREFIX).takeIf { t -> t != it }?.toIntOrNull() } }
                .toSet()
        }
        cancelAllReminders(context, event.id)
        users.forEach { scheduleReminders(context, event, it) }
    }

    private fun uniqueName(key: String, eventId: Int, userId: Int) = "reminder_${key}_${eventId}_$userId"
}
