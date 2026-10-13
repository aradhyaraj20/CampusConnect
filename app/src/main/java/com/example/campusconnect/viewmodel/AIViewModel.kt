package com.example.campusconnect.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.database.AIInteractionDao
import com.example.campusconnect.models.AIInteraction
import com.example.campusconnect.models.Event
import com.example.campusconnect.repository.AIEventResponse
import com.example.campusconnect.repository.AIRepository
import com.example.campusconnect.repository.AdminAIAnswer
import com.example.campusconnect.repository.EventRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class AIMode { STUDENT, CREATE, ADMIN }

class AIViewModel(
    private val aiRepository: AIRepository,
    private val eventRepository: EventRepository,
    private val interactionDao: AIInteractionDao,
    private val userId: Int,
    private val mode: AIMode = AIMode.CREATE
) : ViewModel() {

    private val _uiState = MutableStateFlow<AIUiState>(AIUiState.Idle)
    val uiState: StateFlow<AIUiState> = _uiState.asStateFlow()
    private val _chatHistory = MutableStateFlow<List<AIInteraction>>(emptyList())
    val chatHistory: StateFlow<List<AIInteraction>> = _chatHistory.asStateFlow()

    private var draftEvent: Event? = null
    private var pendingMissingField: String? = null
    private var lastConflict: String? = null

    init {
        viewModelScope.launch {
            interactionDao.getHistory(userId).collectLatest { _chatHistory.value = it }
        }
    }

    fun processPrompt(prompt: String) {
        viewModelScope.launch {
            interactionDao.insert(
                AIInteraction(user_id = userId, prompt = prompt, response = "", timestamp = System.currentTimeMillis(), is_user_message = true)
            )
            _uiState.value = AIUiState.Loading
            when (mode) {
                AIMode.CREATE -> handleCreate(prompt)
                AIMode.STUDENT -> {
                    val answer = runCatching { aiRepository.answerStudentQuery(prompt, userId) }
                        .getOrElse { "AI is temporarily unavailable. You can continue using regular search." }
                    reply(answer)
                    _uiState.value = AIUiState.Idle
                }
                AIMode.ADMIN -> {
                    val answer = runCatching { aiRepository.answerAdminQuery(prompt) }
                        .getOrElse { AdminAIAnswer("I could not analyse campus data right now.") }
                    reply(answer.text)
                    _uiState.value = if (answer.suggestReviewReports) AIUiState.AdminAction("Review Reports") else AIUiState.Idle
                }
            }
        }
    }

    private suspend fun handleCreate(prompt: String) {
        val conversation = _chatHistory.value.map { it.is_user_message to if (it.is_user_message) it.prompt else it.response }
        val response = aiRepository.parseEventRequest(prompt, pendingMissingField, conversation)
        mergeDraft(response.event)
        lastConflict = response.conflict
        val missing = mutableListOf<String>()
        if (draftEvent?.title.isNullOrBlank()) missing.add("title")
        if (draftEvent?.date.isNullOrBlank() || (draftEvent?.date_timestamp ?: 0L) == 0L) missing.add("date")
        if (draftEvent?.time.isNullOrBlank()) missing.add("time")
        if (draftEvent?.location.isNullOrBlank()) missing.add("location")
        val isComplete = missing.isEmpty()
        pendingMissingField = if (isComplete) null else missing.first()

        val replyText = aiRepository.craftCreateEventReply(
            userMessage = prompt,
            draftSummary = draftSummary(),
            nextMissingField = pendingMissingField,
            isComplete = isComplete
        ) + if (isComplete) response.conflict?.let { "\n\n$it" }.orEmpty() else ""
        reply(replyText)

        val readyDraft = draftEvent
        _uiState.value = if (isComplete && readyDraft != null) {
            AIUiState.Preview(response.copy(event = readyDraft, isComplete = true, conflict = lastConflict))
        } else {
            AIUiState.Idle
        }
    }

    private fun draftSummary(): String {
        val d = draftEvent ?: return "(nothing captured yet)"
        return listOfNotNull(
            d.title.takeIf { it.isNotBlank() }?.let { "title=$it" },
            d.date.takeIf { it.isNotBlank() }?.let { "date=$it" },
            d.time.takeIf { it.isNotBlank() }?.let { "time=$it" },
            d.location.takeIf { it.isNotBlank() }?.let { "location=$it" },
            d.max_attendees?.let { "capacity=$it" }
        ).ifEmpty { listOf("(nothing captured yet)") }.joinToString(", ")
    }

    private fun mergeDraft(newEvent: Event) {
        val current = draftEvent
        draftEvent = if (current == null) newEvent else current.copy(
            title = newEvent.title.ifBlank { current.title },
            date = newEvent.date.ifBlank { current.date },
            date_timestamp = if (newEvent.date_timestamp > 0) newEvent.date_timestamp else current.date_timestamp,
            time = newEvent.time.ifBlank { current.time },
            location = newEvent.location.ifBlank { current.location },
            max_attendees = newEvent.max_attendees ?: current.max_attendees,
            eligibility = newEvent.eligibility ?: current.eligibility,
            category = if (newEvent.category != "General") newEvent.category else current.category,
            tags = newEvent.tags ?: current.tags,
            description = if (newEvent.description.isNotBlank()) newEvent.description else current.description
        )
    }

    fun confirmEvent(ignoreConflict: Boolean = false) {
        val event = draftEvent ?: return
        val conflict = lastConflict
        if (!ignoreConflict && !conflict.isNullOrBlank()) {
            _uiState.value = AIUiState.Conflict(conflict, event)
            return
        }
        viewModelScope.launch {
            _uiState.value = AIUiState.Loading
            val id = eventRepository.insertEvent(event.copy(creator_id = userId, status = "active"))
            interactionDao.clearHistory(userId)
            _uiState.value = AIUiState.Success("Event '${event.title}' created!", id.toInt())
            draftEvent = null
            pendingMissingField = null
            lastConflict = null
        }
    }

    fun clearConflictAndContinue() {
        lastConflict = null
        confirmEvent(ignoreConflict = true)
    }

    fun resetState() { _uiState.value = AIUiState.Idle }

    private suspend fun reply(text: String) {
        interactionDao.insert(
            AIInteraction(user_id = userId, prompt = "", response = text, timestamp = System.currentTimeMillis(), is_user_message = false)
        )
    }
}

sealed class AIUiState {
    object Idle : AIUiState()
    object Loading : AIUiState()
    data class Preview(val response: AIEventResponse) : AIUiState()
    data class Conflict(val message: String, val event: Event) : AIUiState()
    data class Success(val message: String, val eventId: Int) : AIUiState()
    data class Error(val error: String) : AIUiState()
    data class AdminAction(val action: String) : AIUiState()
}
