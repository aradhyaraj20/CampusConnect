package com.example.campusconnect.domain.usecase

import com.example.campusconnect.utils.DateFormats
import com.example.campusconnect.database.BadgeDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.KarmaTransaction
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.UserBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KarmaUseCases(
    private val userDao: UserDao,
    private val badgeDao: BadgeDao,
    private val notificationDao: NotificationDao
) {

    companion object {
        const val KARMA_ATTEND_EVENT = 10
        const val KARMA_ORGANIZE_EVENT = 25
        const val KARMA_SKILL_EXCHANGE = 20
        const val KARMA_VOUCH = 15
        const val KARMA_CANCEL_PENALTY = -5
    }

    suspend fun awardKarma(userId: Int, points: Int, reason: String, eventId: Int = 0) {
        // 1. Audit Transaction
        val transaction = KarmaTransaction(
            from_user_id = 0, // System
            to_user_id = userId,
            event_id = eventId,
            points = points,
            given_at = DateFormats.storedDay()
        )
        userDao.insertKarmaTransaction(transaction)

        // 2. Add Karma
        userDao.addKarma(userId, points)

        // 3. Notification
        notificationDao.insertNotification(
            Notification(
                user_id = userId,
                title = "Karma Updated! ✨",
                message = "You earned $points karma for: $reason.",
                timestamp = System.currentTimeMillis(),
                type = "karma",
                event_id = eventId
            )
        )

        // 4. Check Automatic Badge Unlocking
        checkAndUnlockBadges(userId)
    }

    private suspend fun checkAndUnlockBadges(userId: Int) {
        val user = userDao.getUserById(userId) ?: return

        // Badge 1: FIRST_EVENT / Active Member if Karma >= 50
        if (user.karma_points >= 50) {
            badgeDao.awardBadge(UserBadge(user_id = userId, badge_id = 1, earned_at = "Today"))
        }

        // Badge 2: Community Helper if Karma >= 150
        if (user.karma_points >= 150) {
            badgeDao.awardBadge(UserBadge(user_id = userId, badge_id = 2, earned_at = "Today"))
        }
    }
}
