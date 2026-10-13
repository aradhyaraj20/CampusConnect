package com.example.campusconnect.search

import java.util.Calendar
import java.util.TimeZone

/**
 * A date/time window an event's date_timestamp must fall inside, derived from a
 * phrase like "tomorrow" or "this weekend". All calculations use Asia/Kolkata per
 * spec section 13.
 */
data class DateWindow(
    val startMillis: Long,
    val endMillis: Long,
    val label: String
)

data class ParsedQuery(
    val rawQuery: String,
    val keywords: List<String>,
    val expandedTerms: Set<String>,
    val dateWindow: DateWindow?,
    val eligibilityHint: String?,
    val matchedTopics: List<String>
)

/**
 * Implements the pipeline from spec section 8:
 *   Query -> Intent / Keywords / Filters -> Synonym Expansion -> scored against events.
 * Purely rule-based (no network call) so search stays instant and works offline -
 * this is deliberately separate from the Gemini-backed "Ask CampusConnect AI" assistant.
 */
object QueryInterpreter {

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    private val stopwords = setOf(
        "i", "want", "something", "related", "to", "the", "a", "an", "for", "of", "on", "in",
        "at", "is", "are", "me", "show", "find", "need", "events", "event", "this", "that",
        "with", "and", "or", "can", "you", "which", "what", "when", "any", "some", "my"
    )

    // Broad interest/topic dictionary covering the query styles from spec section 7
    // ("I like football", "AI this weekend", "DBMS study group", etc). Deliberately wider
    // than the old hardcoded map so more natural phrasing actually resolves to something.
    private val topicSynonyms: Map<String, List<String>> = mapOf(
        "football" to listOf("soccer", "football", "fifa"),
        "cricket" to listOf("cricket", "ipl", "batting", "bowling"),
        "sports" to listOf(
            "sports", "cricket", "basketball", "badminton", "volleyball", "athletics",
            "tournament", "match", "fitness", "gym", "football", "soccer"
        ),
        "ai" to listOf(
            "ai", "artificial intelligence", "machine learning", "ml", "deep learning",
            "neural", "data science", "nlp", "hackathon"
        ),
        "coding" to listOf(
            "coding", "programming", "developer", "hackathon", "software", "python",
            "java", "code", "dev", "web", "app development"
        ),
        "dbms" to listOf("dbms", "database", "sql", "rdbms", "query", "normalization"),
        "study" to listOf(
            "study group", "study", "revision", "exam", "prep", "preparation",
            "tutoring", "peer learning", "mentor"
        ),
        "cultural" to listOf("cultural", "dance", "music", "art", "drama", "fest", "singing", "painting"),
        "social" to listOf("social", "party", "gathering", "meetup", "fun", "concert"),
        "career" to listOf("career", "placement", "internship", "resume", "job", "interview"),
        "workshop" to listOf("workshop", "seminar", "lecture", "lab", "bootcamp", "training")
    )

    private val eligibilityKeywords = listOf(
        "mca", "bca", "btech", "mtech", "mba", "bba", "bsc", "msc",
        "freshers", "final year", "first year", "second year", "third year"
    )

    fun parse(query: String): ParsedQuery {
        val lower = query.lowercase().trim()
        val tokens = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
        val keywords = tokens.filter { it !in stopwords && it.length > 1 }

        val matchedTopics = mutableListOf<String>()
        val expanded = mutableSetOf<String>()
        expanded.addAll(keywords)

        for ((topic, terms) in topicSynonyms) {
            val topicMentioned = lower.contains(topic) || terms.any { lower.contains(it) }
            if (topicMentioned) {
                matchedTopics.add(topic)
                expanded.addAll(terms)
            }
        }

        val dateWindow = parseDateWindow(lower)
        val eligibility = eligibilityKeywords.firstOrNull { lower.contains(it) }

        return ParsedQuery(
            rawQuery = query,
            keywords = keywords,
            expandedTerms = expanded,
            dateWindow = dateWindow,
            eligibilityHint = eligibility,
            matchedTopics = matchedTopics
        )
    }

    private fun startOfDay(c: Calendar): Long {
        val copy = c.clone() as Calendar
        copy.set(Calendar.HOUR_OF_DAY, 0)
        copy.set(Calendar.MINUTE, 0)
        copy.set(Calendar.SECOND, 0)
        copy.set(Calendar.MILLISECOND, 0)
        return copy.timeInMillis
    }

    private fun endOfDay(c: Calendar): Long = startOfDay(c) + 24 * 60 * 60 * 1000L - 1

    private fun parseDateWindow(lower: String): DateWindow? {
        val now = Calendar.getInstance(IST)

        return when {
            "tomorrow" in lower -> {
                val c = now.clone() as Calendar
                c.add(Calendar.DAY_OF_YEAR, 1)
                DateWindow(startOfDay(c), endOfDay(c), "tomorrow")
            }
            "today" in lower || "tonight" in lower -> {
                DateWindow(startOfDay(now), endOfDay(now), "today")
            }
            "this weekend" in lower || "weekend" in lower -> {
                val dayOfWeek = now.get(Calendar.DAY_OF_WEEK) // Sunday=1 .. Saturday=7
                val daysUntilSat = ((Calendar.SATURDAY - dayOfWeek) + 7) % 7
                val sat = now.clone() as Calendar
                sat.add(Calendar.DAY_OF_YEAR, daysUntilSat)
                val sun = sat.clone() as Calendar
                sun.add(Calendar.DAY_OF_YEAR, 1)
                DateWindow(startOfDay(sat), endOfDay(sun), "this weekend")
            }
            "this week" in lower -> {
                val end = now.clone() as Calendar
                end.add(Calendar.DAY_OF_YEAR, 7)
                DateWindow(startOfDay(now), endOfDay(end), "this week")
            }
            "next week" in lower -> {
                val start = now.clone() as Calendar
                start.add(Calendar.DAY_OF_YEAR, 7)
                val end = start.clone() as Calendar
                end.add(Calendar.DAY_OF_YEAR, 7)
                DateWindow(startOfDay(start), endOfDay(end), "next week")
            }
            else -> null
        }
    }
}
