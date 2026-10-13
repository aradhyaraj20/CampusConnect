package com.example.campusconnect.domain.event

enum class RsvpDecision {
    GOING,
    WAITLIST
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
}
