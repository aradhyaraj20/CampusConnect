package com.example.campusconnect.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.campusconnect.domain.usecase.RSVPUseCases
import com.example.campusconnect.models.*

@Database(
    entities = [
        User::class,
        Event::class,
        RSVP::class,
        Message::class,
        Poll::class,
        PollOption::class,
        PollVote::class,
        RecapPhoto::class,
        MarketplaceItem::class,
        KarmaTransaction::class,
        Notification::class,
        UserInterest::class,
        SearchHistory::class,
        AIInteraction::class,
        Attendance::class,
        Report::class,
        Ad::class
    ],
    version = 19,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun rsvpDao(): RsvpDao
    abstract fun messageDao(): MessageDao
    abstract fun pollDao(): PollDao
    abstract fun marketplaceDao(): MarketplaceDao
    abstract fun notificationDao(): NotificationDao
    abstract fun aiInteractionDao(): AIInteractionDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun reportDao(): ReportDao
    abstract fun adDao(): AdDao
    abstract fun userDataDao(): UserDataDao

    /**
     * Removes a user and every row that references them, atomically. Seats they held are handed to
     * the waitlist, their poll votes leave the totals, and anyone booked on an event they organised
     * gets [cancelNotice] (title, message) built from that event's title.
     */
    suspend fun eraseUser(userId: Int, cancelNotice: ((eventTitle: String) -> Pair<String, String>)? = null) = withTransaction {
        val d = userDataDao()
        val audience = d.getAudienceOfOwnedEvents(userId)

        d.releasePollVotes(userId); d.deletePollVotes(userId)
        val rsvps = RSVPUseCases(rsvpDao(), eventDao(), notificationDao())
        rsvpDao().getGoingEventIdsForUser(userId).forEach { rsvps.cancelRsvpAndPromoteWaitlist(userId, it) }

        d.deleteVotesOnOwnedEvents(userId); d.deleteOptionsOnOwnedEvents(userId)
        d.deletePollsOnOwnedEvents(userId); d.deleteRsvpsOnOwnedEvents(userId)
        d.deleteMessagesOnOwnedEvents(userId); d.deleteRecapsOnOwnedEvents(userId)
        d.deleteAttendanceOnOwnedEvents(userId); d.deleteNotificationsOnOwnedEvents(userId)
        d.deleteReportsOnOwnedEvents(userId); d.deleteKarmaOnOwnedEvents(userId)
        d.deleteOwnedEvents(userId)

        d.deleteRsvps(userId); d.deleteMessages(userId); d.deleteRecapPhotos(userId)
        d.deleteMarketplaceItems(userId); d.deleteAds(userId)
        d.deleteKarmaTransactions(userId); d.deleteNotifications(userId); d.deleteInterests(userId)
        d.deleteSearchHistory(userId); d.deleteAiInteractions(userId); d.deleteAttendance(userId)
        d.deleteReports(userId)
        userDao().deleteUserById(userId)

        if (cancelNotice != null) {
            val now = System.currentTimeMillis()
            audience.forEach { a ->
                val (title, message) = cancelNotice(a.title)
                notificationDao().insertNotification(
                    Notification(user_id = a.user_id, title = title, message = message, timestamp = now, type = "event_update")
                )
            }
        }
    }

    /**
     * Deletes an event and everything hanging off it (no foreign keys, so done by hand). Everyone
     * holding a seat or a waitlist spot, except [actorId], is sent [noticeTitle]/[noticeMessage]
     * in the same transaction.
     */
    suspend fun deleteEventCascade(
        eventId: Int,
        noticeTitle: String? = null,
        noticeMessage: String? = null,
        actorId: Int = 0
    ) = withTransaction {
        val recipients = if (noticeTitle != null && noticeMessage != null) {
            rsvpDao().getActiveRsvpsForEvent(eventId).map { it.user_id }.filter { it != actorId }.distinct()
        } else emptyList()

        val d = userDataDao()
        d.deleteVotesForEvent(eventId); d.deleteOptionsForEvent(eventId); d.deletePollsForEvent(eventId)
        d.deleteRsvpsForEvent(eventId); d.deleteMessagesForEvent(eventId); d.deleteRecapsForEvent(eventId)
        d.deleteAttendanceForEvent(eventId); d.deleteNotificationsForEvent(eventId)
        d.deleteReportsForEvent(eventId); d.deleteKarmaForEvent(eventId)
        d.deleteEventById(eventId)

        val now = System.currentTimeMillis()
        recipients.forEach { userId ->
            notificationDao().insertNotification(
                Notification(user_id = userId, title = noticeTitle!!, message = noticeMessage!!, timestamp = now, type = "event_update")
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `ticketId` TEXT NOT NULL,
                        `checkInTime` INTEGER NOT NULL,
                        `checkedInBy` INTEGER NOT NULL,
                        `status` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `skill_listings` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `userId` INTEGER NOT NULL,
                        `skillName` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `level` TEXT NOT NULL,
                        `availability` TEXT NOT NULL,
                        `expectedExchange` TEXT NOT NULL,
                        `rating` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reportedBy` INTEGER NOT NULL,
                        `targetType` TEXT NOT NULL,
                        `targetId` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Phase A: role-based dashboard architecture - adds the role column that
        // SessionManager/Role.kt already expected but the User entity never persisted.
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `users` ADD COLUMN `role` TEXT NOT NULL DEFAULT 'STUDENT'"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `users` ADD COLUMN `account_status` TEXT NOT NULL DEFAULT 'ACTIVE'"
                )
            }
        }

        // Adds course + duration captured at registration, and last_login_at so
        // AccountCleanupWorker can find accounts that have gone quiet.
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `users` ADD COLUMN `course` TEXT")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `course_duration_months` INTEGER")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `course_end_at` INTEGER")
                db.execSQL(
                    "ALTER TABLE `users` ADD COLUMN `last_login_at` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_email` ON `users` (`email`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_role` ON `users` (`role`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_users_course` ON `users` (`course`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_users_account_status` ON `users` (`account_status`)"
                )
            }
        }

        // Adds the admin-managed ads table (Phase G: admin can display ads to students).
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ads` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `message` TEXT NOT NULL,
                        `image_uri` TEXT,
                        `link_url` TEXT,
                        `is_active` INTEGER NOT NULL DEFAULT 1,
                        `created_by` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Keeps Ask AI, Create Event and Analytics chats in separate threads.
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ai_interactions` ADD COLUMN `mode` TEXT NOT NULL DEFAULT 'STUDENT'")
            }
        }

        // Removes duplicate RSVPs / poll votes / check-ins, then enforces uniqueness and
        // adds indices for the columns every screen filters on.
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep one RSVP per (user, event), preferring the "going" row.
                db.execSQL(
                    "DELETE FROM rsvps WHERE id NOT IN (SELECT COALESCE(MIN(CASE WHEN status = 'going' THEN id END), MIN(id)) " +
                        "FROM rsvps GROUP BY user_id, event_id)"
                )
                // Take duplicate votes back out of the option totals before deleting them.
                db.execSQL(
                    "UPDATE poll_options SET vote_count = MAX(0, vote_count - (SELECT COUNT(*) FROM poll_votes v " +
                        "WHERE v.option_id = poll_options.id AND v.id NOT IN (SELECT MIN(id) FROM poll_votes GROUP BY poll_id, user_id)))"
                )
                db.execSQL("DELETE FROM poll_votes WHERE id NOT IN (SELECT MIN(id) FROM poll_votes GROUP BY poll_id, user_id)")
                db.execSQL("DELETE FROM attendance WHERE id NOT IN (SELECT MIN(id) FROM attendance GROUP BY eventId, userId)")

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_rsvps_user_id_event_id` ON `rsvps` (`user_id`, `event_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_rsvps_event_id_status` ON `rsvps` (`event_id`, `status`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_poll_votes_poll_id_user_id` ON `poll_votes` (`poll_id`, `user_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attendance_eventId_userId` ON `attendance` (`eventId`, `userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_creator_id` ON `events` (`creator_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_date_timestamp` ON `events` (`date_timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_event_id_sent_at` ON `messages` (`event_id`, `sent_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_user_id_timestamp` ON `notifications` (`user_id`, `timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_polls_event_id` ON `polls` (`event_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_poll_options_poll_id` ON `poll_options` (`poll_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recap_photos_event_id` ON `recap_photos` (`event_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_interactions_user_id_mode_timestamp` ON `ai_interactions` (`user_id`, `mode`, `timestamp`)")
            }
        }

        /**
         * Adds poll deadlines and the forced password change flag, and drops tables for features
         * that were removed or never used: friendships, study_group_members, settings, badges,
         * user_badges and skill_listings.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `polls` ADD COLUMN `closes_at` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `users` ADD COLUMN `must_change_password` INTEGER NOT NULL DEFAULT 0")
                listOf("friendships", "study_group_members", "settings", "badges", "user_badges", "skill_listings")
                    .forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "campus_connect_database"
                )
                    .addMigrations(
                        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
                        MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
                        MIGRATION_18_19
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
