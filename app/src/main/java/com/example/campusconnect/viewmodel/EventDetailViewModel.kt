package com.example.campusconnect.viewmodel

import com.example.campusconnect.utils.DateFormats
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.R
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.NotificationDao
import com.example.campusconnect.core.common.TransactionRunner
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.domain.usecase.RSVPUseCases
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.KarmaTransaction
import com.example.campusconnect.models.Notification
import com.example.campusconnect.models.RSVP
import com.example.campusconnect.models.User
import com.example.campusconnect.domain.event.RsvpDecision
import com.example.campusconnect.domain.event.RsvpPolicy
import com.example.campusconnect.domain.event.JoinBlock
import com.example.campusconnect.domain.security.AccessPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.campusconnect.repository.AIRepository
import com.example.campusconnect.utils.AuthorizationManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ONE_HOUR_MILLIS: Long = 3600000L

data class EventDetailUiState(
    val event: Event? = null,
    val attendees: List<User> = emptyList(),
    val rsvpButtonTextRes: Int = R.string.rsvp_join,
    val isRsvpEnabled: Boolean = true,
    val isGoing: Boolean = false,
    val isWaitlisted: Boolean = false,
    val isEditVisible: Boolean = false,
    // Closed until the RSVP check proves otherwise, so chat/polls can't be opened early.
    val hasAccess: Boolean = false,
    val isLoading: Boolean = true,
    val isAiGenerating: Boolean = false,
    val attendeeCount: Int = 0,
    val spotsLeft: Int = 0,
    val isFull: Boolean = false
)

sealed interface EventDetailUiEvent {
    data class ShowToastRes(val messageRes: Int, val formatArgs: String? = null) : EventDetailUiEvent
    data class ShowAiDialog(val suggestedDescription: String) : EventDetailUiEvent
    object Close : EventDetailUiEvent
}

class EventDetailViewModel(
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val userDao: UserDao,
    private val notificationDao: NotificationDao,
    private val eventId: Int,
    private val currentUserId: Int,
    transactions: TransactionRunner = TransactionRunner.None,
    private val aiRepository: AIRepository = AIRepository(eventDao = eventDao, userDao = userDao, rsvpDao = rsvpDao)
) : ViewModel() {

    private val rsvpUseCases = RSVPUseCases(rsvpDao, eventDao, notificationDao, transactions)

    private val _uiState = MutableStateFlow(EventDetailUiState())
    val uiState: StateFlow<EventDetailUiState> = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<EventDetailUiEvent>()
    // Serialises join taps so a double tap can't read the seat count twice.
    private val rsvpMutex = Mutex()
    val uiEvent: SharedFlow<EventDetailUiEvent> = _uiEvent.asSharedFlow()

    init {
        loadEventData()
    }

    fun loadEventData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // 1. Fetch Event
            val event = eventDao.getEventById(eventId)
            if (event == null) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.event_not_found))
                _uiEvent.emit(EventDetailUiEvent.Close)
                return@launch
            }

            val currentUser = userDao.getUserById(currentUserId)
            val isCreator = (event.creator_id == currentUserId) || AccessPolicy.canAccessAdmin(currentUser?.role)

            _uiState.value = _uiState.value.copy(
                event = event,
                isEditVisible = isCreator
            )

            // 2. RSVP & Access Control checks
            updateRsvpAndAccessState(event)

            // 3. Observe Attendees Flow
            observeAttendees(event)
        }
    }

    private fun joinBlockFor(event: Event): JoinBlock? = RsvpPolicy.joinBlock(
        status = event.status,
        eventStartMillis = event.date_timestamp,
        registrationDeadline = event.registration_deadline,
        isRecurring = event.date.startsWith("Every", ignoreCase = true),
        nowMillis = System.currentTimeMillis()
    )

    private fun blockTextRes(block: JoinBlock): Int = when (block) {
        JoinBlock.CANCELLED -> R.string.rsvp_blocked_cancelled
        JoinBlock.ENDED -> R.string.rsvp_blocked_ended
        JoinBlock.DEADLINE_PASSED -> R.string.rsvp_blocked_deadline
    }

    private suspend fun updateRsvpAndAccessState(event: Event) {
        val rsvp = rsvpDao.getUserRSVPForEvent(currentUserId, eventId)
        val attendeeCount = rsvpDao.getAttendeeCount(eventId)
        val isFull = RsvpPolicy.isFull(attendeeCount, event.max_attendees)
        val hasAccess = AuthorizationManager.canAccessRestrictedContent(currentUserId, event, rsvpDao) ||
            _uiState.value.isEditVisible
        val block = joinBlockFor(event)

        _uiState.value = when {
            rsvp?.status == "going" -> _uiState.value.copy(
                rsvpButtonTextRes = R.string.leave_event,
                isRsvpEnabled = true, isGoing = true, isWaitlisted = false
            )
            rsvp?.status == "waitlisted" -> _uiState.value.copy(
                rsvpButtonTextRes = R.string.leave_waitlist,
                isRsvpEnabled = true, isGoing = false, isWaitlisted = true
            )
            block != null -> _uiState.value.copy(
                rsvpButtonTextRes = blockTextRes(block),
                isRsvpEnabled = false, isGoing = false, isWaitlisted = false
            )
            else -> _uiState.value.copy(
                rsvpButtonTextRes = if (isFull) R.string.join_waitlist else R.string.rsvp_join,
                isRsvpEnabled = true, isGoing = false, isWaitlisted = false
            )
        }.copy(hasAccess = hasAccess, isLoading = false)
    }

    private var attendeesJob: Job? = null

    // Restarted on every reload so the seat count uses the current capacity, not the one from first load.
    private fun observeAttendees(event: Event) {
        attendeesJob?.cancel()
        attendeesJob = viewModelScope.launch {
            rsvpDao.getAttendeesForEvent(eventId).collectLatest { users ->
                val count = users.size
                val maxAttendees = event.max_attendees ?: 0
                val left = (maxAttendees - count).coerceAtLeast(0)

                _uiState.value = _uiState.value.copy(
                    attendees = users,
                    attendeeCount = count,
                    spotsLeft = left,
                    isFull = maxAttendees > 0 && count >= maxAttendees
                )
            }
        }
    }

    fun handleRSVP() {
        if (currentUserId <= 0) return
        viewModelScope.launch {
            rsvpMutex.withLock { joinLocked() }
        }
    }

    private suspend fun joinLocked() {
        run {
            val event = eventDao.getEventById(eventId) ?: return
            if (rsvpDao.getUserRSVPForEvent(currentUserId, eventId) != null) {
                updateRsvpAndAccessState(event)
                return
            }
            joinBlockFor(event)?.let { block ->
                _uiEvent.emit(EventDetailUiEvent.ShowToastRes(blockTextRes(block)))
                updateRsvpAndAccessState(event)
                return
            }
            val status = rsvpUseCases.rsvpOrWaitlist(currentUserId, eventId)
            _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.rsvp_successful, status))
            updateRsvpAndAccessState(event)
        }
    }


    fun cancelRsvp() {
        viewModelScope.launch {
            val event = _uiState.value.event ?: eventDao.getEventById(eventId) ?: return@launch
            rsvpMutex.withLock { rsvpUseCases.cancelRsvpAndPromoteWaitlist(currentUserId, eventId) }
            updateRsvpAndAccessState(event)
        }
    }

    fun handleVouch() {
        viewModelScope.launch {
            val event = _uiState.value.event ?: eventDao.getEventById(eventId) ?: return@launch
            val toUserId = event.creator_id

            if (toUserId == null || toUserId == currentUserId) {
                _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.cannot_vouch))
                return@launch
            }

            val existing = userDao.getKarmaTransaction(currentUserId, toUserId, eventId)
            if (existing != null) {
                _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.already_vouched))
                return@launch
            }

            // Perform karma transaction
            userDao.insertKarmaTransaction(
                KarmaTransaction(
                    from_user_id = currentUserId,
                    to_user_id = toUserId,
                    event_id = eventId,
                    points = 1,
                    given_at = DateFormats.storedDay()
                )
            )
            userDao.addKarma(toUserId, 1)

            // Notify host
            notificationDao.insertNotification(
                Notification(
                    user_id = toUserId,
                    title = "You earned Karma! ✨",
                    message = "Someone vouched for you in '${event.title}'",
                    timestamp = System.currentTimeMillis(),
                    type = "karma",
                    event_id = eventId
                )
            )

            _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.karma_shared))
        }
    }

    /**
     * Organizer "Enhance with AI" event description rewrite.
     *
     * Goes through AIRepository -> AIConfig, the same shared-key pipeline used by the
     * student assistant and the admin analytics assistant. Whatever Gemini key is
     * currently active app-wide (set once by an admin in Admin > AI Configuration, or
     * baked in at build time) is what gets used here - there is no separate key for
     * this screen. If no key is configured anywhere, a clearly-labelled local rewrite
     * is shown instead of failing silently.
     */
    fun generateAiEventDescription() {
        viewModelScope.launch {
            val currentEvent = _uiState.value.event ?: return@launch

            _uiState.value = _uiState.value.copy(
                isAiGenerating = true,
                isLoading = true
            )

            val result = aiRepository.enhanceEventDescription(currentEvent.title, currentEvent.description)

            _uiState.value = _uiState.value.copy(
                isAiGenerating = false,
                isLoading = false
            )

            if (!result.fromAi) {
                _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.ai_offline_rewrite_notice))
            }
            _uiEvent.emit(EventDetailUiEvent.ShowAiDialog(result.text))
        }
    }

    fun updateEventDescription(newDescription: String) {
        viewModelScope.launch {
            val currentEvent = _uiState.value.event ?: return@launch
            if (!_uiState.value.isEditVisible) return@launch
            val updatedEvent = currentEvent.copy(description = newDescription)

            eventDao.insertEvent(updatedEvent)
            _uiState.value = _uiState.value.copy(event = updatedEvent)
            _uiEvent.emit(EventDetailUiEvent.ShowToastRes(R.string.description_updated))
        }
    }
}

class EventDetailViewModelFactory(
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val userDao: UserDao,
    private val notificationDao: NotificationDao,
    private val eventId: Int,
    private val currentUserId: Int,
    private val transactions: TransactionRunner = TransactionRunner.None
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventDetailViewModel::class.java)) {
            return EventDetailViewModel(
                eventDao = eventDao,
                rsvpDao = rsvpDao,
                userDao = userDao,
                notificationDao = notificationDao,
                eventId = eventId,
                currentUserId = currentUserId,
                transactions = transactions
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
