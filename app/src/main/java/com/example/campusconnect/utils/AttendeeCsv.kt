package com.example.campusconnect.utils

import com.example.campusconnect.models.User

/** Builds the attendee export an organizer can open in Excel/Sheets. Pure, so it is unit-tested. */
object AttendeeCsv {
    private const val HEADER = "Name,Email,Course"
    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')

    fun build(attendees: List<User>): String =
        (listOf(HEADER) + attendees.map { row(it) }).joinToString("\r\n", postfix = "\r\n")

    private fun row(user: User): String =
        listOf(user.name, user.email, user.course.orEmpty()).joinToString(",") { escape(it) }

    /** RFC 4180 quoting, plus a leading quote on cells a spreadsheet would evaluate as a formula. */
    internal fun escape(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_PREFIXES) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
