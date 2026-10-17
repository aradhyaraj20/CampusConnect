package com.example.campusconnect.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "polls",
    indices = [Index(value = ["event_id"])]
)
data class Poll(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val event_id: Int,
    val question: String,
    /** Human-readable deadline shown on the card; blank when the poll has no deadline. */
    val ends_at: String,
    /** Epoch millis after which voting is closed; 0 = never closes. */
    @ColumnInfo(defaultValue = "0") val closes_at: Long = 0
) {
    fun isClosed(now: Long = System.currentTimeMillis()) = closes_at in 1 until now
}
