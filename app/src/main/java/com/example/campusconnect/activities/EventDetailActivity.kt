package com.example.campusconnect.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityEventDetailBinding
import com.example.campusconnect.models.RSVP
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class EventDetailActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventDetailBinding
    private var eventId: Int = -1
    private var currentUserId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Event Details"

        val prefs = getSharedPreferences("campus_connect_prefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getInt("user_id", -1)

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1) {
            finish()
            return
        }

        loadEventDetails()

        binding.rsvpButton.setOnClickListener {
            handleRSVP()
        }

        binding.groupChatButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("EVENT_ID", eventId)
            startActivity(intent)
        }

        binding.pollsButton.setOnClickListener {
            val intent = Intent(this, PollActivity::class.java)
            intent.putExtra("EVENT_ID", eventId)
            startActivity(intent)
        }

        binding.vouchButton.setOnClickListener {
            handleVouch()
        }

        binding.scrollUpFab.setOnClickListener {
            binding.nestedScrollView.fullScroll(android.view.View.FOCUS_UP)
        }

        binding.scrollDownFab.setOnClickListener {
            binding.nestedScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    private fun handleVouch() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@EventDetailActivity)
            val event = db.eventDao().getEventById(eventId)
            val toUserId = event?.creator_id
            
            if (toUserId != null && toUserId != currentUserId) {
                // Check if already vouched
                val existing = db.userDao().getKarmaTransaction(currentUserId, toUserId, eventId)
                if (existing == null) {
                    db.userDao().insertKarmaTransaction(com.example.campusconnect.models.KarmaTransaction(
                        from_user_id = currentUserId,
                        to_user_id = toUserId,
                        event_id = eventId,
                        points = 1,
                        given_at = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    ))
                    db.userDao().addKarma(toUserId, 1)
                    Toast.makeText(this@EventDetailActivity, "Karma shared! ✨", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@EventDetailActivity, "You already vouched for this host.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@EventDetailActivity, "Cannot vouch for this event.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadEventDetails() {
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@EventDetailActivity).eventDao().getEventById(eventId)
            event?.let {
                binding.titleTextView.text = it.title
                binding.descriptionTextView.text = it.description
                binding.dateTimeTextView.text = "${it.date}, ${it.time}"
                binding.locationTextView.text = it.location
                
                // Update RSVP button state
                updateRSVPButtonState()
            }
        }
    }

    private fun updateRSVPButtonState() {
        lifecycleScope.launch {
            val rsvp = AppDatabase.getDatabase(this@EventDetailActivity).rsvpDao().getUserRSVPForEvent(currentUserId, eventId)
            if (rsvp != null) {
                binding.rsvpButton.text = getString(R.string.you_are_going)
                binding.rsvpButton.isEnabled = false
            } else {
                // Check if full
                val event = AppDatabase.getDatabase(this@EventDetailActivity).eventDao().getEventById(eventId)
                val count = AppDatabase.getDatabase(this@EventDetailActivity).rsvpDao().getAttendeeCount(eventId)
                if (event?.max_attendees != null && count >= event.max_attendees) {
                    binding.rsvpButton.text = getString(R.string.join_waitlist)
                } else {
                    binding.rsvpButton.text = getString(R.string.rsvp_going)
                }
            }
        }
    }

    private fun handleRSVP() {
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@EventDetailActivity).eventDao().getEventById(eventId)
            val count = AppDatabase.getDatabase(this@EventDetailActivity).rsvpDao().getAttendeeCount(eventId)
            val status = if (event?.max_attendees != null && count >= event.max_attendees) "waitlisted" else "going"
            
            val rsvp = RSVP(
                user_id = currentUserId,
                event_id = eventId,
                status = status,
                checked_in = false,
                joined_at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            )
            AppDatabase.getDatabase(this@EventDetailActivity).rsvpDao().insertRSVP(rsvp)
            Toast.makeText(this@EventDetailActivity, "RSVP Successful: $status", Toast.LENGTH_SHORT).show()
            updateRSVPButtonState()
        }
    }
}
