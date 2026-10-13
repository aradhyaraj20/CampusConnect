package com.example.campusconnect.domain.event

import java.util.Calendar

enum class RsvpDecision {
    GOING,
    WAITLIST
}

/** Why a student can't join an event right now. */
enum class JoinBlock {
    CANCELLED,
    ENDED,
    DEADLINE_PASSED
}

object RsvpPolicy {

    fun decideJoin(currentGoingCount: Int, maxAttendees: Int?): RsvpDecision {
        val cap = maxAttendees ?: return RsvpDecision.GOING
        if (cap <= 0) return RsvpDecision.GOING
        return if (currentGoingCount >= cap) RsvpDecision.WAITLIST else RsvpDecision.GOING
    }

    fun isFull(currentGoingCount: Int, maxAttendees: Int?): Boolean =
        decideJoin(currentGoingCount, maxAttendees) == RsvpDecision.WAITLIST

    /**
     * When a GOING attendee leaves, the earliest waitlisted user is promoted.
     */
    fun shouldPromoteWaitlist(cancelledStatus: String, waitlistedCount: Int): Boolean {
        return cancelledStatus.equals("going", ignoreCase = true) && waitlistedCount > 0
    }

    fun statusLabel(decision: RsvpDecision): String = when (decision) {
        RsvpDecision.GOING -> "going"
        RsvpDecision.WAITLIST -> "waitlisted"
    }

    /**
     * Returns null when joining is allowed. An event stays joinable for the whole day it
     * is on (many events have no end time); recurring events ("Every Tue") never end.
     */
    fun joinBlock(
        status: String,
        eventStartMillis: Long,
        registrationDeadline: Long?,
        isRecurring: Boolean,
        nowMillis: Long
    ): JoinBlock? {
        if (status.equals("cancelled", ignoreCase = true)) return JoinBlock.CANCELLED
        if (status.equals("completed", ignoreCase = true)) return JoinBlock.ENDED
        if (!isRecurring && eventStartMillis > 0 && nowMillis >= endOfDay(eventStartMillis)) return JoinBlock.ENDED
        if (registrationDeadline != null && registrationDeadline > 0 && nowMillis > registrationDeadline) {
            return JoinBlock.DEADLINE_PASSED
        }
        return null
    }

    private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
}
