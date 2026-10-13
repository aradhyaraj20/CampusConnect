package com.example.campusconnect.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ai_interactions",
    indices = [Index(value = ["user_id", "mode", "timestamp"])]
)
data class AIInteraction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val prompt: String,
    val response: String,
    val timestamp: Long,
    val is_user_message: Boolean,
    // Which assistant this message belongs to (STUDENT / CREATE / ADMIN) so threads never mix.
    @ColumnInfo(defaultValue = "STUDENT") val mode: String = "STUDENT"
)
