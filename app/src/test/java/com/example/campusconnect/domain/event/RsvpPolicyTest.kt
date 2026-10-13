package com.example.campusconnect.domain.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
