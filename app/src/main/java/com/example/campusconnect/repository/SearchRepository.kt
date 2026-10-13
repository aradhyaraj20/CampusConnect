package com.example.campusconnect.repository

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.SearchHistory
import com.example.campusconnect.search.ParsedQuery
import com.example.campusconnect.search.QueryInterpreter
import com.example.campusconnect.search.SearchScorer
import kotlinx.coroutines.flow.first

/**
 * A single ranked search hit, with a human-readable reason so the UI can show
 * "Recommended because..." / "Matches your interest in..." per spec section 8.
 */
data class SearchResult(
    val event: Event,
    val score: Int,
    val reason: String?
)

class SearchRepository(
    private val eventDao: EventDao,
    private val userDao: UserDao,
    private val rsvpDao: RsvpDao
) {

    /**
     * Section 8 pipeline: parse query -> Intent/Keywords/Filters -> synonym expansion ->
     * hard-filter by date/eligibility when the query names them -> relevance score the rest.
     */
    suspend fun searchEvents(query: String, userId: Int): List<SearchResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parsed = QueryInterpreter.parse(trimmed)
        val allEvents = eventDao.getAllEvents().first()

        val dateFiltered = if (parsed.dateWindow != null) {
            allEvents.filter { it.date_timestamp in parsed.dateWindow.startMillis..parsed.dateWindow.endMillis }
        } else {
            allEvents
        }

        // Eligibility is a soft filter: only exclude events that explicitly restrict to a
        // different program. An event with no eligibility set (or "All") stays visible to everyone.
        val eligibilityFiltered = if (parsed.eligibilityHint != null) {
            dateFiltered.filter { event ->
                val elig = event.eligibility?.lowercase()?.trim()
                elig.isNullOrBlank() || elig.contains("all") || elig.contains(parsed.eligibilityHint)
            }
        } else {
            dateFiltered
        }

        val userInterests = userDao.getInterests(userId).first().map { it.interest_tag.lowercase().trim() }
        val recentQueries = userDao.getRecentSearch(userId).first().map { it.query.lowercase() }
        val goingEventIds = rsvpDao.getGoingEventIdsForUser(userId).toSet()
        val rsvpCategories = allEvents.filter { it.id in goingEventIds }.map { it.category.lowercase() }.toSet()

        return eligibilityFiltered
            .map { event -> SearchScorer.score(event, parsed, userInterests, recentQueries, rsvpCategories) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
    }

    suspend fun saveSearchHistory(userId: Int, query: String) {
        userDao.deleteSearchQuery(userId, query)
        userDao.insertSearchQuery(SearchHistory(user_id = userId, query = query, timestamp = System.currentTimeMillis()))
    }
}
