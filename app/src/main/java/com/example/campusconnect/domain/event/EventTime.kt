package com.example.campusconnect.domain.event

import java.util.Calendar
import java.util.TimeZone

/** Joins a parsed calendar day and an "HH:mm" start time into one timestamp. */
object EventTime {

    private val CAMPUS_TZ: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val HH_MM = Regex("^(\\d{1,2}):(\\d{2})$")

    /**
     * Returns [dayMillis] moved to [time] on the same campus-local day, or [dayMillis]
     * unchanged when either part is missing or the time isn't a valid HH:mm.
     */
    fun combine(dayMillis: Long, time: String?, timeZone: TimeZone = CAMPUS_TZ): Long {
        if (dayMillis <= 0 || time.isNullOrBlank()) return dayMillis
        val match = HH_MM.matchEntire(time.trim()) ?: return dayMillis
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return dayMillis
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = dayMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
