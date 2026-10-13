package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["role"]),
        Index(value = ["course"]),
        Index(value = ["account_status"])
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val email: String,
    val password_hash: String,
    val created_at: String,
    val karma_points: Int = 0,
    val avatar_uri: String? = null,
    val bio: String? = null,
    val interests: String? = null, // Comma-separated tags
    val role: String = "STUDENT",
    val account_status: String = "ACTIVE",
    // Course details captured at registration so that in-app competition
    // (karma leaderboard) is scoped to classmates/batchmates on the same course.
    val course: String? = null,
    val course_duration_months: Int? = null,
    // Timestamp (epoch millis) the course is expected to end: created_at + duration.
    val course_end_at: Long? = null,
    // Updated on every successful login. Used by AccountCleanupWorker to purge
    // accounts that go quiet for 2+ months after their course has ended.
    val last_login_at: Long = System.currentTimeMillis()
)
