package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "poll_options")
data class PollOption(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val poll_id: Int,
    val option_text: String,
    val vote_count: Int = 0
)
