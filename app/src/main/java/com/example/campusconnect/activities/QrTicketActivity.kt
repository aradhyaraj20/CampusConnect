package com.example.campusconnect.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.databinding.ActivityQrTicketBinding
import com.example.campusconnect.utils.QrCodeGenerator
import kotlinx.coroutines.launch

class QrTicketActivity : AppCompatActivity() {
    private lateinit var binding: ActivityQrTicketBinding
    private var rsvpId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrTicketBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "QR Ticket"

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
            val rsvp = db.rsvpDao().getRsvpById(rsvpId)
            rsvp?.let {
                val event = db.eventDao().getEventById(it.event_id)
                event?.let { e ->
                    binding.eventTitle.text = e.title
                    binding.eventTime.text = "${e.date}, ${e.time}"
                    binding.statusTextView.text = if (rsvp.checked_in) "Status: Checked In" else "Status: Not checked in"
                    
                    val payload = QrCodeGenerator.generateSignedTicketPayload(rsvp.id, rsvp.event_id, rsvp.user_id)
                    val qrBitmap = QrCodeGenerator.generateQrCode(payload)
                    binding.qrImageView.setImageBitmap(qrBitmap)
                }
            }
        }
    }
}
