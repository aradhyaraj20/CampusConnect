package com.example.campusconnect.activities.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.ReportAdapter
import com.example.campusconnect.appContainer
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySimpleListBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.domain.usecase.ModerateReportUseCase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminModerationActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.moderation)
        if (!AccessPolicy.canAccessAdmin(SessionManager.getInstance(this).getCurrentUserRole())) {
            Toast.makeText(this, R.string.admin_access_required, Toast.LENGTH_SHORT).show(); finish(); return
        }
        val db = AppDatabase.getDatabase(this)
        val useCase = appContainer.moderationUseCase(SessionManager.getInstance(this).getCurrentUserId())
        val adapter = ReportAdapter(emptyList(),
            onDismiss = { report -> lifecycleScope.launch { useCase.dismiss(report).showResult(this@AdminModerationActivity, getString(R.string.report_dismissed)) } },
            onRemove = { report -> lifecycleScope.launch { useCase.removeContent(report).showResult(this@AdminModerationActivity, getString(R.string.content_action_applied)) } },
            onResolve = { report -> lifecycleScope.launch { useCase.resolve(report).showResult(this@AdminModerationActivity, getString(R.string.report_resolved)) } }
        )
        binding.recyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.list_columns))
        binding.recyclerView.adapter = adapter
        val pager = com.example.campusconnect.core.common.Pager(binding.recyclerView) { adapter.submit(it) }
        lifecycleScope.launch {
            db.reportDao().getAllReports().collectLatest { reports ->
                pager.submit(reports)
                binding.emptyState.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
