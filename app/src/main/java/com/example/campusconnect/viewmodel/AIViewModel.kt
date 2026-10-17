package com.example.campusconnect.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.database.AIInteractionDao
import com.example.campusconnect.domain.event.EventTime
import com.example.campusconnect.domain.usecase.EventForm
import com.example.campusconnect.domain.usecase.SaveEventError
import com.example.campusconnect.domain.usecase.SaveEventResult
import com.example.campusconnect.domain.usecase.SaveEventUseCase
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

private const val MAX_CONTEXT_TURNS = 6

enum class AIMode { STUDENT, CREATE, ADMIN }

class AIViewModel(
    private val aiRepository: AIRepository,
    private val eventRepository: EventRepository,
    private val interactionDao: AIInteractionDao,
    private val userId: Int,
    private val mode: AIMode = AIMode.CREATE,
    private val saveEvent: SaveEventUseCase? = null
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
            interactionDao.getHistory(userId, mode.name).collectLatest { _chatHistory.value = it }
        }
    }

    fun processPrompt(prompt: String) {
        viewModelScope.launch {
            interactionDao.insert(
                AIInteraction(user_id = userId, prompt = prompt, response = "", timestamp = System.currentTimeMillis(), is_user_message = true, mode = mode.name)
            )
            _uiState.value = AIUiState.Loading
            when (mode) {
                AIMode.CREATE -> handleCreate(prompt)
                AIMode.STUDENT -> {
                    val answer = runCatching { aiRepository.answerStudentQuery(prompt, userId, recentConversation(prompt)) }
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

    /** Last few turns before this prompt, so follow-ups like "what about tomorrow?" make sense. */
    private fun recentConversation(currentPrompt: String): List<Pair<Boolean, String>> {
        val turns = _chatHistory.value.map { it.is_user_message to if (it.is_user_message) it.prompt else it.response }
            .filter { it.second.isNotBlank() }
        val withoutCurrent = if (turns.lastOrNull() == (true to currentPrompt)) turns.dropLast(1) else turns
        return withoutCurrent.takeLast(MAX_CONTEXT_TURNS)
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
        val merged = if (current == null) newEvent else current.copy(
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
        // Date and time often arrive in different messages; reminders and "upcoming"
        // filters need the real start, so fold the time into the timestamp every merge.
        draftEvent = merged.copy(date_timestamp = EventTime.combine(merged.date_timestamp, merged.time))
    }

    fun confirmEvent(ignoreConflict: Boolean = false) {
        val event = draftEvent ?: return
        val conflict = lastConflict
        if (!ignoreConflict && !conflict.isNullOrBlank()) {
            _uiState.value = AIUiState.Conflict(conflict, event)
            return
        }
        val useCase = saveEvent ?: return
        viewModelScope.launch {
            _uiState.value = AIUiState.Loading
            // Same path as the manual form: role re-checked in the database, no past dates, valid
            // capacity. The draft came from model output, so it gets no shortcut around the rules.
            val form = EventForm(
                title = event.title,
                description = event.description.ifBlank { event.title },
                location = event.location,
                maxAttendeesText = event.max_attendees?.toString().orEmpty(),
                type = event.type,
                isFlash = event.is_flash,
                dateTimestamp = event.date_timestamp,
                imageUri = null,
                category = event.category,
                tags = event.tags,
                eligibility = event.eligibility
            )
            when (val result = useCase.save(userId, null, form)) {
                is SaveEventResult.Saved -> {
                    interactionDao.clearHistory(userId, mode.name)
                    _uiState.value = AIUiState.Success("Event '${result.event.title}' created!", result.event.id)
                    draftEvent = null
                    pendingMissingField = null
                    lastConflict = null
                }
                is SaveEventResult.Invalid -> _uiState.value = AIUiState.Error(saveErrorMessage(result.error))
            }
        }
    }

    private fun saveErrorMessage(error: SaveEventError) = when (error) {
        SaveEventError.REQUIRED_FIELDS -> "The event still needs a title and a location. Tell me where it happens."
        SaveEventError.INVALID_CAPACITY -> "The capacity must be a positive number."
        SaveEventError.PAST_DATE -> "That date is in the past. Give me a date from today onwards."
        SaveEventError.NOT_ALLOWED -> "Your account can no longer create events."
        SaveEventError.CAPACITY_BELOW_ATTENDEES, SaveEventError.NOT_FOUND -> "The event could not be saved."
    }

    fun clearConflictAndContinue() {
        lastConflict = null
        confirmEvent(ignoreConflict = true)
    }

    fun resetState() { _uiState.value = AIUiState.Idle }

    private suspend fun reply(text: String) {
        interactionDao.insert(
            AIInteraction(user_id = userId, prompt = "", response = text, timestamp = System.currentTimeMillis(), is_user_message = false, mode = mode.name)
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
