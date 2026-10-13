package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["eventId", "userId"], unique = true)]
)
data class Attendance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Int,
    val userId: Int,
    val ticketId: String,
    val checkInTime: Long,
    val checkedInBy: Int,
    val status: String = "CHECKED_IN"
)
