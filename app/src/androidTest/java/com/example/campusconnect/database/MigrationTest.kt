package com.example.campusconnect.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room exports each schema version to app/schemas. Schemas 11-15 were never exported,
 * so only the current version can be validated today. When you bump the version, keep
 * the previous N.json, add a migration, and add a test like the template below.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun currentSchemaCreatesAndValidates() {
        helper.createDatabase("test-db", 18).close()
        helper.runMigrationsAndValidate("test-db", 18, true).close()
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

    // Template for the next migration:
    // @Test fun migrate16To17() {
    //     helper.createDatabase("test-db", 16).apply { /* insert v16 rows */ close() }
    //     helper.runMigrationsAndValidate("test-db", 17, true, AppDatabase.MIGRATION_16_17)
    // }

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
}
