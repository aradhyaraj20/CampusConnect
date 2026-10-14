package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Message
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE event_id = :eventId ORDER BY id ASC")
    fun getMessagesForEvent(eventId: Int): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Delete
    suspend fun deleteMessage(message: Message)
}
