package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.common.applyImeAndSystemBarInsets
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityQrTicketBinding
import com.example.campusconnect.utils.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrTicketActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrTicketBinding
    private var rsvpId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyImeAndSystemBarInsets(binding.toolbar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.qr_ticket_title)

        rsvpId = intent.getIntExtra("RSVP_ID", -1)
        if (rsvpId == -1) {
            finish()
            return
        }

        loadTicketDetails()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadTicketDetails() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@QrTicketActivity)
            val currentUserId = SessionManager.getInstance(this@QrTicketActivity).getCurrentUserId()
            val rsvp = db.rsvpDao().getRsvpById(rsvpId)
            val event = rsvp?.let { db.eventDao().getEventById(it.event_id) }
            // Only the ticket holder gets a QR, and only for a confirmed seat on a live event.
            if (rsvp == null || event == null || rsvp.user_id != currentUserId) {
                Toast.makeText(this@QrTicketActivity, R.string.event_not_found, Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            binding.eventTitle.text = event.title
            binding.eventTime.text = getString(R.string.date_time_format, event.date, event.time)
            when {
                !event.status.equals("active", ignoreCase = true) -> {
                    binding.statusTextView.setText(R.string.ticket_event_cancelled)
                    binding.qrImageView.setImageDrawable(null)
                }
                rsvp.status != "going" -> {
                    binding.statusTextView.setText(R.string.ticket_waitlisted)
                    binding.qrImageView.setImageDrawable(null)
                }
                else -> {
                    binding.statusTextView.setText(
                        if (rsvp.checked_in) R.string.ticket_checked_in else R.string.ticket_not_checked_in
                    )
                    val payload = QrCodeGenerator.generateSignedTicketPayload(
                        rsvp.id, rsvp.event_id, rsvp.user_id, event.date_timestamp
                    )
                    // Building a 512x512 bitmap pixel by pixel is too slow for the main thread.
                    val qrBitmap = withContext(Dispatchers.Default) { QrCodeGenerator.generateQrCode(payload) }
                    binding.qrImageView.setImageBitmap(qrBitmap)
                }
            }
        }
    }
}
