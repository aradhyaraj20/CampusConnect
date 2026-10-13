package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Ad
import kotlinx.coroutines.flow.Flow

@Dao
interface AdDao {
    @Query("SELECT * FROM ads ORDER BY created_at DESC")
    fun getAllAds(): Flow<List<Ad>>

    // Shown to students on Home - most recently created active ad first.
    @Query("SELECT * FROM ads WHERE is_active = 1 ORDER BY created_at DESC LIMIT 1")
    fun getActiveAd(): Flow<Ad?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAd(ad: Ad): Long

    @Query("UPDATE ads SET is_active = :active WHERE id = :adId")
    suspend fun setActive(adId: Int, active: Boolean)

    @Delete
    suspend fun deleteAd(ad: Ad)
}
