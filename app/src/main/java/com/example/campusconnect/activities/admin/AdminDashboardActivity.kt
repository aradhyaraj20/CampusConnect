package com.example.campusconnect.activities.admin

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAdminDashboardBinding
import com.example.campusconnect.databinding.ItemStatCardBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModel
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModelFactory
import com.example.campusconnect.viewmodel.admin.CategoryStat
import kotlinx.coroutines.launch

class AdminDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashboardBinding
    private lateinit var viewModel: AdminDashboardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivityAdminDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val db = AppDatabase.getDatabase(this)
        val sessionManager = SessionManager.getInstance(this)

        // Guard the destination itself, not just the entry button - hiding a button
        // is not sufficient security (spec section 3).
        if (!AccessPolicy.canAccessAdmin(sessionManager.getCurrentUserRole())) {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.welcomeText.text = getString(R.string.welcome_format, sessionManager.getCurrentUserName())

        viewModel = ViewModelProvider(
            this,
            AdminDashboardViewModelFactory(
                db.userDao(), db.eventDao(), db.rsvpDao(), db.attendanceDao(), db.reportDao()
            )
        )[AdminDashboardViewModel::class.java]

        binding.reviewReportsButton.setOnClickListener {
            startActivity(Intent(this, AdminModerationActivity::class.java))
        }

        val openAiConfig = {
            startActivity(Intent(this, AdminAiConfigActivity::class.java))
        }
        binding.aiConfigCard.setOnClickListener { openAiConfig() }
        binding.manageAiKeyButton.setOnClickListener { openAiConfig() }

        setupBottomNav()
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                bindStat(binding.statUsers, state.userCount.toString(), getString(R.string.users))
                bindStat(binding.statEvents, state.eventCount.toString(), getString(R.string.events))
                bindStat(binding.statRsvps, state.rsvpCount.toString(), getString(R.string.rsvps))
                bindStat(binding.statAttendancePercent, "${state.attendancePercent}%", getString(R.string.attendance_percent))

                binding.pendingReportsText.text = getString(R.string.reports_format, state.pendingReports)

                renderCategories(state.popularCategories)
            }
        }
    }

    private fun bindStat(cardBinding: ItemStatCardBinding, value: String, label: String) {
        cardBinding.statValue.text = value
        cardBinding.statLabel.text = label
    }

    private fun renderCategories(categories: List<CategoryStat>) {
        binding.categoriesContainer.removeAllViews()
        if (categories.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.no_events_yet)
            empty.setTextColor(getColor(android.R.color.darker_gray))
            binding.categoriesContainer.addView(empty)
            return
        }
        for (stat in categories) {
            val row = TextView(this)
            row.text = "${stat.category} · ${stat.count}"
            row.textSize = 15f
            row.setPadding(0, 12, 0, 12)
            binding.categoriesContainer.addView(row)
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_dashboard
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_users -> {
                    startActivity(Intent(this, AdminUsersActivity::class.java))
                    false
                }
                R.id.nav_moderation -> {
                    startActivity(Intent(this, AdminModerationActivity::class.java))
                    false
                }
                R.id.nav_analytics -> {
                    startActivity(Intent(this, AdminAnalyticsActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, com.example.campusconnect.activities.MyProfileActivity::class.java))
                    false
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        renderAiConfigStatus()
    }

    private fun renderAiConfigStatus() {
        val configured = com.example.campusconnect.core.ai.AIConfig.isConfigured
        binding.aiConfigStatusText.text = getString(
            if (configured) R.string.ai_config_status_ok else R.string.ai_config_status_missing
        )
        binding.aiConfigStatusText.setTextColor(
            getColor(if (configured) R.color.success else R.color.warning)
        )
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
