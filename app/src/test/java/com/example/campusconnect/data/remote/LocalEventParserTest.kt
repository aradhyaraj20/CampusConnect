package com.example.campusconnect.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalEventParserTest {
    @Test
    fun completePrompt_extractsRequiredFields() {
        val draft = LocalEventParser.extract(
            "I want to organize a Python workshop for 50 MCA students next Friday at 4 PM in Seminar Hall 2",
            null
        )
        assertNotNull(draft.title)
        assertTrue(draft.title!!.contains("Python", ignoreCase = true))
        assertTrue(draft.dateFound)
        assertEquals("16:00", draft.time)
        assertTrue(draft.location!!.contains("Seminar Hall", ignoreCase = true))
        assertEquals(50, draft.capacity)
    }

    @Test
    fun incompletePrompt_doesNotInventDateOrLocation() {
        val draft = LocalEventParser.extract("create a coding meetup", null)
        assertFalse(draft.dateFound)
        assertNull(draft.time)
        assertNull(draft.location)
        assertNull(draft.capacity)
    }

    @Test
    fun pendingLocation_acceptsDirectAnswer() {
        val draft = LocalEventParser.extract("Seminar Hall 2", "location")
        assertEquals("Seminar Hall 2", draft.location)
    }
}
