package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val user_id: Int,
    val query: String,
    val timestamp: Long
)
