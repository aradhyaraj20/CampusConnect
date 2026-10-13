package com.example.campusconnect.domain.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RsvpPolicyTest {

    @Test
    fun joinWhenSeatsRemain_isGoing() {
        assertEquals(RsvpDecision.GOING, RsvpPolicy.decideJoin(10, 50))
        assertEquals("going", RsvpPolicy.statusLabel(RsvpDecision.GOING))
    }

    @Test
    fun joinWhenAtCapacity_isWaitlist() {
        assertEquals(RsvpDecision.WAITLIST, RsvpPolicy.decideJoin(50, 50))
        assertTrue(RsvpPolicy.isFull(50, 50))
    }

    @Test
    fun unlimitedCapacity_neverWaitlists() {
        assertEquals(RsvpDecision.GOING, RsvpPolicy.decideJoin(999, null))
        assertFalse(RsvpPolicy.isFull(999, null))
    }

    @Test
    fun promoteWaitlistOnlyWhenGoingCancels() {
        assertTrue(RsvpPolicy.shouldPromoteWaitlist("going", 3))
        assertFalse(RsvpPolicy.shouldPromoteWaitlist("waitlisted", 3))
        assertFalse(RsvpPolicy.shouldPromoteWaitlist("going", 0))
    }

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun activeUpcomingEvent_canBeJoined() {
        assertNull(RsvpPolicy.joinBlock("active", now + day, null, false, now))
    }

    @Test
    fun cancelledEvent_isBlocked_regardlessOfCase() {
        assertEquals(JoinBlock.CANCELLED, RsvpPolicy.joinBlock("CANCELLED", now + day, null, false, now))
        assertEquals(JoinBlock.CANCELLED, RsvpPolicy.joinBlock("cancelled", now + day, null, false, now))
    }

    @Test
    fun pastEvent_isBlocked_butLaterTheSameDayIsNot() {
        assertEquals(JoinBlock.ENDED, RsvpPolicy.joinBlock("active", now - 2 * day, null, false, now))
        assertNull(RsvpPolicy.joinBlock("active", now - 60_000, null, false, now))
    }

    @Test
    fun recurringEvent_neverEnds() {
        assertNull(RsvpPolicy.joinBlock("active", now - 30 * day, null, true, now))
    }

    @Test
    fun deadlinePassed_isBlocked() {
        assertEquals(JoinBlock.DEADLINE_PASSED, RsvpPolicy.joinBlock("active", now + day, now - 1, false, now))
        assertNull(RsvpPolicy.joinBlock("active", now + day, now + 1, false, now))
    }
}
