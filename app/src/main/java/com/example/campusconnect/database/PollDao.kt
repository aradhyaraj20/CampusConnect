package com.example.campusconnect.database

import androidx.room.*
import com.example.campusconnect.models.Poll
import com.example.campusconnect.models.PollOption
import com.example.campusconnect.models.PollVote
import kotlinx.coroutines.flow.Flow

@Dao
interface PollDao {
    @Query("SELECT * FROM polls WHERE event_id = :eventId")
    fun getPollsForEvent(eventId: Int): Flow<List<Poll>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoll(poll: Poll): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPollOptions(options: List<PollOption>)

    @Query("SELECT * FROM poll_options WHERE poll_id = :pollId")
    fun getOptionsForPoll(pollId: Int): Flow<List<PollOption>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun vote(vote: PollVote)

    @Query("UPDATE poll_options SET vote_count = vote_count + 1 WHERE id = :optionId")
    suspend fun incrementVoteCount(optionId: Int): Int
}
