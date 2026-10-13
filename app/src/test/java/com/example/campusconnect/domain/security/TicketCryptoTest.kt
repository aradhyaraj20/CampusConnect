package com.example.campusconnect.domain.security

import org.junit.Assert.assertTrue
import org.junit.Test

class TicketCryptoTest {

    @Test
    fun validSignature_isAccepted() {
        val issued = 1_700_000_000_000L
        val expiry = issued + 86_400_000L
        val ticketId = TicketCrypto.ticketId(1, 10, 5)
        val sig = TicketCrypto.sign(ticketId, 10, 5, issued, expiry)
        val result = TicketCrypto.verify(TicketClaims(ticketId, 10, 5, issued, expiry, sig), nowMillis = issued + 1000)
        assertTrue(result is TicketCheck.Valid)
    }

    @Test
    fun tamperedSignature_isRejected() {
        val issued = 1_700_000_000_000L
        val expiry = issued + 86_400_000L
        val ticketId = TicketCrypto.ticketId(1, 10, 5)
        val result = TicketCrypto.verify(
            TicketClaims(ticketId, 10, 5, issued, expiry, "deadbeef"),
            nowMillis = issued + 1000
        )
        assertTrue(result is TicketCheck.Invalid)
        assertTrue((result as TicketCheck.Invalid).reason.contains("Forged") || result.reason.contains("tampered"))
    }

    @Test
    fun expiredTicket_isRejected() {
        val issued = 1_700_000_000_000L
        val expiry = issued + 1000L
        val ticketId = TicketCrypto.ticketId(2, 8, 3)
        val sig = TicketCrypto.sign(ticketId, 8, 3, issued, expiry)
        val result = TicketCrypto.verify(TicketClaims(ticketId, 8, 3, issued, expiry, sig), nowMillis = expiry + 1)
        assertTrue(result is TicketCheck.Invalid)
        assertTrue((result as TicketCheck.Invalid).reason.contains("expired", ignoreCase = true))
    }
}
