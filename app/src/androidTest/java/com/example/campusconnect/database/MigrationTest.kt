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
        helper.createDatabase("test-db", 16).close()
        helper.runMigrationsAndValidate("test-db", 16, true).close()
    }

    // Template for the next migration:
    // @Test fun migrate16To17() {
    //     helper.createDatabase("test-db", 16).apply { /* insert v16 rows */ close() }
    //     helper.runMigrationsAndValidate("test-db", 17, true, AppDatabase.MIGRATION_16_17)
    // }
}
