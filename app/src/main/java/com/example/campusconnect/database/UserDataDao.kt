package com.example.campusconnect.database

import androidx.room.Dao
import androidx.room.Query

/**
 * Deletes everything that belongs to a user. The schema has no foreign keys, so
 * removing a row from `users` alone would leave orphans behind; call these inside
 * one transaction (see [AppDatabase.eraseUser]).
 */
@Dao
interface UserDataDao {
    // Rows tied to events the user created
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
    @Query("DELETE FROM study_group_members WHERE user_id = :userId")
    suspend fun deleteGroupMemberships(userId: Int)
    @Query("DELETE FROM friendships WHERE user_id = :userId OR friend_id = :userId")
    suspend fun deleteFriendships(userId: Int)
    @Query("DELETE FROM messages WHERE user_id = :userId")
    suspend fun deleteMessages(userId: Int)
    @Query("DELETE FROM poll_votes WHERE user_id = :userId")
    suspend fun deletePollVotes(userId: Int)
    @Query("DELETE FROM recap_photos WHERE user_id = :userId")
    suspend fun deleteRecapPhotos(userId: Int)
    @Query("DELETE FROM user_badges WHERE user_id = :userId")
    suspend fun deleteUserBadges(userId: Int)
    @Query("DELETE FROM marketplace_items WHERE poster_id = :userId")
    suspend fun deleteMarketplaceItems(userId: Int)
    @Query("DELETE FROM settings WHERE user_id = :userId")
    suspend fun deleteSettings(userId: Int)
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
    @Query("DELETE FROM skill_listings WHERE userId = :userId")
    suspend fun deleteSkillListings(userId: Int)
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
    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteEventById(eventId: Int)
}
