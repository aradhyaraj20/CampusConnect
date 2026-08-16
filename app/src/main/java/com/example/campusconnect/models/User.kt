package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val password_hash: String,
    val created_at: String,
    val karma_points: Int = 0,
    val avatar_uri: String? = null
)
