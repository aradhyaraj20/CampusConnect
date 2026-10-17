package com.example.campusconnect.domain.usecase

import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.PollDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.Poll
import com.example.campusconnect.models.PollOption
import com.example.campusconnect.models.PollVote

enum class PollError { QUESTION_REQUIRED, TOO_FEW_OPTIONS, TOO_MANY_OPTIONS, DUPLICATE_OPTIONS, TOO_LONG, NOT_ALLOWED, NOT_FOUND }

enum class VoteResult { RECORDED, ALREADY_VOTED, CLOSED, NOT_FOUND }

/**
 * Event polls. Only the event's organizer (or an admin, re-read from the database) can open one;
 * attendees vote once per poll, and voting stops at the poll's deadline.
 */
class PollUseCases(
    private val pollDao: PollDao,
    private val eventDao: EventDao,
    private val userDao: UserDao,
    private val rsvpDao: RsvpDao,
    private val notificationDao: NotificationDao,
    private val transactions: TransactionRunner = TransactionRunner.None
) {

    /** Returns null when the poll was created, otherwise why not. */
    suspend fun create(
        actorId: Int,
        eventId: Int,
        question: String,
        options: List<String>,
        closesAt: Long,
        endsLabel: String
    ): PollError? {
        validate(question, options)?.let { return it }
        val event = eventDao.getEventById(eventId) ?: return PollError.NOT_FOUND
        val actor = userDao.getUserById(actorId)
        val allowed = actor != null && actor.account_status.equals("ACTIVE", ignoreCase = true) &&
            ((event.creator_id == actorId && AccessPolicy.canCreateEvents(actor.role)) || AccessPolicy.canAccessAdmin(actor.role))
        if (!allowed) return PollError.NOT_ALLOWED

        val cleanQuestion = question.trim()
        transactions.run {
            val pollId = pollDao.insertPoll(
                Poll(event_id = eventId, question = cleanQuestion, ends_at = endsLabel, closes_at = closesAt)
            ).toInt()
            pollDao.insertPollOptions(clean(options).map { PollOption(poll_id = pollId, option_text = it) })
            val now = System.currentTimeMillis()
            rsvpDao.getActiveRsvpsForEvent(eventId)
                .filter { it.status == "going" && it.user_id != actorId }
                .forEach {
                    notificationDao.insertNotification(
                        Notification(
                            user_id = it.user_id,
                            title = "New poll: ${event.title}",
                            message = cleanQuestion,
                            timestamp = now,
                            type = "event_update",
                            event_id = eventId
                        )
                    )
                }
        }
        return null
    }

    /** One vote per user per poll (unique index); the vote and the option total change together. */
    suspend fun vote(pollId: Int, optionId: Int, userId: Int, now: Long = System.currentTimeMillis()): VoteResult =
        transactions.run {
            val poll = pollDao.getPollById(pollId) ?: return@run VoteResult.NOT_FOUND
            if (pollDao.optionBelongsToPoll(optionId, pollId) == 0) return@run VoteResult.NOT_FOUND
            if (poll.isClosed(now)) return@run VoteResult.CLOSED
            val id = pollDao.vote(PollVote(poll_id = pollId, option_id = optionId, user_id = userId))
            if (id == -1L) return@run VoteResult.ALREADY_VOTED
            pollDao.incrementVoteCount(optionId)
            VoteResult.RECORDED
        }

    companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 6
        const val MAX_QUESTION = 200
        const val MAX_OPTION = 80

        fun clean(options: List<String>) = options.map { it.trim() }.filter { it.isNotEmpty() }

        fun validate(question: String, options: List<String>): PollError? {
            val q = question.trim()
            if (q.isEmpty()) return PollError.QUESTION_REQUIRED
            val opts = clean(options)
            if (opts.size < MIN_OPTIONS) return PollError.TOO_FEW_OPTIONS
            if (opts.size > MAX_OPTIONS) return PollError.TOO_MANY_OPTIONS
            if (opts.map { it.lowercase() }.toSet().size != opts.size) return PollError.DUPLICATE_OPTIONS
            if (q.length > MAX_QUESTION || opts.any { it.length > MAX_OPTION }) return PollError.TOO_LONG
            return null
        }
    }
}
