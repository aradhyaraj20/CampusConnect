package com.example.campusconnect.domain.usecase

import com.example.campusconnect.database.EventDao
import com.example.campusconnect.models.Event
import kotlinx.coroutines.flow.first

class AISummaryUseCase(private val eventDao: EventDao) {

    suspend fun generateEventSummary(eventId: Int): String {
        val event = eventDao.getEventById(eventId) ?: return "Event not found."

        return """
            📌 **What**: ${event.title}
            📅 **When**: ${event.date} at ${event.time}
            📍 **Where**: ${event.location}
            👥 **Eligibility**: ${event.eligibility ?: "Open to all students"}
            💡 **Why Attend**: ${event.description}
        """.trimIndent()
    }

    suspend fun answerCampusQuestion(query: String, userId: Int): String {
        val lower = query.lowercase()
        val allEvents = eventDao.getAllEvents().first()

        return when {
            lower.contains("ai") || lower.contains("machine learning") -> {
                val matches = allEvents.filter { it.title.lowercase().contains("ai") || it.category.lowercase().contains("academic") }
                if (matches.isNotEmpty()) {
                    "Found ${matches.size} AI-related sessions: ${matches.take(3).joinToString { it.title }}."
                } else {
                    "No AI sessions currently scheduled for this week."
                }
            }
            lower.contains("placement") || lower.contains("interview") || lower.contains("career") -> {
                "For placements, we recommend attending Career Fair 2026 and Algo Masters Group!"
            }
            lower.contains("free seats") || lower.contains("available") -> {
                val available = allEvents.filter { (it.max_attendees ?: 0) == 0 || (it.max_attendees ?: 0) > 10 }
                "Events with free capacity: ${available.take(3).joinToString { it.title }}."
            }
            else -> {
                "Check out the Home Feed for the latest campus events or ask me to schedule a new workshop!"
            }
        }
    }
}
