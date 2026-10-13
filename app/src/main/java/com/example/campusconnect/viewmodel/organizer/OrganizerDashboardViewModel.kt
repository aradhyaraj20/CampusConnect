package com.example.campusconnect.viewmodel.organizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.database.AttendanceDao
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import com.example.campusconnect.domain.model.EventStatus
import com.example.campusconnect.models.Event
import com.example.campusconnect.models.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class OrganizerDashboardUiState(
    val organizerName: String = "",
    val isLoading: Boolean = true,
    val upcomingEvents: List<Event> = emptyList(),
    val ongoingEvents: List<Event> = emptyList(),
    val completedEvents: List<Event> = emptyList(),
    val eventsCreated: Int = 0,
    val registrations: Int = 0,
    val attendanceCount: Int = 0,
    val attendancePercent: Int = 0
)

class OrganizerDashboardViewModel(
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val attendanceDao: AttendanceDao,
    private val userDao: UserDao,
    private val organizerId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizerDashboardUiState())
    val uiState: StateFlow<OrganizerDashboardUiState> = _uiState.asStateFlow()

    init {
        loadOrganizerName()
        observeEvents()
    }

    private fun loadOrganizerName() {
        viewModelScope.launch {
            val user: User? = userDao.getUserById(organizerId)
            _uiState.value = _uiState.value.copy(organizerName = user?.name ?: "Organizer")
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            eventDao.getEventsByCreator(organizerId).collectLatest { events ->
                val upcoming = events.filter {
                    it.status.equals("active", ignoreCase = true) &&
                        !it.status.equals(EventStatus.ONGOING.name, ignoreCase = true)
                }
                val ongoing = events.filter { it.status.equals(EventStatus.ONGOING.name, ignoreCase = true) }
                val completed = events.filter {
                    it.status.equals("completed", ignoreCase = true) ||
                        it.status.equals(EventStatus.COMPLETED.name, ignoreCase = true)
                }

                _uiState.value = _uiState.value.copy(
                    upcomingEvents = upcoming,
                    ongoingEvents = ongoing,
                    completedEvents = completed,
                    eventsCreated = events.size,
                    isLoading = false
                )

                refreshAnalytics()
            }
        }
    }

    private fun refreshAnalytics() {
        viewModelScope.launch {
            val registrations = rsvpDao.getRegistrationCountForOrganizer(organizerId)
            val attendanceCount = attendanceDao.getAttendanceCountForOrganizer(organizerId)
            val percent = if (registrations > 0) (attendanceCount * 100) / registrations else 0

            _uiState.value = _uiState.value.copy(
                registrations = registrations,
                attendanceCount = attendanceCount,
                attendancePercent = percent
            )
        }
    }
}

class OrganizerDashboardViewModelFactory(
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val attendanceDao: AttendanceDao,
    private val userDao: UserDao,
    private val organizerId: Int
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OrganizerDashboardViewModel::class.java)) {
            return OrganizerDashboardViewModel(eventDao, rsvpDao, attendanceDao, userDao, organizerId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
