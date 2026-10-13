package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "poll_votes",
    indices = [Index(value = ["poll_id", "user_id"], unique = true)]
)
data class PollVote(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val poll_id: Int,
    val option_id: Int,
    val user_id: Int
)
