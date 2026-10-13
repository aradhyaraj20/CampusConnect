package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Admin-controlled promotional banner. Admin can create/toggle/delete these;
 * active ads are shown to students on the Home feed. Nothing here talks to an
 * external ad network - it is simply admin-authored content the admin opts
 * into showing, per the spec ("let admin display ads of his wish if needed").
 */
@Entity(tableName = "ads")
data class Ad(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val image_uri: String? = null,
    val link_url: String? = null,
    val is_active: Boolean = true,
    val created_by: Int,
    val created_at: Long = System.currentTimeMillis()
)
