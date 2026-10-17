package com.example.campusconnect.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.campusconnect.R
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.activities.MainActivity
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase

class EventReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val eventId = inputData.getInt(KEY_EVENT_ID, -1)
        val userId = inputData.getInt(KEY_USER_ID, -1)

        // Respect the Settings switch and the Android 13+ runtime permission.
        val prefs = applicationContext.getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notify_event_reminders", true) || !canPostNotifications()) return Result.success()

        // The event may have been deleted or cancelled after the reminder was scheduled.
        val db = AppDatabase.getDatabase(applicationContext)
        val event = db.eventDao().getEventById(eventId)
        if (event == null || !event.status.equals("active", ignoreCase = true)) return Result.success()

        // Only for the user who asked, while they are the one signed in on this phone,
        // and only while they still have a seat (or a waitlist spot) or organise the event.
        if (userId <= 0 || SessionManager.getInstance(applicationContext).getCurrentUserId() != userId) return Result.success()
        val rsvp = db.rsvpDao().getUserRSVPForEvent(userId, eventId)
        val stillBooked = rsvp != null && rsvp.status in setOf("going", "waitlisted")
        if (!stillBooked && event.creator_id != userId) return Result.success()

        val title = inputData.getString(KEY_TITLE) ?: applicationContext.getString(R.string.reminder_default_title)
        val message = inputData.getString(KEY_MESSAGE) ?: applicationContext.getString(R.string.reminder_default_message)
        showNotification(title, message, eventId)
        return Result.success()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun showNotification(title: String, message: String, eventId: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "event_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                applicationContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Open the event itself, with Home underneath so Back behaves normally.
        val pendingIntent = TaskStackBuilder.create(applicationContext)
            .addNextIntent(Intent(applicationContext, MainActivity::class.java))
            .addNextIntent(Intent(applicationContext, EventDetailActivity::class.java).putExtra("EVENT_ID", eventId))
            .getPendingIntent(eventId, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(eventId, notification)
    }

    companion object {
        const val KEY_EVENT_ID = "EVENT_ID"
        const val KEY_USER_ID = "USER_ID"
        const val KEY_TITLE = "TITLE"
        const val KEY_MESSAGE = "MESSAGE"
    }
}
