package com.example.campusconnect.domain.event

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class EventTimeTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    private fun istMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(ist).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun midnightDate_getsTheParsedStartTime() {
        val midnight = istMillis(2026, Calendar.OCTOBER, 20, 0, 0)
        assertEquals(istMillis(2026, Calendar.OCTOBER, 20, 18, 0), EventTime.combine(midnight, "18:00", ist))
    }

    @Test
    fun dateWithClockTime_isReplacedNotShifted() {
        val withNoise = istMillis(2026, Calendar.OCTOBER, 20, 11, 37)
        assertEquals(istMillis(2026, Calendar.OCTOBER, 20, 9, 5), EventTime.combine(withNoise, "09:05", ist))
    }

    @Test
    fun missingOrInvalidPieces_leaveTimestampUnchanged() {
        val day = istMillis(2026, Calendar.OCTOBER, 20, 0, 0)
        assertEquals(day, EventTime.combine(day, null, ist))
        assertEquals(day, EventTime.combine(day, "6pm", ist))
        assertEquals(day, EventTime.combine(day, "25:00", ist))
        assertEquals(0L, EventTime.combine(0L, "18:00", ist))
    }
}
