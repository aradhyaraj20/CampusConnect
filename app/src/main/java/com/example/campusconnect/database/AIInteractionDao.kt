package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.AIInteraction
import kotlinx.coroutines.flow.Flow

@Dao
interface AIInteractionDao {
    @Query("SELECT * FROM ai_interactions WHERE user_id = :userId ORDER BY timestamp ASC")
    fun getHistory(userId: Int): Flow<List<AIInteraction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(interaction: AIInteraction)

    @Query("DELETE FROM ai_interactions WHERE user_id = :userId")
    suspend fun clearHistory(userId: Int): Int
}
