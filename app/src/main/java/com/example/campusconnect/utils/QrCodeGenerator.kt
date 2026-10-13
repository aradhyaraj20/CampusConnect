package com.example.campusconnect.utils

import android.graphics.Bitmap
import android.graphics.Color
import com.example.campusconnect.core.security.CryptoUtils
import com.example.campusconnect.domain.security.TicketClaims
import com.example.campusconnect.domain.security.TicketCheck
import com.example.campusconnect.domain.security.TicketCrypto
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import org.json.JSONObject

object QrCodeGenerator {

    fun generateSignedTicketPayload(rsvpId: Int, eventId: Int, userId: Int): String {
        val issuedAt = System.currentTimeMillis()
        val expiry = issuedAt + (7 * 24 * 3600 * 1000L) // 7 days valid
        val ticketId = TicketCrypto.ticketId(rsvpId, eventId, userId)
        val signature = TicketCrypto.sign(ticketId, eventId, userId, issuedAt, expiry)

        val json = JSONObject().apply {
            put("ticketId", ticketId)
            put("eventId", eventId)
            put("userId", userId)
            put("issuedAt", issuedAt)
            put("expiry", expiry)
            put("signature", signature)
        }

        return json.toString()
    }

    fun verifyTicketPayload(payloadJson: String): TicketVerificationResult {
        return try {
            val json = JSONObject(payloadJson)
            val ticketId = json.getString("ticketId")
            val eventId = json.getInt("eventId")
            val userId = json.getInt("userId")
            val issuedAt = json.getLong("issuedAt")
            val expiry = json.getLong("expiry")
            val signature = json.getString("signature")

            val check = TicketCrypto.verify(
                TicketClaims(ticketId, eventId, userId, issuedAt, expiry, signature)
            )
            when (check) {
                is TicketCheck.Valid -> TicketVerificationResult.Valid(ticketId, eventId, userId)
                is TicketCheck.Invalid -> TicketVerificationResult.Invalid(check.reason)
            }
        } catch (e: Exception) {
            TicketVerificationResult.Invalid("Invalid QR payload format")
        }
    }

    fun generateQrCode(text: String, size: Int = 512): Bitmap? {
        return try {
            val bitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: WriterException) {
            e.printStackTrace()
            null
        }
    }
}

sealed class TicketVerificationResult {
    data class Valid(val ticketId: String, val eventId: Int, val userId: Int) : TicketVerificationResult()
    data class Invalid(val reason: String) : TicketVerificationResult()
}
