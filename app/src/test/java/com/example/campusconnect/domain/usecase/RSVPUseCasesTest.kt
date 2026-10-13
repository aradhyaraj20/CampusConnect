package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.RSVP
import com.example.campusconnect.testutil.fakeDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RSVPUseCasesTest {

    private class Fixture(max: Int?, going: Int, waitlist: RSVP? = null, existing: RSVP? = null) {
        val inserted = mutableListOf<RSVP>()
        val updated = mutableListOf<RSVP>()
        val deleted = mutableListOf<RSVP>()
        val notifications = mutableListOf<Notification>()
        private val event = Event(
            id = 1, title = "Hackathon", description = "", type = "event", category = "Social",
            image_uri = null, date = "d", time = "t", location = "l", map_lat = null, map_lng = null,
            max_attendees = max, is_user_created = false, creator_id = 2
        )

        val useCases = RSVPUseCases(
            rsvpDao = fakeDao<RsvpDao>(
                mapOf(
                    "getAttendeeCount" to { going },
                    "getUserRSVPForEvent" to { existing },
                    "getEarliestWaitlistedUser" to { waitlist },
                    "insertRSVP" to { a -> inserted += a[0] as RSVP },
                    "updateRsvp" to { a -> updated += a[0] as RSVP },
                    "deleteRsvp" to { a -> deleted += a[0] as RSVP }
                )
            ),
            eventDao = fakeDao<EventDao>(mapOf("getEventById" to { event })),
            notificationDao = fakeDao<NotificationDao>(
                mapOf("insertNotification" to { a -> notifications += a[0] as Notification })
            )
        )
    }

    private fun rsvp(user: Int, status: String) =
        RSVP(id = user, user_id = user, event_id = 1, status = status, joined_at = "2026-01-01 10:00:00")

    @Test
    fun seatsLeft_userIsGoing() = runBlocking {
        val f = Fixture(max = 50, going = 10)
        assertEquals("going", f.useCases.rsvpOrWaitlist(userId = 7, eventId = 1))
        assertEquals("going", f.inserted.single().status)
    }

    @Test
    fun eventFull_userIsWaitlisted() = runBlocking {
        val f = Fixture(max = 50, going = 50)
        assertEquals("waitlisted", f.useCases.rsvpOrWaitlist(7, 1))
        assertEquals("waitlisted", f.inserted.single().status)
    }

    @Test
    fun noCapacitySet_neverWaitlists() = runBlocking {
        val f = Fixture(max = null, going = 9999)
        assertEquals("going", f.useCases.rsvpOrWaitlist(7, 1))
    }

    @Test
    fun cancelGoing_promotesEarliestWaitlistedAndNotifiesThem() = runBlocking {
        val f = Fixture(max = 1, going = 1, waitlist = rsvp(9, "waitlisted"), existing = rsvp(7, "going"))
        f.useCases.cancelRsvpAndPromoteWaitlist(userId = 7, eventId = 1)

        assertEquals(7, f.deleted.single().user_id)
        assertEquals(9, f.updated.single().user_id)
        assertEquals("going", f.updated.single().status)
        val note = f.notifications.single()
        assertEquals(9, note.user_id)
        assertTrue(note.message.contains("Hackathon"))
    }

    @Test
    fun cancelWaitlisted_doesNotPromoteAnyone() = runBlocking {
        val f = Fixture(max = 1, going = 1, waitlist = rsvp(9, "waitlisted"), existing = rsvp(7, "waitlisted"))
        f.useCases.cancelRsvpAndPromoteWaitlist(7, 1)

        assertEquals(1, f.deleted.size)
        assertTrue(f.updated.isEmpty())
        assertTrue(f.notifications.isEmpty())
    }

    @Test
    fun cancelGoing_withEmptyWaitlist_justDeletes() = runBlocking {
        val f = Fixture(max = 1, going = 1, waitlist = null, existing = rsvp(7, "going"))
        f.useCases.cancelRsvpAndPromoteWaitlist(7, 1)

        assertEquals(1, f.deleted.size)
        assertTrue(f.updated.isEmpty())
    }

    @Test
    fun cancelWithoutRsvp_isNoOp() = runBlocking {
        val f = Fixture(max = 1, going = 0, existing = null)
        f.useCases.cancelRsvpAndPromoteWaitlist(7, 1)

        assertTrue(f.deleted.isEmpty())
        assertTrue(f.updated.isEmpty())
    }
}
