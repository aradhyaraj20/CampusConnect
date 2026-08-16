package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "poll_votes")
data class PollVote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val poll_id: Int,
    val option_id: Int,
    val user_id: Int
)
