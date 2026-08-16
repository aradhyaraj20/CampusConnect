package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.RSVP
import kotlinx.coroutines.flow.Flow

@Dao
interface RsvpDao {
    @Query("SELECT * FROM rsvps WHERE event_id = :eventId")
    fun getRsvpsForEvent(eventId: Int): Flow<List<RSVP>>

    @Query("SELECT * FROM rsvps WHERE id = :rsvpId")
    suspend fun getRsvpById(rsvpId: Int): RSVP?

    @Query("SELECT * FROM rsvps WHERE user_id = :userId AND event_id = :eventId")
    suspend fun getUserRSVPForEvent(userId: Int, eventId: Int): RSVP?

    @Query("SELECT COUNT(*) FROM rsvps WHERE event_id = :eventId AND status = 'going'")
    suspend fun getAttendeeCount(eventId: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRSVP(rsvp: RSVP)

    @Update
    suspend fun updateRsvp(rsvp: RSVP)

    @Delete
    suspend fun deleteRsvp(rsvp: RSVP)
}
