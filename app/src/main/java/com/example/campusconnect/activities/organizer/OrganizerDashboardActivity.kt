package com.example.campusconnect.activities.organizer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.activities.AIAssistantActivity
import com.example.campusconnect.activities.CreateEventActivity
import com.example.campusconnect.activities.EditProfileActivity
import com.example.campusconnect.activities.EventDetailActivity
import com.example.campusconnect.activities.NotificationsActivity
import com.example.campusconnect.adapters.EventAdapter
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityOrganizerDashboardBinding
import com.example.campusconnect.databinding.ItemStatCardBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Event
import com.example.campusconnect.viewmodel.organizer.OrganizerDashboardUiState
import com.example.campusconnect.viewmodel.organizer.OrganizerDashboardViewModel
import com.example.campusconnect.viewmodel.organizer.OrganizerDashboardViewModelFactory
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class OrganizerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrganizerDashboardBinding
    private lateinit var viewModel: OrganizerDashboardViewModel
    private lateinit var eventAdapter: EventAdapter
    private var currentTab = 0 // 0 = upcoming, 1 = ongoing, 2 = completed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessOrganizerDashboard)
        binding = ActivityOrganizerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val db = AppDatabase.getDatabase(this)
        val sessionManager = SessionManager.getInstance(this)
        val organizerId = sessionManager.getCurrentUserId()

        // Guard the destination itself, not just the entry button - hiding a button
        // is not sufficient security (spec section 3).
        if (!AccessPolicy.canAccessOrganizerDashboard(sessionManager.getCurrentUserRole())) {
            Toast.makeText(this, "Organizer access required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            OrganizerDashboardViewModelFactory(
                db.eventDao(), db.rsvpDao(), db.attendanceDao(), db.userDao(), organizerId
            )
        )[OrganizerDashboardViewModel::class.java]

        eventAdapter = EventAdapter(emptyList()) { event ->
            startActivity(Intent(this, EventDetailActivity::class.java).apply {
                putExtra("EVENT_ID", event.id)
            })
        }
        binding.eventsRecyclerView.adapter = eventAdapter
        binding.eventsRecyclerView.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)

        binding.createEventWithAiButton.setOnClickListener {
            startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_CREATE)
            })
        }
        binding.notificationButton.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        binding.eventsTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                renderCurrentTab()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        setupBottomNav()
        observeState()
    }

    private var latestState: OrganizerDashboardUiState = OrganizerDashboardUiState()

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                latestState = state
                binding.welcomeText.text = getString(com.example.campusconnect.R.string.welcome_format, state.organizerName)

                bindStat(binding.statEventsCreated, state.eventsCreated.toString(), getString(com.example.campusconnect.R.string.events_created))
                bindStat(binding.statRegistrations, state.registrations.toString(), getString(com.example.campusconnect.R.string.registrations))
                bindStat(binding.statAttendance, state.attendanceCount.toString(), getString(com.example.campusconnect.R.string.attendance))
                bindStat(binding.statAttendancePercent, "${state.attendancePercent}%", getString(com.example.campusconnect.R.string.attendance_percent))

                renderCurrentTab()
            }
        }
    }

    private fun bindStat(cardBinding: ItemStatCardBinding, value: String, label: String) {
        cardBinding.statValue.text = value
        cardBinding.statLabel.text = label
    }

    private fun renderCurrentTab() {
        val events: List<Event> = when (currentTab) {
            0 -> latestState.upcomingEvents
            1 -> latestState.ongoingEvents
            else -> latestState.completedEvents
        }
        eventAdapter.updateEvents(events)
        binding.emptyEventsState.visibility =
            if (!latestState.isLoading && events.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    /**
     * The bottom-nav tabs used to all show the same page (Dashboard / My Events /
     * Analytics only scrolled to a header, so they looked identical). Each tab now
     * shows a genuinely different view by toggling which sections are visible:
     *   Dashboard  -> events list + analytics (full overview)
     *   My Events  -> events list only
     *   Analytics  -> analytics cards only
     */
    private fun showSections(showEvents: Boolean, showAnalytics: Boolean) {
        binding.eventsSection.visibility =
            if (showEvents) android.view.View.VISIBLE else android.view.View.GONE
        binding.analyticsSection.visibility =
            if (showAnalytics) android.view.View.VISIBLE else android.view.View.GONE
        // "Create Event with AI" belongs with event management, not the pure
        // Analytics view - hiding it there keeps the three tabs visually distinct.
        binding.createEventWithAiButton.visibility =
            if (showEvents) android.view.View.VISIBLE else android.view.View.GONE
        binding.dashboardScrollView.smoothScrollTo(0, 0)
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = com.example.campusconnect.R.id.nav_dashboard
        supportActionBar?.title =
            binding.bottomNav.menu.findItem(com.example.campusconnect.R.id.nav_dashboard)?.title
        showSections(showEvents = true, showAnalytics = true)
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                com.example.campusconnect.R.id.nav_dashboard -> {
                    supportActionBar?.title = item.title
                    showSections(showEvents = true, showAnalytics = true)
                    true
                }
                com.example.campusconnect.R.id.nav_my_events -> {
                    supportActionBar?.title = item.title
                    showSections(showEvents = true, showAnalytics = false)
                    true
                }
                com.example.campusconnect.R.id.nav_create -> {
                    startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                        putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_CREATE)
                    })
                    false
                }
                com.example.campusconnect.R.id.nav_analytics -> {
                    supportActionBar?.title = item.title
                    showSections(showEvents = false, showAnalytics = true)
                    true
                }
                com.example.campusconnect.R.id.nav_profile -> {
                    startActivity(Intent(this, com.example.campusconnect.activities.MyProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(com.example.campusconnect.R.menu.toolbar_account_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return com.example.campusconnect.core.common.AccountMenuHelper
            .handleMenuItem(this, item.itemId) || super.onOptionsItemSelected(item)
    }
}
