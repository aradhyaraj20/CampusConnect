package com.example.campusconnect.search

import com.example.campusconnect.models.Event
import com.example.campusconnect.repository.SearchResult

object SearchScorer {

    fun score(
        event: Event,
        parsed: ParsedQuery,
        userInterests: List<String>,
        recentQueries: List<String>,
        rsvpCategories: Set<String>
    ): SearchResult {
        val title = event.title.lowercase()
        val desc = event.description.lowercase()
        val category = event.category.lowercase()
        val tags = (event.tags ?: "").lowercase()
        val eligibility = (event.eligibility ?: "").lowercase()

        var score = 0
        val reasonCandidates = mutableListOf<Pair<Int, String>>()
        fun consider(points: Int, candidateReason: String) {
            score += points
            reasonCandidates.add(points to candidateReason)
        }

        val allTerms = parsed.expandedTerms + parsed.keywords
        if (allTerms.any { it.isNotBlank() && title == it }) {
            consider(60, "Exact match for \"${parsed.rawQuery}\"")
        }
        if (allTerms.any { it.length > 1 && title.contains(it) }) {
            consider(40, "Matches your search in the title")
        }
        if (allTerms.any { it.length > 1 && tags.contains(it) }) {
            consider(25, "Tagged ${event.tags}")
        }
        if (allTerms.any { it.length > 1 && category.contains(it) }) {
            consider(20, "In ${event.category}")
        }
        if (allTerms.any { it.length > 1 && desc.contains(it) }) {
            consider(10, "Mentioned in the event description")
        }

        val matchedInterest = userInterests.firstOrNull {
            it.isNotBlank() && (category.contains(it) || tags.contains(it) || title.contains(it))
        }
        if (matchedInterest != null) {
            consider(30, "Recommended because you follow ${matchedInterest.replaceFirstChar { c -> c.uppercase() }}")
        }

        for (topic in parsed.matchedTopics) {
            if (category.contains(topic) || tags.contains(topic) || title.contains(topic)) {
                consider(22, "Matches your interest in ${topic.replaceFirstChar { c -> c.uppercase() }}")
            }
        }

        if (parsed.dateWindow != null && event.date_timestamp in parsed.dateWindow.startMillis..parsed.dateWindow.endMillis) {
            consider(20, "Happening ${parsed.dateWindow.label}")
        }
        if (parsed.eligibilityHint != null && eligibility.contains(parsed.eligibilityHint)) {
            consider(15, "Open to ${event.eligibility}")
        }

        val pastMatches = recentQueries.count { pq -> allTerms.any { it.length > 1 && pq.contains(it) } }
        if (pastMatches > 0) {
            consider((pastMatches * 5).coerceAtMost(15), "Similar to your past searches")
        }
        if (category in rsvpCategories) {
            consider(18, "You've attended ${event.category} events before")
        }

        return SearchResult(event, score, reasonCandidates.maxByOrNull { it.first }?.second)
    }
}
