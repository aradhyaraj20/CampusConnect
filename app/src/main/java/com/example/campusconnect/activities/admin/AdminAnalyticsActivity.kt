package com.example.campusconnect.activities.admin

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.activities.AIAssistantActivity
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySimpleListBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModel
import com.example.campusconnect.viewmodel.admin.AdminDashboardViewModelFactory
import kotlinx.coroutines.launch

class AdminAnalyticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.analytics)
        if (!AccessPolicy.canAccessAdmin(SessionManager.getInstance(this).getCurrentUserRole())) {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show(); finish(); return
        }
        binding.aiAssistButton.visibility = View.VISIBLE
        binding.aiAssistButton.setOnClickListener {
            startActivity(Intent(this, AIAssistantActivity::class.java).apply {
                putExtra(AIAssistantActivity.EXTRA_MODE, AIAssistantActivity.MODE_ADMIN)
            })
        }
        val db = AppDatabase.getDatabase(this)
        val vm = ViewModelProvider(this, AdminDashboardViewModelFactory(db.userDao(), db.eventDao(), db.rsvpDao(), db.attendanceDao(), db.reportDao()))[AdminDashboardViewModel::class.java]
        lifecycleScope.launch {
            vm.uiState.collect { state ->
                binding.emptyState.visibility = View.VISIBLE
                binding.emptyState.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                binding.emptyState.text = buildString {
                    append("Users: ${state.userCount}\nEvents: ${state.eventCount}\nRSVPs: ${state.rsvpCount}\n")
                    append("Attendance: ${state.attendancePercent}%\nPending reports: ${state.pendingReports}\n\nPopular categories\n")
                    state.popularCategories.forEach { append("• ${it.category}: ${it.count}\n") }
                }
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
