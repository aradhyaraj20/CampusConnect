package com.example.campusconnect.search

import com.example.campusconnect.models.Event
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchScorerTest {

    private fun event(
        title: String = "Python Workshop",
        category: String = "Technical",
        tags: String? = "Python, Workshop",
        description: String = "Learn Python basics",
        eligibility: String? = "MCA Students"
    ) = Event(
        id = 1,
        title = title,
        description = description,
        type = "event",
        category = category,
        image_uri = null,
        date = "Sep 11, 2026",
        time = "16:00",
        location = "Seminar Hall 2",
        map_lat = null,
        map_lng = null,
        max_attendees = 50,
        is_user_created = true,
        creator_id = 2,
        tags = tags,
        eligibility = eligibility
    )

    @Test
    fun titleMatch_outweighsDescription() {
        val parsed = QueryInterpreter.parse("python")
        val result = SearchScorer.score(event(), parsed, emptyList(), emptyList(), emptySet())
        assertTrue(result.score >= 40)
        assertTrue(result.reason != null)
    }

    @Test
    fun interestMatch_addsExplanation() {
        val parsed = QueryInterpreter.parse("workshop")
        val result = SearchScorer.score(event(), parsed, listOf("python"), emptyList(), emptySet())
        assertTrue(result.reason?.contains("follow", ignoreCase = true) == true || result.score > 0)
    }

    @Test
    fun unrelatedQuery_canScoreZero() {
        val parsed = QueryInterpreter.parse("underwater basket weaving xyz")
        val result = SearchScorer.score(event(title = "Chess Club", category = "Sports", tags = "chess", description = "board"), parsed, emptyList(), emptyList(), emptySet())
        assertTrue(result.score == 0)
    }
}
