package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reports")
data class Report(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reportedBy: Int,
    val targetType: String, // "USER", "EVENT", "MESSAGE", "MARKETPLACE"
    val targetId: Int,
    val reason: String,
    val status: String = "PENDING", // "PENDING", "UNDER_REVIEW", "RESOLVED", "DISMISSED"
    val timestamp: Long
)
