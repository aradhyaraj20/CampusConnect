package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "karma_transactions")
data class KarmaTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val from_user_id: Int,
    val to_user_id: Int,
    val event_id: Int,
    val points: Int,
    val given_at: String
)
