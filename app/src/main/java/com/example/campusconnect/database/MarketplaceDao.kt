package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.MarketplaceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MarketplaceDao {
    @Query("SELECT * FROM marketplace_items")
    fun getAllItems(): Flow<List<MarketplaceItem>>

    @Query("SELECT * FROM marketplace_items WHERE category = :category")
    fun getItemsByCategory(category: String): Flow<List<MarketplaceItem>>

    @Query("SELECT COUNT(*) FROM marketplace_items")
    suspend fun getItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MarketplaceItem)

    @Delete
    suspend fun deleteItem(item: MarketplaceItem)
}
