package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.SkillListing
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {
    @Query("SELECT * FROM skill_listings ORDER BY id DESC")
    fun getAllSkills(): Flow<List<SkillListing>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkill(skill: SkillListing)

    @Delete
    suspend fun deleteSkill(skill: SkillListing)
}
