package com.example.campusconnect.data.repository

import com.example.campusconnect.database.SkillDao
import com.example.campusconnect.models.SkillListing
import kotlinx.coroutines.flow.Flow

class SkillRepository(private val skillDao: SkillDao) {

    fun getAllSkills(): Flow<List<SkillListing>> = skillDao.getAllSkills()

    suspend fun addSkill(skill: SkillListing) {
        skillDao.insertSkill(skill)
    }

    suspend fun removeSkill(skill: SkillListing) {
        skillDao.deleteSkill(skill)
    }
}
