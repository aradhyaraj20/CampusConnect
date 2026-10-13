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
        helper.createDatabase("test-db", 17).close()
        helper.runMigrationsAndValidate("test-db", 17, true).close()
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
}
