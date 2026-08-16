package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_group_members")
data class StudyGroupMember(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val group_id: Int,
    val user_id: Int,
    val joined_at: String
)
