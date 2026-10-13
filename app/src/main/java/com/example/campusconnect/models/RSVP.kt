package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rsvps",
    indices = [Index(value = ["user_id", "event_id"], unique = true), Index(value = ["event_id", "status"])]
)
data class RSVP(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val event_id: Int,
    val status: String, // e.g., "going", "maybe", "not_going"
    val checked_in: Boolean = false,
    val joined_at: String
)
