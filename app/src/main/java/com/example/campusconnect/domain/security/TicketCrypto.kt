package com.example.campusconnect.domain.security

import com.example.campusconnect.core.security.CryptoUtils

data class TicketClaims(
    val ticketId: String,
    val eventId: Int,
    val userId: Int,
    val issuedAt: Long,
    val expiry: Long,
    val signature: String
)

sealed class TicketCheck {
    data class Valid(val claims: TicketClaims) : TicketCheck()
    data class Invalid(val reason: String) : TicketCheck()
}

object TicketCrypto {

    fun ticketId(rsvpId: Int, eventId: Int, userId: Int): String = "TICK_${rsvpId}_${eventId}_$userId"

    fun canonical(ticketId: String, eventId: Int, userId: Int, issuedAt: Long, expiry: Long): String =
        "$ticketId:$eventId:$userId:$issuedAt:$expiry"

    fun sign(ticketId: String, eventId: Int, userId: Int, issuedAt: Long, expiry: Long): String =
        CryptoUtils.generateHmacSignature(canonical(ticketId, eventId, userId, issuedAt, expiry))

    fun verify(claims: TicketClaims, nowMillis: Long = System.currentTimeMillis()): TicketCheck {
        val expected = sign(claims.ticketId, claims.eventId, claims.userId, claims.issuedAt, claims.expiry)
        if (!CryptoUtils.verifyHmacSignature(
                canonical(claims.ticketId, claims.eventId, claims.userId, claims.issuedAt, claims.expiry),
                claims.signature
            ) || expected != claims.signature
        ) {
            return TicketCheck.Invalid("Forged or tampered QR ticket")
        }
        if (nowMillis > claims.expiry) {
            return TicketCheck.Invalid("QR Ticket has expired")
        }
        return TicketCheck.Valid(claims)
    }
}
