package com.example.campusconnect.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.appContainer
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.common.showMessage
import com.example.campusconnect.core.security.RoleGuard
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityCreateEventBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.domain.event.EventTime
import com.example.campusconnect.domain.usecase.EventForm
import com.example.campusconnect.domain.usecase.SaveEventError
import com.example.campusconnect.domain.usecase.SaveEventResult
import com.example.campusconnect.domain.usecase.SaveEventUseCase
import com.example.campusconnect.models.Event
import com.example.campusconnect.utils.ImageStore
import com.example.campusconnect.utils.ReminderManager
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
    private var selectedImageUri: String? = null

    // The picked image is copied into app storage right away: a picker URI stops being readable
    // after a restart, which used to make event covers disappear.
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val stored = ImageStore.import(this@CreateEventActivity, uri, ImageStore.EVENT_COVERS)
            if (stored == null) {
                showMessage(R.string.image_import_failed)
                return@launch
            }
            // Picking twice before saving: drop the copy that is being replaced.
            ImageStore.delete(this@CreateEventActivity, selectedImageUri)
            selectedImageUri = stored
            binding.eventImagePreview.load(stored)
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

        binding.pickEventImageButton.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        val editEventId = intent.getIntExtra(EXTRA_EVENT_ID, -1)
        if (editEventId != -1) {
            loadEventForEdit(editEventId)
        } else {
            supportActionBar?.title = getString(R.string.qa_create_event)
            binding.screenTitle.setText(R.string.create_event_screen_title)
            updateDateLabel()
            updateTimeLabel()
        }

        binding.dateEditText.setOnClickListener { showDatePicker() }
        binding.timeEditText.setOnClickListener { showTimePicker() }

        binding.createButton.setOnClickListener {
            saveEvent()
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
            binding.screenTitle.setText(R.string.edit_event_screen_title)
            binding.createButton.text = getString(R.string.edit_event_full)

            binding.titleEditText.setText(event.title)
            binding.descriptionEditText.setText(event.description)
            binding.locationEditText.setText(event.location)
            binding.maxAttendeesEditText.setText(event.max_attendees?.toString() ?: "")
            binding.flashSessionSwitch.isChecked = event.is_flash
            if (event.type == "event") binding.radioEvent.isChecked = true else binding.radioStudyGroup.isChecked = true

            // Seed/AI events keep the displayed start time in `time`; the timestamp may carry another time of day.
            selectedCalendar.timeInMillis = EventTime.combine(event.date_timestamp, event.time, TimeZone.getDefault())
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
        val form = EventForm(
            title = binding.titleEditText.text.toString(),
            description = binding.descriptionEditText.text.toString(),
            location = binding.locationEditText.text.toString(),
            maxAttendeesText = binding.maxAttendeesEditText.text.toString(),
            type = if (binding.radioEvent.isChecked) "event" else "study_group",
            isFlash = binding.flashSessionSwitch.isChecked,
            dateTimestamp = selectedCalendar.timeInMillis,
            imageUri = selectedImageUri
        )
        clearFieldErrors()
        // Disabled while saving so a double tap can't create the event twice.
        binding.createButton.isEnabled = false
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@CreateEventActivity)
            val useCase = SaveEventUseCase(db.eventDao(), db.userDao(), db.rsvpDao(), db.notificationDao(), appContainer.transactions)
            val userId = SessionManager.getInstance(this@CreateEventActivity).getCurrentUserId()
            when (val result = useCase.save(userId, editingEvent?.id, form)) {
                is SaveEventResult.Saved -> {
                    val previousImage = editingEvent?.image_uri
                    if (selectedImageUri != null && previousImage != result.event.image_uri) {
                        ImageStore.delete(this@CreateEventActivity, previousImage)
                    }
                    // Anyone on this device who set reminders gets them moved to the new time.
                    if (!result.isNew) ReminderManager.rescheduleForEvent(this@CreateEventActivity, result.event)
                    Toast.makeText(
                        this@CreateEventActivity,
                        if (result.isNew) R.string.event_created_success else R.string.event_updated_success,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
                is SaveEventResult.Invalid -> {
                    binding.createButton.isEnabled = true
                    showValidationError(result.error, form)
                }
            }
        }
    }

    private fun clearFieldErrors() {
        listOf(
            binding.titleLayout, binding.descriptionLayout, binding.locationLayout,
            binding.maxAttendeesLayout, binding.dateLayout
        ).forEach { it.error = null }
    }

    /** Puts the problem on the field it belongs to; anything not tied to one field goes in a Snackbar. */
    private fun showValidationError(error: SaveEventError, form: EventForm) {
        val message = getString(errorMessage(error))
        when (error) {
            SaveEventError.REQUIRED_FIELDS -> {
                val required = getString(R.string.error_field_required)
                val missing = listOfNotNull(
                    binding.titleLayout.takeIf { form.title.isBlank() },
                    binding.descriptionLayout.takeIf { form.description.isBlank() },
                    binding.locationLayout.takeIf { form.location.isBlank() }
                )
                if (missing.isEmpty()) showMessage(message)
                missing.forEach { it.error = required }
                missing.firstOrNull()?.requestFocus()
            }
            SaveEventError.INVALID_CAPACITY, SaveEventError.CAPACITY_BELOW_ATTENDEES -> {
                binding.maxAttendeesLayout.error = message
                binding.maxAttendeesLayout.requestFocus()
            }
            SaveEventError.PAST_DATE -> binding.dateLayout.error = message
            else -> showMessage(message)
        }
    }

    private fun errorMessage(error: SaveEventError): Int = when (error) {
        SaveEventError.REQUIRED_FIELDS -> R.string.please_fill_required_fields
        SaveEventError.INVALID_CAPACITY -> R.string.err_invalid_capacity
        SaveEventError.PAST_DATE -> R.string.err_event_past_date
        SaveEventError.CAPACITY_BELOW_ATTENDEES -> R.string.err_capacity_below_attendees
        SaveEventError.NOT_ALLOWED -> R.string.you_can_only_edit_events_you_cre
        SaveEventError.NOT_FOUND -> R.string.event_not_found
    }

    companion object {
        const val EXTRA_EVENT_ID = "EXTRA_EVENT_ID"
    }
}
