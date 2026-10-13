package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.R
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.domain.security.TicketCrypto
import com.example.campusconnect.models.Attendance
import com.example.campusconnect.utils.QrCodeGenerator
import com.example.campusconnect.utils.TicketVerificationResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * Organizer/admin ticket scanner. Every scan is checked against the database, not just
 * the signature: the scanner must own the event (or be an admin), the event must still
 * be active, and the attendee must still hold a "going" RSVP matching the ticket.
 * When opened from an event (EXTRA_EVENT_ID), tickets for other events are rejected.
 */
class QrScanActivity : AppCompatActivity() {

    private var expectedEventId: Int = -1
    private var scannerUserId: Int = -1

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            finish()
        } else {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        expectedEventId = intent.getIntExtra(EXTRA_EVENT_ID, -1)
        scannerUserId = SessionManager.getInstance(this).getCurrentUserId()

        lifecycleScope.launch {
            // Role comes from the database, not the cached session value.
            val role = AppDatabase.getDatabase(this@QrScanActivity).userDao().getUserById(scannerUserId)?.role
            if (!AccessPolicy.canScanAttendance(role)) {
                finishWith(R.string.scan_requires_organizer)
                return@launch
            }
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt(getString(R.string.scan_prompt))
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            barcodeLauncher.launch(options)
        }
    }

    private fun handleScanResult(contents: String) {
        when (val verification = QrCodeGenerator.verifyTicketPayload(contents)) {
            is TicketVerificationResult.Valid -> processCheckIn(verification.ticketId, verification.eventId, verification.userId)
            is TicketVerificationResult.Invalid -> {
                Toast.makeText(this, verification.reason, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun processCheckIn(ticketId: String, eventId: Int, userId: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@QrScanActivity)

            if (expectedEventId != -1 && eventId != expectedEventId) {
                finishWith(R.string.scan_wrong_event); return@launch
            }
            val event = db.eventDao().getEventById(eventId)
            if (event == null || !event.status.equals("active", ignoreCase = true)) {
                finishWith(R.string.scan_event_inactive); return@launch
            }
            val scannerRole = db.userDao().getUserById(scannerUserId)?.role
            if (event.creator_id != scannerUserId && !AccessPolicy.canAccessAdmin(scannerRole)) {
                finishWith(R.string.scan_not_your_event); return@launch
            }
            val rsvp = db.rsvpDao().getUserRSVPForEvent(userId, eventId)
            if (rsvp == null || rsvp.status != "going" || TicketCrypto.ticketId(rsvp.id, eventId, userId) != ticketId) {
                finishWith(R.string.scan_no_valid_rsvp); return@launch
            }

            val inserted = db.attendanceDao().recordAttendance(
                Attendance(
                    eventId = eventId,
                    userId = userId,
                    ticketId = ticketId,
                    checkInTime = System.currentTimeMillis(),
                    checkedInBy = scannerUserId,
                    status = "CHECKED_IN"
                )
            )
            if (inserted == -1L) {
                finishWith(R.string.scan_already_checked_in); return@launch
            }
            db.rsvpDao().updateRsvp(rsvp.copy(checked_in = true))
            finishWith(R.string.scan_success)
        }
    }

    private fun finishWith(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        const val EXTRA_EVENT_ID = "EVENT_ID"
    }
}
