package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "polls")
data class Poll(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event_id: Int,
    val question: String,
    val ends_at: String
)
