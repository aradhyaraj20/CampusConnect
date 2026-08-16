package com.example.campusconnect.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "marketplace_items")
data class MarketplaceItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String,
    val price: Double,
    val image_uri: String?,
    val poster_id: Int,
    val posted_at: String,
    val is_swap: Boolean = false,
    val exchange_with: String? = null
)
