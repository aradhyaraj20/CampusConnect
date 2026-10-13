package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Notification
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE user_id = :userId ORDER BY timestamp DESC")
    fun getNotificationsForUser(userId: Int): Flow<List<Notification>>

    @Query("SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = 0")
    suspend fun getNotificationCount(userId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: Notification)

    @Delete
    suspend fun deleteNotification(notification: Notification)

    @Query("UPDATE notifications SET is_read = 1 WHERE user_id = :userId AND is_read = 0")
    suspend fun markAllRead(userId: Int): Int

    @Query("DELETE FROM notifications WHERE user_id = :userId")
    suspend fun clearNotifications(userId: Int): Int
}
