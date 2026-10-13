package com.example.campusconnect.data.remote

interface AIDataSource {
    suspend fun extractEventDraft(
        conversation: List<Pair<Boolean, String>>,
        pendingField: String?
    ): EventDraftExtraction

    suspend fun answerWithFacts(systemPrompt: String, userQuery: String, facts: String): String?
}

data class EventDraftExtraction(
    val title: String? = null,
    val dateDisplay: String? = null,
    val dateTimestamp: Long = 0L,
    val dateFound: Boolean = false,
    val time: String? = null,
    val location: String? = null,
    val capacity: Int? = null,
    val eligibility: String? = null,
    val category: String? = null,
    val tags: String? = null,
    val description: String? = null,
    val source: String = "local"
)
