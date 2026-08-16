package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.StudyGroupMember
import com.example.campusconnect.models.RecapPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Int): Event?

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Delete
    suspend fun deleteEvent(event: Event)

    // Study Groups
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addStudyGroupMember(member: StudyGroupMember)

    @Query("SELECT * FROM study_group_members WHERE group_id = :groupId")
    fun getStudyGroupMembers(groupId: Int): Flow<List<StudyGroupMember>>

    // Recap Photos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRecapPhoto(photo: RecapPhoto)

    @Query("SELECT * FROM recap_photos WHERE event_id = :eventId")
    fun getRecapPhotos(eventId: Int): Flow<List<RecapPhoto>>
}
