package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "friendships")
data class Friendship(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val friend_id: Int
)
