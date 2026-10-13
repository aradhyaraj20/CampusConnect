package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.withTransaction
import com.example.campusconnect.R
import com.example.campusconnect.adapters.PollOptionAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityPollBinding
import com.example.campusconnect.models.PollVote
import com.example.campusconnect.utils.AuthorizationManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PollActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPollBinding
    private lateinit var adapter: PollOptionAdapter
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

        adapter = PollOptionAdapter(emptyList()) { option ->
            handleVote(option.id, option.poll_id)
        }

        binding.optionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.optionsRecyclerView.adapter = adapter

        binding.scrollUpFab.setOnClickListener {
            binding.optionsRecyclerView.smoothScrollToPosition(0)
        }

        binding.scrollDownFab.setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                binding.optionsRecyclerView.smoothScrollToPosition(count - 1)
            }
        }

        loadPoll()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadPoll() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@PollActivity)
            if (!AuthorizationManager.canOpenEventSpace(db, currentUserId, eventId)) {
                Toast.makeText(this@PollActivity, R.string.event_space_locked, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            db.pollDao().getPollsForEvent(eventId).collectLatest { polls ->
                val poll = polls.firstOrNull()
                if (poll == null) {
                    binding.pollQuestion.setText(R.string.no_polls_yet)
                    binding.pollStats.text = ""
                    adapter.updateOptions(emptyList())
                    return@collectLatest
                }
                binding.pollQuestion.text = poll.question
                db.pollDao().getOptionsForPoll(poll.id).collectLatest { options ->
                    adapter.updateOptions(options)
                    val totalVotes = options.sumOf { it.vote_count }
                    binding.pollStats.text = resources.getQuantityString(R.plurals.poll_votes, totalVotes, totalVotes, poll.ends_at)
                }
            }
        }
    }

    /** One vote per user per poll, enforced by a unique index; repeat taps are ignored. */
    private fun handleVote(optionId: Int, pollId: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@PollActivity)
            val inserted = db.withTransaction {
                val id = db.pollDao().vote(PollVote(poll_id = pollId, option_id = optionId, user_id = currentUserId))
                if (id != -1L) db.pollDao().incrementVoteCount(optionId)
                id != -1L
            }
            Toast.makeText(
                this@PollActivity,
                if (inserted) R.string.vote_recorded else R.string.vote_already_cast,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
