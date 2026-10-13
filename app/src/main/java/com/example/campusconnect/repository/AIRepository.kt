package com.example.campusconnect.repository

import com.example.campusconnect.core.ai.AIConfig
import com.example.campusconnect.data.remote.AIDataSource
import com.example.campusconnect.data.remote.EventDraftExtraction
import com.example.campusconnect.data.remote.GeminiAIDataSource
import com.example.campusconnect.data.remote.LocalEventParser
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.search.QueryInterpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AIRepository(
    private val eventDao: EventDao,
    private val userDao: UserDao? = null,
    private val rsvpDao: RsvpDao? = null,
    private val reportDao: ReportDao? = null,
    private val remote: AIDataSource = GeminiAIDataSource()
) {

    suspend fun parseEventRequest(
        prompt: String,
        pendingMissingField: String? = null,
        conversation: List<Pair<Boolean, String>> = emptyList()
    ): AIEventResponse = withContext(Dispatchers.IO) {
        val extracted = extractDraft(prompt, pendingMissingField, conversation)
        val event = toEvent(extracted, prompt)
        val missing = mutableListOf<String>()
        if (event.title.isBlank()) missing.add("title")
        if (!extracted.dateFound || event.date.isBlank()) missing.add("date")
        if (event.time.isBlank()) missing.add("time")
        if (event.location.isBlank()) missing.add("location")
        val conflict = if (event.location.isNotBlank() && event.date_timestamp > 0) {
            checkConflicts(event.date_timestamp, event.location, event.time)
        } else null
        AIEventResponse(event, missing.isEmpty(), missing, conflict, extracted.source)
    }

    private suspend fun extractDraft(
        prompt: String,
        pendingField: String?,
        conversation: List<Pair<Boolean, String>>
    ): EventDraftExtraction {
        val local = LocalEventParser.extract(prompt, pendingField)
        if (!AIConfig.isConfigured) return local
        return try {
            mergeDrafts(local, remote.extractEventDraft(conversation.ifEmpty { listOf(true to prompt) }, pendingField))
        } catch (_: Exception) {
            local
        }
    }

    private fun mergeDrafts(local: EventDraftExtraction, remote: EventDraftExtraction) = EventDraftExtraction(
        title = remote.title ?: local.title,
        dateDisplay = if (remote.dateFound) remote.dateDisplay else local.dateDisplay,
        dateTimestamp = if (remote.dateFound) remote.dateTimestamp else local.dateTimestamp,
        dateFound = remote.dateFound || local.dateFound,
        time = remote.time ?: local.time,
        location = remote.location ?: local.location,
        capacity = remote.capacity ?: local.capacity,
        eligibility = remote.eligibility ?: local.eligibility,
        category = remote.category ?: local.category,
        tags = remote.tags ?: local.tags,
        description = remote.description ?: local.description,
        source = remote.source
    )

    private fun toEvent(extracted: EventDraftExtraction, prompt: String) = Event(
        title = extracted.title.orEmpty(),
        description = extracted.description ?: "Created from: $prompt",
        type = if (extracted.category == "Academic") "study_group" else "event",
        category = extracted.category ?: "General",
        image_uri = null,
        date = extracted.dateDisplay.orEmpty(),
        time = extracted.time.orEmpty(),
        location = extracted.location.orEmpty(),
        map_lat = null,
        map_lng = null,
        max_attendees = extracted.capacity,
        is_user_created = true,
        creator_id = 0,
        date_timestamp = extracted.dateTimestamp,
        eligibility = extracted.eligibility,
        tags = extracted.tags,
        status = "active"
    )

    private suspend fun checkConflicts(timestamp: Long, location: String, time: String): String? {
        val window = 60 * 60 * 1000L
        val conflicting = eventDao.getAllEvents().first().find {
            it.status != "cancelled" &&
                it.location.equals(location, ignoreCase = true) &&
                kotlin.math.abs(it.date_timestamp - timestamp) < window &&
                (time.isBlank() || it.time.isBlank() || it.time == time)
        }
        return conflicting?.let { "⚠️ ${it.location} already has '${it.title}' scheduled at this time." }
    }

    /**
     * Organizer "Enhance with AI" event description rewrite (spec section 15/37).
     *
     * Uses the exact same shared-key pipeline as the student and admin assistants:
     * whatever key is currently active in AIConfig (admin runtime override, or the
     * build-time key) is used automatically - the organizer never enters a key of
     * their own. If no key is configured anywhere, falls back to a clearly-labelled
     * local rewrite so the button still does something useful offline.
     */
    suspend fun enhanceEventDescription(title: String, description: String): AiDescriptionResult =
        withContext(Dispatchers.IO) {
            if (AIConfig.isConfigured) {
                val rewritten = runCatching {
                    remote.answerWithFacts(
                        """
                        You are an expert event coordinator for a college campus. Rewrite the
                        event description below to make it more engaging, exciting, and
                        grammatically correct. Keep it factually consistent with the original -
                        never invent a date, time, location, price, or capacity that wasn't
                        already stated. Return ONLY the rewritten description text, with no
                        preamble, quotes, or markdown.
                        """.trimIndent(),
                        "Event title: $title\n\nCurrent description:\n${description.ifBlank { "(none yet)" }}",
                        "Title: $title\nDescription: ${description.ifBlank { "(none yet)" }}"
                    )
                }.getOrNull()
                if (!rewritten.isNullOrBlank()) {
                    return@withContext AiDescriptionResult(rewritten.trim(), fromAi = true)
                }
            }
            // Offline / no-key fallback - a light local rewrite, clearly not AI-generated.
            val base = description.ifBlank { "Join us for $title on campus!" }
            AiDescriptionResult(
                "$base\n\nDon't miss out - grab your spot and bring a friend!",
                fromAi = false
            )
        }

    /**
     * Phrases the CREATE-mode chat reply through Gemini (shared key), instead of the
     * old hardcoded "What should we call the event?" style strings. Gemini only ever
     * chooses *wording* here - it is explicitly told the next field to ask for and
     * told never to invent event details; the actual state machine (which field is
     * missing, when the draft is complete) still lives in AIViewModel/AIRepository,
     * not in the model's response. Falls back to a plain templated string when no key
     * is configured or the call fails, so the wizard still works offline.
     */
    suspend fun craftCreateEventReply(
        userMessage: String,
        draftSummary: String,
        nextMissingField: String?,
        isComplete: Boolean
    ): String = withContext(Dispatchers.IO) {
        val fallback = fallbackCreateEventReply(nextMissingField, isComplete)
        if (!AIConfig.isConfigured) return@withContext fallback

        val phrased = runCatching {
            remote.answerWithFacts(
                """
                You are CampusConnect's event-creation chat assistant (organizer-facing).
                You are helping fill in an event with these fields: title, date, time,
                location, and optionally capacity/category/eligibility/description.
                Rules:
                - Reply in 1-2 short, friendly sentences. No markdown.
                - If the organizer asks who/what you are, or greets you, briefly answer
                  in one sentence, then continue helping.
                - Never invent or assume a title, date, time, location, or any other
                  event detail that isn't in the draft below.
                - If "next missing field" is not "none", your reply MUST end by asking
                  specifically for that one field - do not ask about anything else.
                - If "next missing field" is "none", tell them the draft is ready to
                  review, do not ask another question.
                """.trimIndent(),
                userMessage,
                "Draft so far: $draftSummary\nNext missing field: ${nextMissingField ?: "none"}"
            )
        }.getOrNull()?.trim()

        phrased?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun fallbackCreateEventReply(nextMissingField: String?, isComplete: Boolean): String {
        if (isComplete) return "I've gathered the details. Please confirm the preview."
        return when (nextMissingField) {
            "title" -> "What should we call the event?"
            "date" -> "Which date should it be on? For example: next Friday or 11 September."
            "time" -> "What time should the event start?"
            "location" -> "Where will it take place?"
            else -> "Could you share a bit more detail?"
        }
    }

    suspend fun answerStudentQuery(query: String, userId: Int): String = withContext(Dispatchers.IO) {
        val facts = buildStudentFacts(query, userId)
        if (AIConfig.isConfigured) {
            val phrased = runCatching {
                remote.answerWithFacts(
                    "You are CampusConnect student assistant. Answer only from the facts. Never invent events.",
                    query,
                    facts
                )
            }.getOrNull()
            if (!phrased.isNullOrBlank()) return@withContext phrased
        }
        facts
    }

    private suspend fun buildStudentFacts(query: String, userId: Int): String {
        val events = eventDao.getAllEvents().first().filter { it.status != "cancelled" }
        val lower = query.lowercase(Locale.getDefault())
        val parsed = QueryInterpreter.parse(query)
        if (lower.contains("rsvp") || lower.contains("going") || lower.contains("my next")) {
            val goingIds = rsvpDao?.getGoingEventIdsForUser(userId).orEmpty()
            val mine = events.filter { it.id in goingIds }.sortedBy { it.date_timestamp }
            if (mine.isEmpty()) return "You have not RSVP'd to any events yet."
            if (lower.contains("next")) {
                val next = mine.firstOrNull { it.date_timestamp >= System.currentTimeMillis() } ?: mine.last()
                return "Your next event is ${next.title} on ${next.date} at ${next.time} (${next.location})."
            }
            return "You RSVP'd to:\n" + mine.take(8).joinToString("\n") {
                "• ${it.title} — ${it.date} ${it.time} @ ${it.location}"
            }
        }
        var matches = events
        parsed.dateWindow?.let { w -> matches = matches.filter { it.date_timestamp in w.startMillis..w.endMillis } }
        parsed.eligibilityHint?.let { hint ->
            matches = matches.filter { it.eligibility.isNullOrBlank() || it.eligibility.contains(hint, true) }
        }
        if (parsed.expandedTerms.isNotEmpty()) {
            val filtered = matches.filter { event ->
                val blob = listOfNotNull(event.title, event.description, event.category, event.tags, event.eligibility)
                    .joinToString(" ").lowercase(Locale.getDefault())
                parsed.expandedTerms.any { blob.contains(it.lowercase(Locale.getDefault())) }
            }
            if (filtered.isNotEmpty()) matches = filtered
        }
        if (matches.isEmpty()) return "I could not find matching campus events for \"$query\". Try regular search."
        return "Here's what I found:\n" + matches.take(8).joinToString("\n") {
            "• ${it.title} — ${it.date} ${it.time} @ ${it.location}"
        }
    }

    /**
     * Admin analytics assistant (spec section 18).
     *
     * Previously this method only pattern-matched a handful of keywords and returned a
     * fixed campus snapshot for anything else - so every question produced the same
     * answer and the Gemini key was never used. It now follows the same
     * facts-then-phrase pipeline as the student assistant: real aggregates are computed
     * from the database first, then Gemini is asked to answer the question USING ONLY
     * those facts. Gemini never touches the database and never performs actions.
     */
    suspend fun answerAdminQuery(query: String): AdminAIAnswer = withContext(Dispatchers.IO) {
        val lower = query.lowercase(Locale.getDefault())
        val facts = buildAdminFacts(query)
        val pendingReports = reportDao?.getPendingReportCount() ?: 0
        val suggestReports = lower.contains("report") && pendingReports > 0

        if (AIConfig.isConfigured) {
            val phrased = runCatching {
                remote.answerWithFacts(
                    """
                    You are the CampusConnect admin analytics assistant.
                    Answer the administrator's question using ONLY the statistics provided.
                    Be concise and specific - quote the actual numbers. Use short bullet
                    lines when listing. If the statistics do not contain what was asked,
                    say so plainly and suggest which admin screen would show it.
                    Never invent events, users, or figures. Never claim to have performed
                    an action - you are read-only and can only report and recommend.
                    """.trimIndent(),
                    query,
                    facts
                )
            }.getOrNull()
            if (!phrased.isNullOrBlank()) {
                return@withContext AdminAIAnswer(phrased.trim(), suggestReports)
            }
        }

        // Offline / no-key fallback: return the raw computed facts rather than a
        // generic snapshot, so the answer still reflects the question that was asked.
        AdminAIAnswer(facts, suggestReports)
    }

    /**
     * Computes the aggregates relevant to the question. Everything here is real data
     * pulled from Room - this is the only source Gemini is allowed to speak from.
     */
    private suspend fun buildAdminFacts(query: String): String {
        val lower = query.lowercase(Locale.getDefault())
        val events = eventDao.getAllEvents().first()
        val active = events.filter { it.status != "cancelled" }
        val users = userDao?.getAllUsers()?.first().orEmpty()
        val pendingReports = reportDao?.getPendingReportCount() ?: 0
        val rsvpTotal = rsvpDao?.getTotalRsvpCount() ?: 0

        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        val cal = Calendar.getInstance(ist).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        val dayMs = 86_400_000L
        val weekStart = todayStart - 7 * dayMs
        val monthStart = todayStart - 30 * dayMs

        val sb = StringBuilder()

        sb.appendLine("PLATFORM TOTALS")
        sb.appendLine("- Total users: ${users.size}")
        sb.appendLine("- Students: ${users.count { it.role == "STUDENT" }}")
        sb.appendLine("- Organizers: ${users.count { it.role == "ORGANIZER" }}")
        sb.appendLine("- Admins: ${users.count { it.role == "ADMIN" }}")
        sb.appendLine("- Total events: ${events.size} (active ${active.size}, cancelled ${events.size - active.size})")
        sb.appendLine("- Total RSVPs: $rsvpTotal")
        sb.appendLine("- Pending moderation reports: $pendingReports")

        sb.appendLine()
        sb.appendLine("TIME WINDOWS (Asia/Kolkata)")
        sb.appendLine("- Events dated today: ${active.count { it.date_timestamp in todayStart until todayStart + dayMs }}")
        sb.appendLine("- Events in the last 7 days: ${active.count { it.date_timestamp in weekStart until todayStart + dayMs }}")
        sb.appendLine("- Events in the last 30 days: ${active.count { it.date_timestamp in monthStart until todayStart + dayMs }}")
        sb.appendLine("- Upcoming events: ${active.count { it.date_timestamp >= todayStart }}")

        val ranked = active.map { e -> e to (rsvpDao?.getAttendeeCount(e.id) ?: 0) }
            .sortedByDescending { it.second }

        if (ranked.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("EVENTS BY RSVP COUNT (highest first)")
            ranked.take(10).forEach { (e, n) ->
                sb.appendLine("- ${e.title} | ${e.category} | ${e.date} | $n RSVPs | capacity ${e.max_attendees ?: "unlimited"}")
            }
            if (ranked.size > 3) {
                sb.appendLine()
                sb.appendLine("LOWEST RSVP EVENTS")
                ranked.takeLast(5).reversed().forEach { (e, n) ->
                    sb.appendLine("- ${e.title} | ${e.date} | $n RSVPs")
                }
            }
        }

        val cats = runCatching { eventDao.getCategoryCounts() }.getOrDefault(emptyList())
        if (cats.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("EVENTS BY CATEGORY")
            cats.forEach { sb.appendLine("- ${it.category}: ${it.count} events") }
        }

        val byCreator = active.groupBy { it.creator_id }
            .map { (id, list) -> (users.find { it.id == id }?.name ?: "User $id") to list.size }
            .sortedByDescending { it.second }
        if (byCreator.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("EVENTS CREATED PER ORGANIZER")
            byCreator.take(10).forEach { (n, c) -> sb.appendLine("- $n: $c events") }
        }

        // Only attach the per-event attendance detail when it is actually being asked
        // about, to keep the prompt small and the answer focused.
        if (lower.contains("attend") || lower.contains("turnout") || lower.contains("check")) {
            sb.appendLine()
            sb.appendLine("ATTENDANCE VS REGISTRATION")
            ranked.take(10).forEach { (e, going) ->
                sb.appendLine("- ${e.title}: $going registered")
            }
        }

        return sb.toString().trim()
    }
}

data class AIEventResponse(
    val event: Event,
    val isComplete: Boolean,
    val missingFields: List<String>,
    val conflict: String? = null,
    val source: String = "local"
)

data class AdminAIAnswer(val text: String, val suggestReviewReports: Boolean = false)

data class AiDescriptionResult(val text: String, val fromAi: Boolean)
