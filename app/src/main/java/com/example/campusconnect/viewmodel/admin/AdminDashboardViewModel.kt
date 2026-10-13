package com.example.campusconnect.viewmodel.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.campusconnect.database.AttendanceDao
import com.example.campusconnect.database.EventDao
import com.example.campusconnect.database.ReportDao
import com.example.campusconnect.database.RsvpDao
import com.example.campusconnect.database.UserDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryStat(val category: String, val count: Int)

data class AdminDashboardUiState(
    val isLoading: Boolean = true,
    val userCount: Int = 0,
    val eventCount: Int = 0,
    val rsvpCount: Int = 0,
    val attendancePercent: Int = 0,
    val pendingReports: Int = 0,
    val popularCategories: List<CategoryStat> = emptyList()
)

class AdminDashboardViewModel(
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val attendanceDao: AttendanceDao,
    private val reportDao: ReportDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminDashboardUiState())
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            val userCount = userDao.getUserCount()
            val eventCount = eventDao.getEventCount()
            val rsvpCount = rsvpDao.getTotalRsvpCount()
            val attendanceCount = attendanceDao.getTotalAttendanceCount()
            val pendingReports = reportDao.getPendingReportCount()
            val categories = eventDao.getCategoryCounts().map { CategoryStat(it.category, it.count) }

            val attendancePercent = if (rsvpCount > 0) (attendanceCount * 100) / rsvpCount else 0

            _uiState.value = AdminDashboardUiState(
                isLoading = false,
                userCount = userCount,
                eventCount = eventCount,
                rsvpCount = rsvpCount,
                attendancePercent = attendancePercent,
                pendingReports = pendingReports,
                popularCategories = categories
            )
        }
    }
}

class AdminDashboardViewModelFactory(
    private val userDao: UserDao,
    private val eventDao: EventDao,
    private val rsvpDao: RsvpDao,
    private val attendanceDao: AttendanceDao,
    private val reportDao: ReportDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdminDashboardViewModel::class.java)) {
            return AdminDashboardViewModel(userDao, eventDao, rsvpDao, attendanceDao, reportDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
