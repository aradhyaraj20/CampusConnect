package com.example.campusconnect.database

import androidx.room.Dao
import androidx.room.Query

/** Someone holding a seat or waitlist spot on an event that is about to disappear. */
data class EventAudience(val user_id: Int, val event_id: Int, val title: String)

/**
 * Deletes everything that belongs to a user. The schema has no foreign keys, so
 * removing a row from `users` alone would leave orphans behind; call these inside
 * one transaction (see [AppDatabase.eraseUser]).
 */
@Dao
interface UserDataDao {
    // Rows tied to events the user created
    @Query(
        "SELECT rsvps.user_id, rsvps.event_id, events.title FROM rsvps INNER JOIN events ON events.id = rsvps.event_id " +
            "WHERE events.creator_id = :userId AND rsvps.user_id != :userId AND rsvps.status IN ('going', 'waitlisted')"
    )
    suspend fun getAudienceOfOwnedEvents(userId: Int): List<EventAudience>
    @Query("DELETE FROM notifications WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteNotificationsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM reports WHERE targetType = 'EVENT' AND targetId IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteReportsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM karma_transactions WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteKarmaOnOwnedEvents(userId: Int)
    @Query("DELETE FROM poll_votes WHERE poll_id IN (SELECT id FROM polls WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId))")
    suspend fun deleteVotesOnOwnedEvents(userId: Int)
    @Query("DELETE FROM poll_options WHERE poll_id IN (SELECT id FROM polls WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId))")
    suspend fun deleteOptionsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM polls WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deletePollsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM rsvps WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteRsvpsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM messages WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteMessagesOnOwnedEvents(userId: Int)
    @Query("DELETE FROM recap_photos WHERE event_id IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteRecapsOnOwnedEvents(userId: Int)
    @Query("DELETE FROM attendance WHERE eventId IN (SELECT id FROM events WHERE creator_id = :userId)")
    suspend fun deleteAttendanceOnOwnedEvents(userId: Int)
    @Query("DELETE FROM events WHERE creator_id = :userId")
    suspend fun deleteOwnedEvents(userId: Int)

    // Rows the user authored or owns
    @Query("DELETE FROM rsvps WHERE user_id = :userId")
    suspend fun deleteRsvps(userId: Int)
    @Query("DELETE FROM messages WHERE user_id = :userId")
    suspend fun deleteMessages(userId: Int)
    /** Takes the user's votes back out of the option totals; run before [deletePollVotes]. */
    @Query(
        "UPDATE poll_options SET vote_count = MAX(0, vote_count - " +
            "(SELECT COUNT(*) FROM poll_votes v WHERE v.option_id = poll_options.id AND v.user_id = :userId))"
    )
    suspend fun releasePollVotes(userId: Int)
    @Query("DELETE FROM poll_votes WHERE user_id = :userId")
    suspend fun deletePollVotes(userId: Int)
    @Query("DELETE FROM recap_photos WHERE user_id = :userId")
    suspend fun deleteRecapPhotos(userId: Int)
    @Query("DELETE FROM marketplace_items WHERE poster_id = :userId")
    suspend fun deleteMarketplaceItems(userId: Int)
    @Query("DELETE FROM ads WHERE created_by = :userId")
    suspend fun deleteAds(userId: Int)
    @Query("DELETE FROM karma_transactions WHERE from_user_id = :userId OR to_user_id = :userId")
    suspend fun deleteKarmaTransactions(userId: Int)
    @Query("DELETE FROM notifications WHERE user_id = :userId")
    suspend fun deleteNotifications(userId: Int)
    @Query("DELETE FROM user_interests WHERE user_id = :userId")
    suspend fun deleteInterests(userId: Int)
    @Query("DELETE FROM search_history WHERE user_id = :userId")
    suspend fun deleteSearchHistory(userId: Int)
    @Query("DELETE FROM ai_interactions WHERE user_id = :userId")
    suspend fun deleteAiInteractions(userId: Int)
    @Query("DELETE FROM attendance WHERE userId = :userId")
    suspend fun deleteAttendance(userId: Int)
    @Query("DELETE FROM reports WHERE reportedBy = :userId OR (targetType = 'USER' AND targetId = :userId)")
    suspend fun deleteReports(userId: Int)

    // ---- single event cascade (AppDatabase.deleteEventCascade) ----
    @Query("DELETE FROM poll_votes WHERE poll_id IN (SELECT id FROM polls WHERE event_id = :eventId)")
    suspend fun deleteVotesForEvent(eventId: Int)
    @Query("DELETE FROM poll_options WHERE poll_id IN (SELECT id FROM polls WHERE event_id = :eventId)")
    suspend fun deleteOptionsForEvent(eventId: Int)
    @Query("DELETE FROM polls WHERE event_id = :eventId")
    suspend fun deletePollsForEvent(eventId: Int)
    @Query("DELETE FROM rsvps WHERE event_id = :eventId")
    suspend fun deleteRsvpsForEvent(eventId: Int)
    @Query("DELETE FROM messages WHERE event_id = :eventId")
    suspend fun deleteMessagesForEvent(eventId: Int)
    @Query("DELETE FROM recap_photos WHERE event_id = :eventId")
    suspend fun deleteRecapsForEvent(eventId: Int)
    @Query("DELETE FROM attendance WHERE eventId = :eventId")
    suspend fun deleteAttendanceForEvent(eventId: Int)
    @Query("DELETE FROM notifications WHERE event_id = :eventId")
    suspend fun deleteNotificationsForEvent(eventId: Int)
    @Query("DELETE FROM reports WHERE targetType = 'EVENT' AND targetId = :eventId")
    suspend fun deleteReportsForEvent(eventId: Int)
    @Query("DELETE FROM karma_transactions WHERE event_id = :eventId")
    suspend fun deleteKarmaForEvent(eventId: Int)
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Int)
}
