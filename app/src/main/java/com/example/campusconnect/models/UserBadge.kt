package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_badges")
data class UserBadge(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val badge_id: Int,
    val earned_at: String
)
