package com.example.campusconnect.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.campusconnect.R
import com.example.campusconnect.adapters.AttendeeAdapter
import com.example.campusconnect.core.common.NotificationPermission
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityEventDetailBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.Notification
import com.example.campusconnect.utils.AttendeeCsv
import com.example.campusconnect.utils.ReminderManager
import com.example.campusconnect.viewmodel.EventDetailUiEvent
import com.example.campusconnect.viewmodel.EventDetailViewModel
import com.example.campusconnect.viewmodel.EventDetailViewModelFactory
import com.example.campusconnect.viewmodel.ONE_HOUR_MILLIS
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EventDetailActivity : AppCompatActivity() {
    private val notificationPermission = NotificationPermission(this)

    private lateinit var binding: ActivityEventDetailBinding
    private lateinit var attendeeAdapter: AttendeeAdapter
    private lateinit var viewModel: EventDetailViewModel

    private var eventId: Int = -1
    private var currentUserId: Int = -1
    private var canEdit: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.event_details_title)

        currentUserId = SessionManager.getInstance(this).getCurrentUserId()

        eventId = intent.getIntExtra("EVENT_ID", -1)
        if (eventId == -1 || currentUserId <= 0) {
            finish()
            return
        }

        // Single Room Database instantiation & DAO initialization
        val database = AppDatabase.getDatabase(this)
        val factory = EventDetailViewModelFactory(
            eventDao = database.eventDao(),
            rsvpDao = database.rsvpDao(),
            userDao = database.userDao(),
            notificationDao = database.notificationDao(),
            eventId = eventId,
            currentUserId = currentUserId
        )
        viewModel = ViewModelProvider(this, factory)[EventDetailViewModel::class.java]

        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        attendeeAdapter = AttendeeAdapter(emptyList())
        binding.attendeesRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.attendeesRecyclerView.adapter = attendeeAdapter
    }

    private fun setupListeners() {
        binding.viewOnMapButton.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            startActivity(intent)
        }

        binding.rsvpButton.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isGoing || state.isWaitlisted) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(if (state.isGoing) R.string.leave_event_title else R.string.leave_waitlist_title)
                    .apply { if (state.isGoing) setMessage(R.string.leave_event_message) }
                    .setPositiveButton(R.string.leave_action) { _, _ -> viewModel.cancelRsvp() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                viewModel.handleRSVP()
            }
        }

        binding.groupChatButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            startActivity(intent)
        }

        binding.pollsButton.setOnClickListener {
            val intent = Intent(this, PollActivity::class.java).apply {
                putExtra("EVENT_ID", eventId)
            }
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_event_detail, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: android.view.Menu): Boolean {
        // Organizer/admin-only actions stay hidden for regular attendees.
        menu.findItem(R.id.menu_edit)?.isVisible = canEdit
        menu.findItem(R.id.menu_email)?.isVisible = canEdit
        menu.findItem(R.id.menu_export_csv)?.isVisible = canEdit
        menu.findItem(R.id.menu_scan_tickets)?.isVisible = canEdit
        menu.findItem(R.id.menu_delete)?.isVisible = canEdit
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_memories -> {
                startActivity(Intent(this, EventRecapActivity::class.java).apply {
                    putExtra("EVENT_ID", eventId)
                })
                true
            }
            R.id.menu_add_calendar -> {
                addToSystemCalendar()
                true
            }
            R.id.menu_reminders -> {
                scheduleSmartReminders()
                true
            }
            R.id.menu_edit -> {
                startActivity(Intent(this, CreateEventActivity::class.java).apply {
                    putExtra(CreateEventActivity.EXTRA_EVENT_ID, eventId)
                })
                true
            }
            R.id.menu_email -> {
                emailAttendees()
                true
            }
            R.id.menu_scan_tickets -> {
                startActivity(Intent(this, QrScanActivity::class.java).putExtra(QrScanActivity.EXTRA_EVENT_ID, eventId))
                true
            }
            R.id.menu_export_csv -> {
                exportAttendeesCsv()
                true
            }
            R.id.menu_delete -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete_event_title)
                    .setMessage(R.string.delete_event_message)
                    .setPositiveButton(R.string.delete_action) { _, _ -> deleteEvent() }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collectLatest { state ->
                        binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                        state.event?.let { event ->
                            binding.titleTextView.text = event.title
                            binding.descriptionTextView.text = event.description
                            binding.dateTimeTextView.text = getString(R.string.date_time_format, event.date, event.time)
                            binding.locationTextView.text = event.location
                            if (!event.image_uri.isNullOrBlank()) {
                                binding.bannerImage.load(event.image_uri)
                            }
                        }

                        // RSVP Button State
                        binding.rsvpButton.text = getString(state.rsvpButtonTextRes)
                        binding.rsvpButton.isEnabled = state.isRsvpEnabled

                        // Organizer/admin-only actions live in the toolbar overflow menu.
                        if (canEdit != state.isEditVisible) {
                            canEdit = state.isEditVisible
                            invalidateOptionsMenu()
                        }

                        // Access Control State
                        binding.groupChatButton.isEnabled = state.hasAccess
                        binding.pollsButton.isEnabled = state.hasAccess
                        binding.restrictedMessage.visibility = if (state.hasAccess) View.GONE else View.VISIBLE

                        // Attendees list & count info
                        attendeeAdapter.updateAttendees(state.attendees)
                        if (state.isFull) {
                            binding.attendeeInfoTextView.text = getString(R.string.going_full_format, state.attendeeCount)
                        } else {
                            binding.attendeeInfoTextView.text = getString(R.string.going_left_format, state.attendeeCount, state.spotsLeft.toString())
                        }
                    }
                }

                launch {
                    viewModel.uiEvent.collect { event ->
                        when (event) {
                            is EventDetailUiEvent.ShowToastRes -> {
                                val message = if (event.formatArgs != null) {
                                    getString(event.messageRes, event.formatArgs)
                                } else {
                                    getString(event.messageRes)
                                }
                                Toast.makeText(this@EventDetailActivity, message, Toast.LENGTH_SHORT).show()
                            }

                            is EventDetailUiEvent.ShowAiDialog -> {
                                showAiDescriptionDialog(event.suggestedDescription)
                            }

                            EventDetailUiEvent.Close -> finish()
                        }
                    }
                }
            }
        }
    }

    private fun showAiDescriptionDialog(suggestedDescription: String) {
        val inputEditText = EditText(this).apply {
            setText(suggestedDescription)
            hint = getString(R.string.ai_description_hint)
            setPadding(48, 32, 48, 32)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.ai_description_title))
            .setView(inputEditText)
            .setPositiveButton(getString(R.string.save)) { dialog, _ ->
                val newDescription = inputEditText.text.toString().trim()
                if (newDescription.isNotEmpty()) {
                    viewModel.updateEventDescription(newDescription)
                }
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Re-checks permission against the database (the hidden menu item is not enough),
     * tells attendees, cancels their reminders and removes every row tied to the event.
     */
    private fun deleteEvent() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@EventDetailActivity)
            val event = db.eventDao().getEventById(eventId) ?: return@launch finish()
            val role = db.userDao().getUserById(currentUserId)?.role
            if (event.creator_id != currentUserId && !AccessPolicy.canAccessAdmin(role)) {
                Toast.makeText(this@EventDetailActivity, R.string.not_allowed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val attendees = db.rsvpDao().getAttendeesForEvent(eventId).first()
            ReminderManager.cancelReminders(this@EventDetailActivity, eventId)
            db.deleteEventCascade(eventId)
            val now = System.currentTimeMillis()
            attendees.filter { it.id != currentUserId }.forEach { user ->
                db.notificationDao().insertNotification(
                    Notification(
                        user_id = user.id,
                        title = getString(R.string.event_deleted_notice_title),
                        message = getString(R.string.event_deleted_notice_message, event.title),
                        timestamp = now,
                        type = "event_update"
                    )
                )
            }
            Toast.makeText(this@EventDetailActivity, R.string.event_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * "Give organizer more freedom ... let them send mails" - composes an email
     * to everyone who RSVP'd "going", via the device's mail app (no SMTP
     * credentials are stored/sent by this app).
     */
    private fun emailAttendees() {
        lifecycleScope.launch {
            val event = viewModel.uiState.value.event ?: return@launch
            val attendees = AppDatabase.getDatabase(this@EventDetailActivity)
                .rsvpDao().getAttendeesForEvent(eventId).first()
            val emails = attendees.map { it.email }.filter { it.isNotBlank() }.toTypedArray()

            if (emails.isEmpty()) {
                Toast.makeText(this@EventDetailActivity, "No attendees to email yet", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:")
                putExtra(Intent.EXTRA_BCC, emails)
                putExtra(Intent.EXTRA_SUBJECT, "Update: ${event.title}")
                putExtra(Intent.EXTRA_TEXT, "Hi everyone,\n\nThis is an update about \"${event.title}\" on ${event.date} at ${event.time}.\n\n")
            }
            try {
                startActivity(Intent.createChooser(intent, "Email attendees"))
            } catch (_: Exception) {
                Toast.makeText(this@EventDetailActivity, "No email app available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Roadmap Phase 5: writes the "going" list to a CSV in cache and hands it to the share sheet. */
    private fun exportAttendeesCsv() {
        lifecycleScope.launch {
            val event = viewModel.uiState.value.event ?: return@launch
            val attendees = AppDatabase.getDatabase(this@EventDetailActivity)
                .rsvpDao().getAttendeesForEvent(eventId).first()
            if (attendees.isEmpty()) {
                Toast.makeText(this@EventDetailActivity, "No attendees to export yet", Toast.LENGTH_SHORT).show()
                return@launch
            }
            try {
                val dir = java.io.File(cacheDir, "exports").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() } // don't leave older attendee lists lying around
                val safeName = event.title.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_').ifEmpty { "event" }
                val file = java.io.File(dir, "attendees_${safeName}_${event.id}.csv")
                file.writeText(AttendeeCsv.build(attendees), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(this@EventDetailActivity, "$packageName.fileprovider", file)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Attendees: ${event.title}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(share, "Export attendees"))
            } catch (_: Exception) {
                Toast.makeText(this@EventDetailActivity, "Could not export attendees", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun scheduleSmartReminders() {
        val currentEvent = viewModel.uiState.value.event ?: return
        notificationPermission.request()
        ReminderManager.scheduleReminders(this, currentEvent)
        Toast.makeText(this, getString(R.string.reminders_set), Toast.LENGTH_LONG).show()
    }

    private fun addToSystemCalendar() {
        val currentEvent = viewModel.uiState.value.event ?: return
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, currentEvent.title)
            putExtra(CalendarContract.Events.DESCRIPTION, currentEvent.description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, currentEvent.location)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, currentEvent.date_timestamp)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, currentEvent.date_timestamp + ONE_HOUR_MILLIS)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
