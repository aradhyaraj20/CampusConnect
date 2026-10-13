package com.example.campusconnect.utils

import android.content.Context
import androidx.work.*
import com.example.campusconnect.models.Event
import com.example.campusconnect.worker.EventReminderWorker
import java.util.concurrent.TimeUnit

object ReminderManager {

    fun scheduleReminders(context: Context, event: Event) {
        val workManager = WorkManager.getInstance(context)
        
        // 1. 24h Reminder
        val delay24h = event.date_timestamp - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        if (delay24h > 0) {
            val data = workDataOf(
                "TITLE" to "Event Tomorrow",
                "MESSAGE" to "Reminder: ${event.title} is happening in 24 hours.",
                "EVENT_ID" to event.id
            )
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay24h, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_24h_${event.id}")
                .build()
            workManager.enqueueUniqueWork("reminder_24h_${event.id}", ExistingWorkPolicy.REPLACE, request)
        }

        // 2. 1h Reminder
        val delay1h = event.date_timestamp - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)
        if (delay1h > 0) {
            val data = workDataOf(
                "TITLE" to "Event Starting Soon",
                "MESSAGE" to "${event.title} starts in 1 hour.",
                "EVENT_ID" to event.id
            )
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delay1h, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("reminder_1h_${event.id}")
                .build()
            workManager.enqueueUniqueWork("reminder_1h_${event.id}", ExistingWorkPolicy.REPLACE, request)
        }
    }
    
    fun cancelReminders(context: Context, eventId: Int) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWorkByTag("reminder_24h_$eventId")
        workManager.cancelAllWorkByTag("reminder_1h_$eventId")
    }
}
