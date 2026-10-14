package com.example.campusconnect.activities.admin

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.activities.AIAssistantActivity
import com.example.campusconnect.core.common.StatCardStyle
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAdminAnalyticsBinding
import com.example.campusconnect.databinding.ItemCategoryBarBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.viewmodel.admin.AdminDashboardUiState
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModel
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModelFactory
import kotlinx.coroutines.launch
import java.util.Locale

class AdminAnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminAnalyticsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivityAdminAnalyticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.analytics)
        if (!AccessPolicy.canAccessAdmin(SessionManager.getInstance(this).getCurrentUserRole())) {
            Toast.makeText(this, R.string.admin_access_required, Toast.LENGTH_SHORT).show(); finish(); return
        }

        binding.aiAssistantFab.setOnClickListener {
            startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_ADMIN)
            })
        }

        val db = AppDatabase.getDatabase(this)
        val vm = ViewModelProvider(
            this,
            AdminDashboardViewModelFactory(db.userDao(), db.eventDao(), db.rsvpDao(), db.attendanceDao(), db.reportDao())
        )[AdminDashboardViewModel::class.java]
        lifecycleScope.launch {
            vm.uiState.collect { render(it) }
        }
    }

    private fun render(state: AdminDashboardUiState) {
        StatCardStyle.apply(this, binding.statUsers, state.userCount.toString(), getString(R.string.users), R.color.qa_purple, R.color.qa_purple_bg)
        StatCardStyle.apply(this, binding.statEvents, state.eventCount.toString(), getString(R.string.events), R.color.qa_orange, R.color.qa_orange_bg)
        StatCardStyle.apply(this, binding.statRsvps, state.rsvpCount.toString(), getString(R.string.rsvps), R.color.qa_blue, R.color.qa_blue_bg)
        StatCardStyle.apply(this, binding.statAttendance, "${state.attendancePercent}%", getString(R.string.attendance_percent), R.color.qa_green, R.color.qa_green_bg)
        val avg = if (state.eventCount > 0) state.rsvpCount.toFloat() / state.eventCount else 0f
        StatCardStyle.apply(this, binding.statAvgRsvps, String.format(Locale.US, "%.1f", avg), getString(R.string.analytics_avg_rsvps), R.color.qa_teal, R.color.qa_teal_bg)
        StatCardStyle.apply(this, binding.statReports, state.pendingReports.toString(), getString(R.string.analytics_pending_reports), R.color.qa_pink, R.color.qa_pink_bg)

        val container = binding.categoriesContainer
        container.removeAllViews()
        if (state.popularCategories.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.no_events_yet)
                setTextColor(getColor(R.color.text_secondary))
            })
            return
        }
        val total = state.popularCategories.sumOf { it.count }.coerceAtLeast(1)
        val inflater = LayoutInflater.from(this)
        for (stat in state.popularCategories) {
            val row = ItemCategoryBarBinding.inflate(inflater, container, false)
            val percent = stat.count * 100 / total
            row.categoryName.text = stat.category
            row.categoryCount.text = getString(R.string.analytics_category_format, stat.count, percent)
            row.categoryBar.setProgressCompat(percent, false)
            container.addView(row.root)
        }
    }

    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
