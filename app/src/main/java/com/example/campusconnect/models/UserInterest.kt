package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_interests")
data class UserInterest(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val interest_tag: String,
    val weight: Float = 1.0f // To rank interests
)
