package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Poll
import com.example.campusconnect.models.PollOption
import com.example.campusconnect.models.PollVote
import kotlinx.coroutines.flow.Flow

@Dao
interface PollDao {
    @Query("SELECT * FROM polls WHERE event_id = :eventId ORDER BY id DESC")
    fun getPollsForEvent(eventId: Int): Flow<List<Poll>>

    @Query("SELECT * FROM polls WHERE id = :pollId")
    suspend fun getPollById(pollId: Int): Poll?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: Poll): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPollOptions(options: List<PollOption>)

    @Query("SELECT * FROM poll_options WHERE poll_id = :pollId")
    fun getOptionsForPoll(pollId: Int): Flow<List<PollOption>>

    @Query("SELECT * FROM poll_options WHERE poll_id IN (SELECT id FROM polls WHERE event_id = :eventId) ORDER BY id")
    fun getOptionsForEvent(eventId: Int): Flow<List<PollOption>>

    @Query("SELECT * FROM poll_votes WHERE user_id = :userId AND poll_id IN (SELECT id FROM polls WHERE event_id = :eventId)")
    fun getUserVotesForEvent(eventId: Int, userId: Int): Flow<List<PollVote>>

    @Query("SELECT COUNT(*) FROM poll_options WHERE id = :optionId AND poll_id = :pollId")
    suspend fun optionBelongsToPoll(optionId: Int, pollId: Int): Int

    /** Returns -1 when this user already voted in the poll. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun vote(vote: PollVote): Long

    @Query("SELECT option_id FROM poll_votes WHERE poll_id = :pollId AND user_id = :userId LIMIT 1")
    suspend fun getUserVote(pollId: Int, userId: Int): Int?

    @Query("UPDATE poll_options SET vote_count = vote_count + 1 WHERE id = :optionId")
    suspend fun incrementVoteCount(optionId: Int): Int
}
