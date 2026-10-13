package com.example.campusconnect.search

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryInterpreterTest {
    @Test
    fun footballQuery_expandsSportsSynonyms() {
        val parsed = QueryInterpreter.parse("I like football")
        assertTrue(parsed.expandedTerms.any { it.contains("football", ignoreCase = true) })
        assertTrue(parsed.matchedTopics.isNotEmpty())
    }

    @Test
    fun weekendAiQuery_hasDateWindow() {
        val parsed = QueryInterpreter.parse("I want something related to AI this weekend")
        assertNotNull(parsed.dateWindow)
    }

    @Test
    fun eligibilityAndTomorrow_detected() {
        val parsed = QueryInterpreter.parse("Events for MCA students tomorrow")
        assertNotNull(parsed.dateWindow)
        assertNotNull(parsed.eligibilityHint)
    }
}
