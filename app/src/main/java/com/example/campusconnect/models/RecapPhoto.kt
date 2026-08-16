package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recap_photos")
data class RecapPhoto(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event_id: Int,
    val user_id: Int,
    val image_uri: String,
    val posted_at: String
)
