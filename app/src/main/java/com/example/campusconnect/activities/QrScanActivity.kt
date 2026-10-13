package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.core.security.SessionManager
import com.example.campusconnect.domain.security.AccessPolicy
import com.example.campusconnect.database.AppDatabase
import com.example.campusconnect.models.Attendance
import com.example.campusconnect.utils.QrCodeGenerator
import com.example.campusconnect.utils.TicketVerificationResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class QrScanActivity : AppCompatActivity() {

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Cancelled", Toast.LENGTH_LONG).show()
            finish()
        } else {
            handleScanResult(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val role = SessionManager.getInstance(this).getCurrentUserRole()
        if (!AccessPolicy.canScanAttendance(role)) {
            Toast.makeText(this, "Organizer access required to scan attendance", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Point at attendee's QR code")
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        val currentScannerUserId = SessionManager.getInstance(this).getCurrentUserId()

        // 1. Try Signed Payload Verification
        val verification = QrCodeGenerator.verifyTicketPayload(contents)
        if (verification is TicketVerificationResult.Valid) {
            processCheckIn(
                ticketId = verification.ticketId,
                eventId = verification.eventId,
                userId = verification.userId,
                scannerUserId = currentScannerUserId
            )
            return
        }

        // 2. Fallback to Legacy Raw Prefix
        if (contents.startsWith("CAMPUS_CONNECT_RSVP_")) {
            val rsvpId = contents.removePrefix("CAMPUS_CONNECT_RSVP_").toIntOrNull()
            if (rsvpId != null) {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@QrScanActivity)
                    val rsvp = db.rsvpDao().getRsvpById(rsvpId)
                    if (rsvp != null) {
                        processCheckIn(
                            ticketId = "TICK_${rsvp.id}_${rsvp.event_id}_${rsvp.user_id}",
                            eventId = rsvp.event_id,
                            userId = rsvp.user_id,
                            scannerUserId = currentScannerUserId
                        )
                    } else {
                        Toast.makeText(this@QrScanActivity, "Invalid Ticket ID", Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                return
            }
        }

        // 3. Invalid QR Ticket
        val reason = (verification as? TicketVerificationResult.Invalid)?.reason ?: "Invalid QR Code"
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun processCheckIn(ticketId: String, eventId: Int, userId: Int, scannerUserId: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(this@QrScanActivity)

            // Check if already checked in
            val existingAttendance = db.attendanceDao().getAttendanceForUser(eventId, userId)
            if (existingAttendance != null) {
                Toast.makeText(this@QrScanActivity, "Attendee ALREADY Checked In!", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            // Record attendance
            val attendance = Attendance(
                eventId = eventId,
                userId = userId,
                ticketId = ticketId,
                checkInTime = System.currentTimeMillis(),
                checkedInBy = scannerUserId,
                status = "CHECKED_IN"
            )
            db.attendanceDao().recordAttendance(attendance)

            // Update RSVP checked_in state
            val rsvp = db.rsvpDao().getUserRSVPForEvent(userId, eventId)
            if (rsvp != null) {
                db.rsvpDao().updateRsvp(rsvp.copy(checked_in = true))
            }

            Toast.makeText(this@QrScanActivity, "Check-in Successful! 🎉", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
