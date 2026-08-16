package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val user_id: Int,
    val dark_mode: Boolean = false,
    val notifications_enabled: Boolean = true,
    val offline_mode: Boolean = false,
    val default_campus: String? = null
)
