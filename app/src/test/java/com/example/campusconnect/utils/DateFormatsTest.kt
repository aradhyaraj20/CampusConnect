package com.example.campusconnect.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateFormatsTest {

    @Test
    fun eventStrings_areLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            val date = Date(Calendar.getInstance().apply { set(2026, Calendar.JUNE, 10, 9, 5, 0) }.timeInMillis)
            Locale.setDefault(Locale.forLanguageTag("hi-IN-u-nu-deva"))
            assertEquals("Jun 10, 2026", DateFormats.eventDay(date))
            assertEquals("09:05", DateFormats.eventTime(date))
            assertEquals(19, DateFormats.storedTimestamp(date).length)
        } finally {
            Locale.setDefault(original)
        }
    }
}
