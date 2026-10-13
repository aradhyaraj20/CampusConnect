package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "skill_listings")
data class SkillListing(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val skillName: String,
    val description: String,
    val level: String,
    val availability: String,
    val expectedExchange: String,
    val rating: Float = 5.0f
)
