package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityCreateEventBinding
import com.example.campusconnect.models.Event
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CreateEventActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateEventBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Event"

        binding.createButton.setOnClickListener {
            saveEvent()
        }

        binding.scrollUpFab.setOnClickListener {
            binding.nestedScrollView.fullScroll(android.view.View.FOCUS_UP)
        }

        binding.scrollDownFab.setOnClickListener {
            binding.nestedScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun saveEvent() {
        val title = binding.titleEditText.text.toString()
        val description = binding.descriptionEditText.text.toString()
        val location = binding.locationEditText.text.toString()
        val maxAttendees = binding.maxAttendeesEditText.text.toString().toIntOrNull()
        val type = if (binding.radioEvent.isChecked) "event" else "study_group"
        val isFlash = binding.flashSessionSwitch.isChecked

        if (title.isNotEmpty() && description.isNotEmpty() && location.isNotEmpty()) {
            lifecycleScope.launch {
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val expiryTime = if (isFlash) System.currentTimeMillis() + 3600000 else null
                
                val event = Event(
                    title = title,
                    description = description,
                    type = type,
                    category = if (type == "event") "Social" else "Academic",
                    image_uri = null,
                    date = date,
                    time = time,
                    location = location,
                    map_lat = null,
                    map_lng = null,
                    max_attendees = maxAttendees,
                    is_user_created = true,
                    creator_id = 1, // Placeholder
                    is_flash = isFlash,
                    expiry_time = expiryTime
                )
                
                AppDatabase.getDatabase(this@CreateEventActivity).eventDao().insertEvent(event)
                Toast.makeText(this@CreateEventActivity, "Event Created Successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
        }
    }
}
