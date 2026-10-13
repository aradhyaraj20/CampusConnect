package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.BadgeDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.KarmaTransaction
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.User
import com.example.campusconnect.models.UserBadge
import com.example.campusconnect.testutil.fakeDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KarmaUseCasesTest {

    private class Fixture(karmaAfterAward: Int) {
        val badges = mutableListOf<UserBadge>()
        val transactions = mutableListOf<KarmaTransaction>()
        val added = mutableListOf<Pair<Int, Int>>()
        val notifications = mutableListOf<Notification>()
        private val user = User(
            id = 5, name = "A", email = "a@x.com", password_hash = "h", created_at = "t",
            karma_points = karmaAfterAward
        )

        val useCases = KarmaUseCases(
            userDao = fakeDao<UserDao>(
                mapOf(
                    "insertKarmaTransaction" to { a -> transactions += a[0] as KarmaTransaction },
                    "addKarma" to { a -> added += (a[0] as Int) to (a[1] as Int); 1 },
                    "getUserById" to { user }
                )
            ),
            badgeDao = fakeDao<BadgeDao>(mapOf("awardBadge" to { a -> badges += a[0] as UserBadge })),
            notificationDao = fakeDao<NotificationDao>(
                mapOf("insertNotification" to { a -> notifications += a[0] as Notification })
            )
        )
    }

    @Test
    fun awardKarma_recordsTransactionAddsPointsAndNotifies() = runBlocking {
        val f = Fixture(karmaAfterAward = 10)
        f.useCases.awardKarma(userId = 5, points = KarmaUseCases.KARMA_ATTEND_EVENT, reason = "attending", eventId = 3)

        assertEquals(0, f.transactions.single().from_user_id)
        assertEquals(10, f.transactions.single().points)
        assertEquals(5 to 10, f.added.single())
        assertTrue(f.notifications.single().message.contains("attending"))
    }

    @Test
    fun belowFifty_noBadge() = runBlocking {
        val f = Fixture(karmaAfterAward = 49)
        f.useCases.awardKarma(5, 10, "x")
        assertTrue(f.badges.isEmpty())
    }

    @Test
    fun fifty_unlocksFirstBadgeOnly() = runBlocking {
        val f = Fixture(karmaAfterAward = 50)
        f.useCases.awardKarma(5, 10, "x")
        assertEquals(listOf(1), f.badges.map { it.badge_id })
    }

    @Test
    fun oneFifty_unlocksBothBadges() = runBlocking {
        val f = Fixture(karmaAfterAward = 150)
        f.useCases.awardKarma(5, 10, "x")
        assertEquals(listOf(1, 2), f.badges.map { it.badge_id })
    }
}
