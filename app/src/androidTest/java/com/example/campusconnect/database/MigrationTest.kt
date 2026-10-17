package com.example.campusconnect.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room exports each schema version to app/schemas. Schemas 11-15 were never exported, so
 * migrations are tested from v16 (the oldest exported schema) up to the current version.
 * When you bump the version, keep the previous N.json, add a migration, add a test for it
 * and append it to [ALL_FROM_16].
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /** Upgrading the oldest exported schema through every migration must end at the current schema. */
    @Test
    fun migrateFrom16ToLatest_validatesAgainstCurrentSchema() {
        helper.createDatabase("test-db-chain", 16).close()
        helper.runMigrationsAndValidate("test-db-chain", LATEST, true, *ALL_FROM_16).close()
    }

    @Test
    fun migrate16To17_keepsChatRowsAsStudentMode() {
        helper.createDatabase("test-db-16", 16).apply {
            execSQL(
                "INSERT INTO ai_interactions (user_id, prompt, response, timestamp, is_user_message) " +
                    "VALUES (1, 'hi', '', 1, 1)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("test-db-16", 17, true, AppDatabase.MIGRATION_16_17)
        db.query("SELECT mode FROM ai_interactions").use {
            it.moveToFirst()
            org.junit.Assert.assertEquals("STUDENT", it.getString(0))
        }
        db.close()
    }

    @Test
    fun migrate17To18_removesDuplicatesAndFixesVoteTotals() {
        helper.createDatabase("test-db-17", 17).apply {
            // Same student RSVP'd twice (waitlisted, then going): keep the "going" row.
            execSQL("INSERT INTO rsvps (id, user_id, event_id, status, checked_in, joined_at) VALUES (1, 5, 9, 'waitlisted', 0, 't')")
            execSQL("INSERT INTO rsvps (id, user_id, event_id, status, checked_in, joined_at) VALUES (2, 5, 9, 'going', 0, 't')")
            // Seeded total of 10 includes 2 extra taps by user 5 on option 1.
            execSQL("INSERT INTO poll_options (id, poll_id, option_text, vote_count) VALUES (1, 1, 'A', 10)")
            execSQL("INSERT INTO poll_votes (id, poll_id, option_id, user_id) VALUES (1, 1, 1, 5)")
            execSQL("INSERT INTO poll_votes (id, poll_id, option_id, user_id) VALUES (2, 1, 1, 5)")
            execSQL("INSERT INTO poll_votes (id, poll_id, option_id, user_id) VALUES (3, 1, 1, 5)")
            close()
        }
        val db = helper.runMigrationsAndValidate("test-db-17", 18, true, AppDatabase.MIGRATION_17_18)
        db.query("SELECT id, status FROM rsvps").use {
            org.junit.Assert.assertEquals(1, it.count)
            it.moveToFirst()
            org.junit.Assert.assertEquals("going", it.getString(1))
        }
        db.query("SELECT COUNT(*) FROM poll_votes").use {
            it.moveToFirst(); org.junit.Assert.assertEquals(1, it.getInt(0))
        }
        db.query("SELECT vote_count FROM poll_options WHERE id = 1").use {
            it.moveToFirst(); org.junit.Assert.assertEquals(8, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate18To19_addsPollDeadlineAndPasswordFlag_dropsUnusedTables() {
        helper.createDatabase("test-db-18", 18).apply {
            execSQL("INSERT INTO polls (id, event_id, question, ends_at) VALUES (1, 3, 'Q?', '20:00')")
            execSQL(
                "INSERT INTO users (id, name, email, password_hash, created_at, karma_points, role, account_status, last_login_at) " +
                    "VALUES (7, 'N', 'n@x.com', 'h', 't', 0, 'ADMIN', 'ACTIVE', 0)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("test-db-18", 19, true, AppDatabase.MIGRATION_18_19)
        db.query("SELECT closes_at FROM polls WHERE id = 1").use {
            it.moveToFirst(); org.junit.Assert.assertEquals(0L, it.getLong(0))
        }
        db.query("SELECT must_change_password FROM users WHERE id = 7").use {
            it.moveToFirst(); org.junit.Assert.assertEquals(0, it.getInt(0))
        }
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN " +
                "('friendships', 'study_group_members', 'settings', 'badges', 'user_badges', 'skill_listings')"
        ).use {
            it.moveToFirst(); org.junit.Assert.assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val LATEST = 19
        val ALL_FROM_16 = arrayOf(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19)
    }
}
