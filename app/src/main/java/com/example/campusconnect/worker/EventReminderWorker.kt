package com.example.campusconnect.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.campusconnect.activities.MainActivity

class EventReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): androidx.work.ListenableWorker.Result {
        val title = inputData.getString("TITLE") ?: "Event Reminder"
        val message = inputData.getString("MESSAGE") ?: "An event is starting soon!"
        val eventId = inputData.getInt("EVENT_ID", -1)

        showNotification(title, message, eventId)
        return androidx.work.ListenableWorker.Result.success()
    }

    private fun showNotification(title: String, message: String, eventId: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "event_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Event Reminders", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EVENT_ID", eventId)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 
            eventId, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(eventId, notification)
    }
}
