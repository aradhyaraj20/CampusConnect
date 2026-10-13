package com.example.campusconnect.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParseValidationTest {

    @Test
    fun completeOrganizerPrompt_isReadyForPreview() {
        val draft = LocalEventParser.extract(
            "I want to conduct a Python workshop for 50 MCA students next Friday at 4 PM in Seminar Hall 2",
            null
        )
        assertTrue(draft.dateFound)
        assertFalse(draft.time.isNullOrBlank())
        assertFalse(draft.location.isNullOrBlank())
        assertFalse(draft.title.isNullOrBlank())
    }

    @Test
    fun incompletePrompt_staysIncomplete() {
        val draft = LocalEventParser.extract("workshop", null)
        assertFalse(draft.dateFound)
        assertTrue(draft.time.isNullOrBlank())
        assertTrue(draft.location.isNullOrBlank())
    }
}
