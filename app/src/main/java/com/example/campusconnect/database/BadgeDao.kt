package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Badge
import com.example.campusconnect.models.UserBadge
import kotlinx.coroutines.flow.Flow

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges")
    fun getAllBadges(): Flow<List<Badge>>

    @Query("SELECT COUNT(*) FROM badges")
    suspend fun getBadgeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadge(badge: Badge)

    @Query("SELECT badges.* FROM badges INNER JOIN user_badges ON badges.id = user_badges.badge_id WHERE user_badges.user_id = :userId")
    fun getUserBadges(userId: Int): Flow<List<Badge>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun awardBadge(userBadge: UserBadge)
}
