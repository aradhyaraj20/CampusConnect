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

    /** Events the user holds a seat or waitlist spot for, soonest first. Re-emits on RSVP changes too. */
    @Query(
        "SELECT events.* FROM events INNER JOIN rsvps ON rsvps.event_id = events.id " +
            "WHERE rsvps.user_id = :userId AND rsvps.status IN ('going', 'waitlisted') ORDER BY events.date_timestamp ASC"
    )
    fun getJoinedEvents(userId: Int): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Int): Event?

    @Query("UPDATE events SET status = :status WHERE id = :eventId")
    suspend fun updateEventStatus(eventId: Int, status: String): Int

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    // Organizer dashboard: events this organizer created, newest first
    @Query("SELECT * FROM events WHERE creator_id = :creatorId ORDER BY date_timestamp DESC")
    fun getEventsByCreator(creatorId: Int): Flow<List<Event>>

    @Query("SELECT COUNT(*) FROM events WHERE creator_id = :creatorId")
    suspend fun getEventCountByCreator(creatorId: Int): Int

    // Admin dashboard: category breakdown for "Popular Categories"
    @Query("SELECT category, COUNT(*) as count FROM events GROUP BY category ORDER BY count DESC")
    suspend fun getCategoryCounts(): List<CategoryCount>

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

data class CategoryCount(
    val category: String,
    val count: Int
)
