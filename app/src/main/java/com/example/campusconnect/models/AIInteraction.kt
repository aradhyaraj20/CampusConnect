package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_interactions")
data class AIInteraction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val prompt: String,
    val response: String,
    val timestamp: Long,
    val is_user_message: Boolean
)
