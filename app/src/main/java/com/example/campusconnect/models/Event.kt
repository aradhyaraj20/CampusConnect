package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [Index(value = ["creator_id"]), Index(value = ["date_timestamp"])]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val type: String, // "event" or "study_group"
    val category: String,
    val image_uri: String?,
    val date: String,
    val time: String,
    val location: String,
    val map_lat: Double?,
    val map_lng: Double?,
    val max_attendees: Int?,
    val is_user_created: Boolean,
    val creator_id: Int?,
    val is_flash: Boolean = false,
    val expiry_time: Long? = null, // Timestamp in ms
    val date_timestamp: Long = 0, // Timestamp in ms for calendar marking
    val visibility: String = "public", // "public" or "private"
    val eligibility: String? = null,
    val tags: String? = null, // Comma-separated tags
    val organizer_name: String? = null,
    val registration_deadline: Long? = null,
    val status: String = "active" // "active", "cancelled", "completed"
)
