package com.example.campusconnect.utils

import com.example.campusconnect.models.User
import org.junit.Assert.assertEquals
import org.junit.Test

class AttendeeCsvTest {
    private fun user(name: String, email: String, course: String? = null) =
        User(name = name, email = email, password_hash = "x", created_at = "2026-01-01", course = course)

    @Test
    fun emptyList_isHeaderOnly() {
        assertEquals("Name,Email,Course\r\n", AttendeeCsv.build(emptyList()))
    }

    @Test
    fun rowsAreWritten_withBlankCourseWhenMissing() {
        val csv = AttendeeCsv.build(listOf(user("Aradhya", "a@email.com", "BCA"), user("Ravi", "r@email.com")))
        assertEquals("Name,Email,Course\r\nAradhya,a@email.com,BCA\r\nRavi,r@email.com,\r\n", csv)
    }

    @Test
    fun commasAndQuotesAreEscaped() {
        assertEquals("\"Doe, Jane\"", AttendeeCsv.escape("Doe, Jane"))
        assertEquals("\"say \"\"hi\"\"\"", AttendeeCsv.escape("say \"hi\""))
    }

    @Test
    fun formulaCellsAreNeutralised() {
        assertEquals("'=SUM(A1)", AttendeeCsv.escape("=SUM(A1)"))
        assertEquals("'@cmd", AttendeeCsv.escape("@cmd"))
    }
}
