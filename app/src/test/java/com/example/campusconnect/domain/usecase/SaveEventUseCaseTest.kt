package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.RSVP
import com.example.campusconnect.models.User
import com.example.campusconnect.testutil.fakeDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SaveEventUseCaseTest {

    private val now = Calendar.getInstance().apply { set(2026, Calendar.JUNE, 10, 12, 0, 0) }.timeInMillis
    private val tomorrow = now + 24 * 3_600_000L
    private val lastWeek = now - 7 * 24 * 3_600_000L

    private fun user(id: Int, role: String) =
        User(id = id, name = "N", email = "n$id@x.com", password_hash = "h", created_at = "c", role = role)

    private fun event(creator: Int, ts: Long = tomorrow) = Event(
        id = 9, title = "Old", description = "d", type = "event", category = "Tech", image_uri = null,
        date = "d", time = "t", location = "l", map_lat = null, map_lng = null, max_attendees = 50,
        is_user_created = true, creator_id = creator, date_timestamp = ts
    )

    private class Fixture(actor: User?, existing: Event?, going: Int, now: Long, attendees: List<RSVP> = emptyList()) {
        val notifications = mutableListOf<Notification>()
        val inserted = mutableListOf<Event>()
        val useCase = SaveEventUseCase(
            eventDao = fakeDao<EventDao>(
                mapOf(
                    "getEventById" to { existing },
                    "insertEvent" to { a -> inserted += a[0] as Event; 77L }
                )
            ),
            userDao = fakeDao<UserDao>(mapOf("getUserById" to { actor })),
            rsvpDao = fakeDao<RsvpDao>(mapOf("getAttendeeCount" to { going }, "getActiveRsvpsForEvent" to { attendees })),
            notificationDao = fakeDao<NotificationDao>(mapOf("insertNotification" to { a -> notifications += a[0] as Notification })),
            now = { now }
        )
    }

    private fun form(
        title: String = "Hack Night", capacity: String = "", ts: Long = tomorrow, flash: Boolean = false
    ) = EventForm(title, "desc", "Lab 2", capacity, "event", flash, ts, null)

    private fun fixture(actor: User? = user(1, "ORGANIZER"), existing: Event? = null, going: Int = 0, attendees: List<RSVP> = emptyList()) =
        Fixture(actor, existing, going, now, attendees)

    private fun SaveEventResult.error() = (this as? SaveEventResult.Invalid)?.error

    @Test
    fun student_isNotAllowed() = runBlocking {
        val f = fixture(actor = user(1, "STUDENT"))
        assertEquals(SaveEventError.NOT_ALLOWED, f.useCase.save(1, null, form()).error())
        assertTrue(f.inserted.isEmpty())
    }

    @Test
    fun blankFieldsAfterTrim_areRejected() = runBlocking {
        assertEquals(SaveEventError.REQUIRED_FIELDS, fixture().useCase.save(1, null, form(title = "   ")).error())
    }

    @Test
    fun capacity_blankIsUnlimited_zeroNegativeAndTextAreRejected() = runBlocking {
        val ok = fixture().useCase.save(1, null, form(capacity = " ")) as SaveEventResult.Saved
        assertNull(ok.event.max_attendees)
        for (bad in listOf("0", "-5", "abc", "1.5")) {
            assertEquals(SaveEventError.INVALID_CAPACITY, fixture().useCase.save(1, null, form(capacity = bad)).error())
        }
    }

    @Test
    fun newEvent_inThePast_isRejected_butTodayIsFine() = runBlocking {
        assertEquals(SaveEventError.PAST_DATE, fixture().useCase.save(1, null, form(ts = lastWeek)).error())
        assertTrue(fixture().useCase.save(1, null, form(ts = now - 3_600_000L)) is SaveEventResult.Saved)
    }

    @Test
    fun creating_returnsTheGeneratedId_andFlashGetsAnExpiry() = runBlocking {
        val f = fixture()
        val saved = f.useCase.save(1, null, form(flash = true)) as SaveEventResult.Saved
        assertTrue(saved.isNew)
        assertEquals(77, saved.event.id)
        assertEquals(1, saved.event.creator_id)
        assertEquals(tomorrow + 3_600_000L, saved.event.expiry_time)
    }

    @Test
    fun editing_someoneElsesEvent_isRejected_butAdminMay() = runBlocking {
        val other = event(creator = 2)
        assertEquals(SaveEventError.NOT_ALLOWED, fixture(existing = other).useCase.save(1, 9, form()).error())
        val adminSave = fixture(actor = user(1, "ADMIN"), existing = other).useCase.save(1, 9, form()) as SaveEventResult.Saved
        assertEquals(2, adminSave.event.creator_id)
        assertEquals(9, adminSave.event.id)
    }

    @Test
    fun editing_cannotShrinkCapacityBelowGoingCount() = runBlocking {
        val f = fixture(existing = event(creator = 1), going = 10)
        assertEquals(SaveEventError.CAPACITY_BELOW_ATTENDEES, f.useCase.save(1, 9, form(capacity = "5")).error())
        assertTrue(f.useCase.save(1, 9, form(capacity = "10")) is SaveEventResult.Saved)
    }

    @Test
    fun editing_keepingAnOldDate_isNotTreatedAsPast() = runBlocking {
        val f = fixture(existing = event(creator = 1, ts = lastWeek))
        assertTrue(f.useCase.save(1, 9, form(ts = lastWeek)) is SaveEventResult.Saved)
    }

    @Test
    fun editingMissingEvent_reportsNotFound() = runBlocking {
        assertEquals(SaveEventError.NOT_FOUND, fixture(existing = null).useCase.save(1, 9, form()).error())
    }

    private fun rsvp(user: Int) = RSVP(id = user, user_id = user, event_id = 9, status = "going", joined_at = "t")

    @Test
    fun changingTheTime_notifiesAttendeesButNotTheEditor() = runBlocking {
        val f = fixture(existing = event(creator = 1), attendees = listOf(rsvp(1), rsvp(5), rsvp(6)))
        f.useCase.save(1, 9, form(ts = tomorrow + 3_600_000L))
        assertEquals(listOf(5, 6), f.notifications.map { it.user_id })
        assertTrue(f.notifications.all { it.event_id == 9 && it.type == "event_update" })
    }

    @Test
    fun changingTheLocation_notifies() = runBlocking {
        val f = fixture(existing = event(creator = 1), attendees = listOf(rsvp(5)))
        f.useCase.save(1, 9, form(ts = tomorrow).copy(location = "Main Hall"))
        assertEquals(1, f.notifications.size)
        assertTrue(f.notifications.single().message.contains("Main Hall"))
    }

    @Test
    fun editingOnlyTheDescription_doesNotNotify() = runBlocking {
        val f = fixture(existing = event(creator = 1).copy(location = "Lab 2"), attendees = listOf(rsvp(5)))
        f.useCase.save(1, 9, form(ts = tomorrow).copy(description = "new text"))
        assertTrue(f.notifications.isEmpty())
    }

    @Test
    fun creatingAnEvent_neverNotifies() = runBlocking {
        val f = fixture(attendees = listOf(rsvp(5)))
        f.useCase.save(1, null, form())
        assertTrue(f.notifications.isEmpty())
    }
}
