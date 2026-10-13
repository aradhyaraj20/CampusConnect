package com.example.campusconnect.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.AIInteractionAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityAiAssistantBinding
import com.example.campusconnect.databinding.BottomSheetAiPreviewBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Event
import com.example.campusconnect.repository.AIRepository
import com.example.campusconnect.repository.EventRepository
import com.example.campusconnect.utils.ReminderManager
import com.example.campusconnect.viewmodel.AIMode
import com.example.campusconnect.viewmodel.AIUiState
import com.example.campusconnect.viewmodel.AIViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AIAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private lateinit var viewModel: AIViewModel
    private lateinit var adapter: AIInteractionAdapter
    private var currentUserId: Int = -1
    private var mode: AIMode = AIMode.STUDENT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets()
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mode = when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_CREATE -> AIMode.CREATE
            MODE_ADMIN -> AIMode.ADMIN
            else -> AIMode.STUDENT
        }
        supportActionBar?.title = when (mode) {
            AIMode.CREATE -> "Create Event with AI"
            AIMode.ADMIN -> "Campus Analytics AI"
            AIMode.STUDENT -> "Ask CampusConnect AI"
        }

        val session = SessionManager.getInstance(this)
        currentUserId = session.getCurrentUserId()
        if (mode == AIMode.CREATE && !AccessPolicy.canCreateEvents(session.getCurrentUserRole())) {
            Toast.makeText(this, "Organizer access required", Toast.LENGTH_SHORT).show(); finish(); return
        }
        if (mode == AIMode.ADMIN && !AccessPolicy.canAccessAdmin(session.getCurrentUserRole())) {
            Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show(); finish(); return
        }

        when (mode) {
            AIMode.CREATE -> RoleGuard.enforce(this, AccessPolicy::canCreateEvents)
            AIMode.ADMIN -> RoleGuard.enforce(this, AccessPolicy::canAccessAdmin)
            AIMode.STUDENT -> Unit
        }

        val db = AppDatabase.getDatabase(this)
        val aiRepo = AIRepository(db.eventDao(), db.userDao(), db.rsvpDao(), db.reportDao())
        val eventRepo = EventRepository(db.eventDao(), db.rsvpDao())
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AIViewModel(aiRepo, eventRepo, db.aiInteractionDao(), currentUserId, mode) as T
            }
        })[AIViewModel::class.java]

        adapter = AIInteractionAdapter(emptyList())
        binding.chatRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.chatRecyclerView.adapter = adapter
        binding.sendFab.setOnClickListener {
            val prompt = binding.promptEditText.text.toString()
            if (prompt.isNotEmpty()) {
                viewModel.processPrompt(prompt)
                binding.promptEditText.text?.clear()
            }
        }
        setupEmptyState()
        observeViewModel()
    }

    /** Greeting + tappable example questions shown while the thread is empty. */
    private fun setupEmptyState() {
        val (title, subtitle, suggestions) = when (mode) {
            AIMode.STUDENT -> Triple(
                R.string.ai_empty_title_student, R.string.ai_empty_subtitle_student,
                listOf(R.string.ai_chip_student_week, R.string.ai_chip_student_rsvps,
                    R.string.ai_chip_student_next, R.string.ai_chip_student_coding)
            )
            AIMode.CREATE -> Triple(
                R.string.ai_empty_title_create, R.string.ai_empty_subtitle_create,
                listOf(R.string.ai_chip_create_hackathon, R.string.ai_chip_create_workshop)
            )
            AIMode.ADMIN -> Triple(
                R.string.ai_empty_title_admin, R.string.ai_empty_subtitle_admin,
                listOf(R.string.ai_chip_admin_users, R.string.ai_chip_admin_top, R.string.ai_chip_admin_reports)
            )
        }
        binding.emptyTitle.setText(title)
        binding.emptySubtitle.setText(subtitle)
        suggestions.forEach { res ->
            binding.suggestionChips.addView(Chip(this).apply {
                setText(res)
                setOnClickListener { viewModel.processPrompt(getString(res)) }
            })
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.chatHistory.collectLatest { history ->
                adapter.updateHistory(history)
                binding.emptyState.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
                if (history.isNotEmpty()) binding.chatRecyclerView.smoothScrollToPosition(history.size - 1)
            }
        }
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is AIUiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                    is AIUiState.Preview -> {
                        binding.progressBar.visibility = View.GONE
                        showPreviewBottomSheet(state.response.event, state.response.conflict)
                    }
                    is AIUiState.Conflict -> {
                        binding.progressBar.visibility = View.GONE
                        showConflictDialog(state.message)
                    }
                    is AIUiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@AIAssistantActivity, state.message, Toast.LENGTH_LONG).show()
                        lifecycleScope.launch {
                            val db = AppDatabase.getDatabase(this@AIAssistantActivity)
                            db.eventDao().getEventById(state.eventId)?.let {
                                ReminderManager.scheduleReminders(this@AIAssistantActivity, it)
                            }
                            db.notificationDao().insertNotification(
                                com.example.campusconnect.models.Notification(
                                    user_id = currentUserId,
                                    title = "Event created",
                                    message = state.message,
                                    timestamp = System.currentTimeMillis(),
                                    type = "event_created",
                                    event_id = state.eventId
                                )
                            )
                        }
                        finish()
                    }
                    is AIUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@AIAssistantActivity, state.error, Toast.LENGTH_SHORT).show()
                    }
                    else -> binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun showPreviewBottomSheet(event: Event, conflict: String?) {
        val dialog = BottomSheetDialog(this)
        val sheetBinding = BottomSheetAiPreviewBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        sheetBinding.previewTitle.text = event.title.ifBlank { "Untitled event" }
        sheetBinding.previewDateTime.text = "${event.date} · ${event.time}"
        sheetBinding.previewLocation.text = event.location
        sheetBinding.previewCapacity.text = "Capacity: ${event.max_attendees ?: "Open"}"
        sheetBinding.previewEligibility.text = "Eligibility: ${event.eligibility ?: "Open to all"}"
        sheetBinding.previewTags.text = "Tags: ${event.tags ?: "—"}"
        if (conflict != null) {
            sheetBinding.previewLocation.setTextColor(getColor(android.R.color.holo_red_dark))
            Toast.makeText(this, conflict, Toast.LENGTH_LONG).show()
        }
        sheetBinding.confirmButton.setOnClickListener {
            viewModel.confirmEvent(ignoreConflict = conflict == null)
            dialog.dismiss()
        }
        sheetBinding.editButton.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, CreateEventActivity::class.java))
        }
        dialog.show()
    }

    private fun showConflictDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("Schedule conflict")
            .setMessage("$message\n\nChange the time, change the location in chat, or continue anyway.")
            .setPositiveButton("Continue anyway") { _, _ -> viewModel.clearConflictAndContinue() }
            .setNeutralButton("Change time") { _, _ ->
                viewModel.resetState()
                binding.promptEditText.setText("Change the time to ")
                binding.promptEditText.requestFocus()
            }
            .setNegativeButton("Change location") { _, _ ->
                viewModel.resetState()
                binding.promptEditText.setText("Change the location to ")
                binding.promptEditText.requestFocus()
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_MODE = "AI_MODE"
        const val MODE_STUDENT = "STUDENT"
        const val MODE_CREATE = "CREATE"
        const val MODE_ADMIN = "ADMIN"
    }
}
