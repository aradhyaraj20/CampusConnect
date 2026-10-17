package com.example.campusconnect.activities

import com.example.campusconnect.core.common.showMessage
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.campusconnect.R
import com.example.campusconnect.adapters.RecapPhotoAdapter
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityEventRecapBinding
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.models.RecapPhoto
import com.example.campusconnect.utils.AuthorizationManager
import com.example.campusconnect.utils.DateFormats
import com.example.campusconnect.utils.ImageStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Shared photo memories for an event. Anyone with a seat (plus the organizer and admins) can add
 * photos once the event has started; people can remove their own, organizers and admins any.
 */
class EventRecapActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEventRecapBinding
    private lateinit var adapter: RecapPhotoAdapter
    private var eventId: Int = -1
    private var currentUserId: Int = -1
    private var canModerate = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) savePhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventRecapBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.event_memories)

        eventId = intent.getIntExtra("EVENT_ID", -1)
        currentUserId = SessionManager.getInstance(this).getCurrentUserId()
        if (eventId == -1 || currentUserId <= 0) {
            finish()
            return
        }

        adapter = RecapPhotoAdapter(::confirmDelete)
        binding.recapRecyclerView.layoutManager = StaggeredGridLayoutManager(
            resources.getInteger(R.integer.recap_columns), StaggeredGridLayoutManager.VERTICAL
        )
        binding.recapRecyclerView.adapter = adapter
        binding.addPhotoFab.visibility = View.GONE
        binding.addPhotoFab.setOnClickListener { onAddPhoto() }

        val db = AppDatabase.getDatabase(this)
        lifecycleScope.launch {
            val event = db.eventDao().getEventById(eventId)
            supportActionBar?.subtitle = event?.title
            val role = db.userDao().getUserById(currentUserId)?.role
            canModerate = event?.creator_id == currentUserId || AccessPolicy.canAccessAdmin(role)
            if (AuthorizationManager.canOpenEventSpace(db, currentUserId, eventId)) {
                binding.addPhotoFab.visibility = View.VISIBLE
            } else {
                binding.emptySubtitle.setText(R.string.recap_empty_sub_locked)
            }
        }
        lifecycleScope.launch {
            db.eventDao().getRecapPhotos(eventId).collectLatest { photos ->
                adapter.submitList(photos)
                binding.emptyState.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun onAddPhoto() {
        lifecycleScope.launch {
            val event = AppDatabase.getDatabase(this@EventRecapActivity).eventDao().getEventById(eventId) ?: return@launch
            if (event.date_timestamp > System.currentTimeMillis()) {
                showMessage(R.string.recap_not_started)
                return@launch
            }
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun savePhoto(uri: android.net.Uri) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@EventRecapActivity)
            // Checked again here: the RSVP may have changed while the picker was open.
            if (!AuthorizationManager.canOpenEventSpace(db, currentUserId, eventId)) {
                showMessage(R.string.event_space_locked)
                return@launch
            }
            val stored = ImageStore.import(this@EventRecapActivity, uri, ImageStore.RECAPS)
            if (stored == null) {
                showMessage(R.string.image_import_failed)
                return@launch
            }
            db.eventDao().addRecapPhoto(
                RecapPhoto(event_id = eventId, user_id = currentUserId, image_uri = stored, posted_at = DateFormats.storedDay())
            )
            showMessage(R.string.recap_photo_added)
        }
    }

    private fun confirmDelete(photo: RecapPhoto) {
        if (photo.user_id != currentUserId && !canModerate) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.recap_delete_title)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                lifecycleScope.launch {
                    AppDatabase.getDatabase(this@EventRecapActivity).eventDao().deleteRecapPhoto(photo.id)
                    ImageStore.delete(this@EventRecapActivity, photo.image_uri)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
