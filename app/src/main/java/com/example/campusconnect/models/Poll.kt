package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "polls",
    indices = [Index(value = ["event_id"])]
)
data class Poll(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event_id: Int,
    val question: String,
    val ends_at: String
)
