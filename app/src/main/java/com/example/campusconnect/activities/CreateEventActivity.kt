package com.example.campusconnect.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityCreateEventBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Event
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Handles both creating a new event and, when EXTRA_EVENT_ID is passed, doing
 * a full edit of an existing one (title/description/date/time/location/
 * capacity/type/photo) - giving organizers real editing freedom instead of
 * the old description-only patch dialog. Only the event's creator or an
 * admin may edit/delete (enforced here and in EventDetailActivity).
 */
class CreateEventActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCreateEventBinding
    private var selectedCalendar = Calendar.getInstance()
    private var editingEvent: Event? = null
    private var selectedImageUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { /* provider may not support persistable grants */ }
            selectedImageUri = it
            binding.eventImagePreview.load(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoleGuard.enforce(this, AccessPolicy::canCreateEvents)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val role = SessionManager.getInstance(this).getCurrentUserRole()
        if (role !in listOf("ORGANIZER", "ADMIN")) {
            Toast.makeText(this, R.string.organizer_access_required_to_cre, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.pickEventImageButton.setOnClickListener { pickImage.launch("image/*") }

        val editEventId = intent.getIntExtra(EXTRA_EVENT_ID, -1)
        if (editEventId != -1) {
            loadEventForEdit(editEventId)
        } else {
            supportActionBar?.title = getString(R.string.qa_create_event)
            binding.screenTitle.text = "Create New Event"
            updateDateLabel()
            updateTimeLabel()
        }

        binding.dateEditText.setOnClickListener { showDatePicker() }
        binding.timeEditText.setOnClickListener { showTimePicker() }

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

    private fun loadEventForEdit(eventId: Int) {
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@CreateEventActivity).eventDao().getEventById(eventId)
            if (event == null) {
                Toast.makeText(this@CreateEventActivity, R.string.event_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            val currentUserId = SessionManager.getInstance(this@CreateEventActivity).getCurrentUserId()
            val currentRole = SessionManager.getInstance(this@CreateEventActivity).getCurrentUserRole()
            if (event.creator_id != currentUserId && currentRole != "ADMIN") {
                Toast.makeText(this@CreateEventActivity, R.string.you_can_only_edit_events_you_cre, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }

            editingEvent = event
            supportActionBar?.title = getString(R.string.edit_event_full)
            binding.screenTitle.text = "Edit Event"
            binding.createButton.text = getString(R.string.edit_event_full)

            binding.titleEditText.setText(event.title)
            binding.descriptionEditText.setText(event.description)
            binding.locationEditText.setText(event.location)
            binding.maxAttendeesEditText.setText(event.max_attendees?.toString() ?: "")
            binding.flashSessionSwitch.isChecked = event.is_flash
            if (event.type == "event") binding.radioEvent.isChecked = true else binding.radioStudyGroup.isChecked = true

            selectedCalendar.timeInMillis = event.date_timestamp
            updateDateLabel()
            updateTimeLabel()

            if (!event.image_uri.isNullOrBlank()) {
                binding.eventImagePreview.load(event.image_uri)
            }
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedCalendar.set(Calendar.YEAR, year)
                selectedCalendar.set(Calendar.MONTH, month)
                selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateLabel()
            },
            selectedCalendar.get(Calendar.YEAR),
            selectedCalendar.get(Calendar.MONTH),
            selectedCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedCalendar.set(Calendar.MINUTE, minute)
                updateTimeLabel()
            },
            selectedCalendar.get(Calendar.HOUR_OF_DAY),
            selectedCalendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateLabel() {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.dateEditText.setText(format.format(selectedCalendar.time))
    }

    private fun updateTimeLabel() {
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        binding.timeEditText.setText(format.format(selectedCalendar.time))
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
                val dateTimestamp = selectedCalendar.timeInMillis
                val date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time)
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(selectedCalendar.time)
                val expiryTime = if (isFlash) dateTimestamp + 3600000 else null

                val currentUserId = SessionManager.getInstance(this@CreateEventActivity).getCurrentUserId()
                val existing = editingEvent
                val imageUri = selectedImageUri?.toString() ?: existing?.image_uri

                val event = Event(
                    id = existing?.id ?: 0,
                    title = title,
                    description = description,
                    type = type,
                    category = existing?.category ?: (if (type == "event") "Social" else "Academic"),
                    image_uri = imageUri,
                    date = date,
                    time = time,
                    location = location,
                    map_lat = existing?.map_lat,
                    map_lng = existing?.map_lng,
                    max_attendees = maxAttendees,
                    is_user_created = true,
                    creator_id = existing?.creator_id ?: currentUserId,
                    is_flash = isFlash,
                    expiry_time = expiryTime,
                    date_timestamp = dateTimestamp,
                    visibility = existing?.visibility ?: "public",
                    eligibility = existing?.eligibility,
                    tags = existing?.tags,
                    organizer_name = existing?.organizer_name,
                    registration_deadline = existing?.registration_deadline,
                    status = existing?.status ?: "active"
                )

                // insertEvent uses OnConflictStrategy.REPLACE, so passing the
                // existing id here performs an in-place update rather than
                // creating a duplicate row.
                AppDatabase.getDatabase(this@CreateEventActivity).eventDao().insertEvent(event)
                Toast.makeText(
                    this@CreateEventActivity,
                    if (existing != null) "Event updated" else "Event Created Successfully",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        } else {
            Toast.makeText(this, R.string.please_fill_required_fields, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "EXTRA_EVENT_ID"
    }
}
