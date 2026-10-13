package com.example.campusconnect.activities.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.ReportAdapter
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivitySimpleListBinding
import com.example.campusconnect.domain.usecase.ModerateReportUseCase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AdminModerationActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySimpleListBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySimpleListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.moderation)
        if (SessionManager.getInstance(this).getCurrentUserRole() != "ADMIN") {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show(); finish(); return
        }
        val db = AppDatabase.getDatabase(this)
        val useCase = ModerateReportUseCase(db.reportDao(), db.userDao(), db.eventDao())
        val adapter = ReportAdapter(emptyList(),
            onDismiss = { report -> lifecycleScope.launch { useCase.dismiss(report) } },
            onRemove = { report -> lifecycleScope.launch { useCase.removeContent(report); Toast.makeText(this@AdminModerationActivity, "Content action applied", Toast.LENGTH_SHORT).show() } },
            onResolve = { report -> lifecycleScope.launch { useCase.resolve(report) } }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        lifecycleScope.launch {
            db.reportDao().getAllReports().collectLatest { reports ->
                adapter.submit(reports)
                binding.emptyState.visibility = if (reports.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    override fun onSupportNavigateUp(): Boolean { onBackPressedDispatcher.onBackPressed(); return true }
}
