package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class Notification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val title: String,
    val message: String,
    val timestamp: Long,
    val type: String, // "karma", "flash", "interest", "system", "event_created", "event_update"
    val is_read: Boolean = false,
    val event_id: Int? = null, // For navigation
    val event_type: String? = null,
    val organizer_id: Int? = null
)
