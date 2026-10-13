package com.example.campusconnect.repository

import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.models.Notification
import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val notificationDao: NotificationDao) {

    fun getNotifications(userId: Int): Flow<List<Notification>> = notificationDao.getNotificationsForUser(userId)

    suspend fun insertNotification(notification: Notification) = notificationDao.insertNotification(notification)

    suspend fun markAsRead(notification: Notification) {
        val updated = notification.copy(is_read = true)
        notificationDao.insertNotification(updated)
    }

    suspend fun clearAll(userId: Int) = notificationDao.clearNotifications(userId)
}
