package com.example.campusconnect.activities

import com.example.campusconnect.core.common.showMessage
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.PollCard
import com.example.campusconnect.adapters.PollCardAdapter
import com.example.campusconnect.appContainer
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityPollBinding
import com.example.campusconnect.databinding.DialogCreatePollBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.domain.usecase.PollError
import com.example.campusconnect.domain.usecase.PollUseCases
import com.example.campusconnect.domain.usecase.VoteResult
import com.example.campusconnect.utils.AuthorizationManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Every poll for an event. The organizer (or an admin) can open new ones; attendees vote once each. */
class PollActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPollBinding
    private lateinit var adapter: PollCardAdapter
    private lateinit var polls: PollUseCases
    private var eventId: Int = -1
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPollBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.polls_title)

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1) {
            finish()
            return
        }
        polls = appContainer.pollUseCases()

        adapter = PollCardAdapter { poll, option -> handleVote(poll.id, option.id) }
        binding.pollsRecyclerView.layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.list_columns))
        binding.pollsRecyclerView.adapter = adapter
        binding.createPollFab.setOnClickListener { showCreateDialog() }

        loadPolls()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadPolls() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@PollActivity)
            if (!AuthorizationManager.canOpenEventSpace(db, currentUserId, eventId)) {
                Toast.makeText(this@PollActivity, R.string.event_space_locked, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            val event = db.eventDao().getEventById(eventId)
            supportActionBar?.subtitle = event?.title
            val role = db.userDao().getUserById(currentUserId)?.role
            val canManage = event != null &&
                ((event.creator_id == currentUserId && AccessPolicy.canCreateEvents(role)) || AccessPolicy.canAccessAdmin(role))
            binding.createPollFab.visibility = if (canManage) View.VISIBLE else View.GONE
            binding.emptySubtitle.setText(if (canManage) R.string.poll_empty_sub_organizer else R.string.poll_empty_sub)

            val dao = db.pollDao()
            combine(
                dao.getPollsForEvent(eventId),
                dao.getOptionsForEvent(eventId),
                dao.getUserVotesForEvent(eventId, currentUserId)
            ) { pollList, options, votes ->
                val byPoll = options.groupBy { it.poll_id }
                val mine = votes.associate { it.poll_id to it.option_id }
                pollList.map { PollCard(it, byPoll[it.id].orEmpty(), mine[it.id]) }
            }.collectLatest { cards ->
                adapter.submitList(cards)
                binding.emptyState.visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun handleVote(pollId: Int, optionId: Int) {
        lifecycleScope.launch {
            val message = when (polls.vote(pollId, optionId, currentUserId)) {
                VoteResult.RECORDED -> R.string.vote_recorded
                VoteResult.ALREADY_VOTED -> R.string.vote_already_cast
                VoteResult.CLOSED -> R.string.poll_closed_toast
                VoteResult.NOT_FOUND -> R.string.poll_not_found
            }
            showMessage(message)
        }
    }

    private fun showCreateDialog() {
        val d = DialogCreatePollBinding.inflate(layoutInflater)
        val optionFields = mutableListOf<TextInputEditText>()

        fun addOptionField() {
            val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
                hint = getString(R.string.poll_option_hint, optionFields.size + 1)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.poll_option_gap) }
            }
            val input = TextInputEditText(layout.context).apply {
                maxLines = 1
                filters = arrayOf(android.text.InputFilter.LengthFilter(PollUseCases.MAX_OPTION))
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            layout.addView(input)
            d.optionInputs.addView(layout)
            optionFields += input
            d.addOptionButton.visibility = if (optionFields.size < PollUseCases.MAX_OPTIONS) View.VISIBLE else View.GONE
        }
        repeat(PollUseCases.MIN_OPTIONS) { addOptionField() }
        d.addOptionButton.setOnClickListener { addOptionField() }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.poll_create)
            .setView(d.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.poll_publish, null)
            .create()
        dialog.show()
        // Attached after show() so validation errors keep the dialog open.
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val question = d.questionInput.text?.toString().orEmpty()
            val options = optionFields.map { it.text?.toString().orEmpty() }
            d.questionLayout.error = null
            d.optionsError.visibility = View.GONE

            val days = when (d.durationGroup.checkedChipId) {
                R.id.duration3d -> 3L
                R.id.duration1w -> 7L
                R.id.durationNone -> 0L
                else -> 1L
            }
            val closesAt = if (days == 0L) 0L else System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days)
            val label = if (closesAt == 0L) "" else SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(closesAt))

            lifecycleScope.launch {
                when (val error = polls.create(currentUserId, eventId, question, options, closesAt, label)) {
                    null -> {
                        dialog.dismiss()
                        showMessage(R.string.poll_created)
                    }
                    PollError.QUESTION_REQUIRED -> d.questionLayout.error = getString(R.string.poll_err_question)
                    PollError.NOT_ALLOWED, PollError.NOT_FOUND -> {
                        dialog.dismiss()
                        showMessage(R.string.not_allowed)
                    }
                    else -> {
                        d.optionsError.setText(
                            when (error) {
                                PollError.DUPLICATE_OPTIONS -> R.string.poll_err_duplicate
                                PollError.TOO_LONG -> R.string.poll_err_too_long
                                else -> R.string.poll_err_options
                            }
                        )
                        d.optionsError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}
