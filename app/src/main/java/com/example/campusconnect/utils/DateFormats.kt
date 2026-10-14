package com.example.campusconnect.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One place for the text timestamps stored in the database. Locale.US keeps the digits ASCII on
 * devices set to Arabic/Hindi/etc., so the stored strings stay sortable and comparable.
 * SimpleDateFormat isn't thread-safe, so a new instance is created per call.
 */
object DateFormats {
    fun storedTimestamp(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date)

    fun storedDay(date: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

    /** Event day label stored in `Event.date`, e.g. "Jun 10, 2026". Fixed locale so it is the same on every device. */
    fun eventDay(date: Date): String = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(date)

    /** Event start time stored in `Event.time`, 24h "HH:mm". */
    fun eventTime(date: Date): String = SimpleDateFormat("HH:mm", Locale.US).format(date)
}
