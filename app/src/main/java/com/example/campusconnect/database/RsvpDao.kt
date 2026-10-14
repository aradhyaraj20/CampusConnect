package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.RSVP
import com.example.campusconnect.models.User
import kotlinx.coroutines.flow.Flow

@Dao
interface RsvpDao {
    @Query("SELECT * FROM rsvps WHERE event_id = :eventId")
    fun getRsvpsForEvent(eventId: Int): Flow<List<RSVP>>

    @Query("SELECT users.* FROM users INNER JOIN rsvps ON users.id = rsvps.user_id WHERE rsvps.event_id = :eventId AND rsvps.status = 'going'")
    fun getAttendeesForEvent(eventId: Int): Flow<List<User>>

    @Query("SELECT * FROM rsvps WHERE user_id = :userId")
    fun getRsvpsForUser(userId: Int): Flow<List<RSVP>>

    @Query("SELECT * FROM rsvps WHERE id = :rsvpId")
    suspend fun getRsvpById(rsvpId: Int): RSVP?

    @Query("SELECT * FROM rsvps WHERE user_id = :userId AND event_id = :eventId")
    suspend fun getUserRSVPForEvent(userId: Int, eventId: Int): RSVP?

    /** Everyone holding a seat or a waitlist spot, i.e. who should hear about changes to the event. */
    @Query("SELECT * FROM rsvps WHERE event_id = :eventId AND status IN ('going', 'waitlisted')")
    suspend fun getActiveRsvpsForEvent(eventId: Int): List<RSVP>

    @Query("SELECT COUNT(*) FROM rsvps WHERE event_id = :eventId AND status = 'going'")
    suspend fun getAttendeeCount(eventId: Int): Int

    // Student NLP search: boost events similar to what this user has actually attended before
    @Query("SELECT event_id FROM rsvps WHERE user_id = :userId AND status = 'going'")
    suspend fun getGoingEventIdsForUser(userId: Int): List<Int>

    // Admin dashboard: platform-wide RSVP total
    @Query("SELECT COUNT(*) FROM rsvps")
    suspend fun getTotalRsvpCount(): Int

    // Organizer dashboard: registrations across all events this organizer created
    @Query("SELECT COUNT(*) FROM rsvps WHERE event_id IN (SELECT id FROM events WHERE creator_id = :creatorId) AND status = 'going'")
    suspend fun getRegistrationCountForOrganizer(creatorId: Int): Int

    @Query("SELECT * FROM rsvps WHERE event_id = :eventId AND status = 'waitlisted' ORDER BY id ASC LIMIT 1")
    suspend fun getEarliestWaitlistedUser(eventId: Int): RSVP?

    /** Returns -1 when the user already has an RSVP for this event. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRSVP(rsvp: RSVP): Long

    @Update
    suspend fun updateRsvp(rsvp: RSVP)

    @Delete
    suspend fun deleteRsvp(rsvp: RSVP)
}
