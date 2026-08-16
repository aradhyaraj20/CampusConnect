package com.example.campusconnect.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.campusconnect.database.AppDatabase
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
        
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        options.setPrompt("Point at attendee's QR code")
        options.setBeepEnabled(true)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    private fun handleScanResult(contents: String) {
        if (contents.startsWith("CAMPUS_CONNECT_RSVP_")) {
            val rsvpId = contents.removePrefix("CAMPUS_CONNECT_RSVP_").toIntOrNull()
            if (rsvpId != null) {
                lifecycleScope.launch {
                    val db = AppDatabase.getDatabase(this@QrScanActivity)
                    val rsvp = db.rsvpDao().getRsvpById(rsvpId)
                    if (rsvp != null) {
                        // Mark as checked in
                        val updatedRsvp = rsvp.copy(checked_in = true)
                        db.rsvpDao().updateRsvp(updatedRsvp)
                        Toast.makeText(this@QrScanActivity, "Check-in successful!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@QrScanActivity, "Invalid Ticket", Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            }
        } else {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
