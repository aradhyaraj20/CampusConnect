package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.campusconnect.adapters.PollOptionAdapter
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityPollBinding
import com.example.campusconnect.models.PollVote
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PollActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPollBinding
    private lateinit var adapter: PollOptionAdapter
    private var eventId: Int = -1
    private var currentUserId: Int = 1 // Placeholder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPollBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Polls"

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
            db.pollDao().getPollsForEvent(eventId).collectLatest { polls ->
                if (polls.isNotEmpty()) {
                    val poll = polls[0]
                    binding.pollQuestion.text = poll.question
                    
                    db.pollDao().getOptionsForPoll(poll.id).collectLatest { options ->
                        adapter.updateOptions(options)
                        val totalVotes = options.sumOf { it.vote_count }
                        binding.pollStats.text = "$totalVotes votes · Ends at ${poll.ends_at}"
                    }
                }
            }
        }
    }

    private fun handleVote(optionId: Int, pollId: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@PollActivity)
            // In a real app, check if already voted
            db.pollDao().vote(PollVote(poll_id = pollId, option_id = optionId, user_id = currentUserId))
            db.pollDao().incrementVoteCount(optionId)
            Toast.makeText(this@PollActivity, "Vote recorded!", Toast.LENGTH_SHORT).show()
        }
    }
}
