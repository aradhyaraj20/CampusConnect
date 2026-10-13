package com.example.campusconnect.data.remote

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/** Offline extractor. Never invents date, time, location or capacity. Uses Asia/Kolkata. */
object LocalEventParser {

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    fun extract(prompt: String, pendingField: String?): EventDraftExtraction {
        val lower = prompt.lowercase(Locale.ROOT).trim()
        val date = extractDate(lower)
        return EventDraftExtraction(
            title = extractTitle(prompt, pendingField),
            dateDisplay = date?.first,
            dateTimestamp = date?.second ?: 0L,
            dateFound = date != null,
            time = extractTime(lower),
            location = extractLocation(prompt, pendingField),
            capacity = extractCapacity(lower),
            eligibility = extractEligibility(lower),
            category = determineCategory(lower),
            tags = extractTags(lower),
            description = prompt.takeIf { it.isNotBlank() },
            source = "local"
        )
    }

    private fun extractTitle(prompt: String, pendingField: String?): String? {
        val lower = prompt.trim()
        val explicit = listOf(
            "(?:the )?event (?:name|title) is (.+)",
            "(?:name|title) is (.+)",
            "call it (.+)",
            "title:?\\s+(.+)",
            "organize an? (.+?) for",
            "organise an? (.+?) for",
            "conduct an? (.+?) for",
            "create an? (.+?) for",
            "schedule an? (.+?) for",
            "want to (?:organize|organise|conduct|create|host) an? (.+?) for",
            "want to (?:organize|organise|conduct|create|host) an? (.+)"
        )
        for (p in explicit) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(lower)
            if (matcher.find()) {
                val extracted = matcher.group(1)?.trim()?.trimEnd('.', ',')
                if (!extracted.isNullOrBlank() && extracted.length in 3..80) {
                    return extracted.replaceFirstChar { it.uppercase() }
                }
            }
        }
        if (pendingField == "title" && lower.isNotBlank() && lower.length <= 80) {
            val cleaned = lower.replace(Regex("^(the|an|a)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (cleaned.isNotBlank()) return cleaned.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun extractDate(prompt: String): Pair<String, Long>? {
        val calendar = Calendar.getInstance(IST)
        var found = false
        when {
            prompt.contains("day after tomorrow") -> { calendar.add(Calendar.DAY_OF_YEAR, 2); found = true }
            prompt.contains("tomorrow") -> { calendar.add(Calendar.DAY_OF_YEAR, 1); found = true }
            prompt.contains("today") -> found = true
            prompt.contains("next week") -> { calendar.add(Calendar.DAY_OF_YEAR, 7); found = true }
            prompt.contains("this weekend") || prompt.contains("on weekend") -> {
                while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY) calendar.add(Calendar.DAY_OF_YEAR, 1)
                found = true
            }
            else -> {
                val days = listOf(
                    "friday" to Calendar.FRIDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
                    "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
                    "saturday" to Calendar.SATURDAY, "sunday" to Calendar.SUNDAY
                )
                for ((name, const) in days) {
                    if (Regex("\\b$name\\b").containsMatchIn(prompt)) {
                        do { calendar.add(Calendar.DAY_OF_YEAR, 1) } while (calendar.get(Calendar.DAY_OF_WEEK) != const)
                        found = true
                        break
                    }
                }
            }
        }
        val monthPattern = Pattern.compile(
            "(january|february|march|april|may|june|july|august|september|october|november|december)\\s+(\\d{1,2})",
            Pattern.CASE_INSENSITIVE
        )
        val matcher = monthPattern.matcher(prompt)
        if (matcher.find()) {
            calendar.set(Calendar.MONTH, monthIndex(matcher.group(1)))
            calendar.set(Calendar.DAY_OF_MONTH, matcher.group(2)?.toInt() ?: 1)
            found = true
        }
        return if (found) format(calendar) else null
    }

    private fun format(calendar: Calendar): Pair<String, Long> {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        sdf.timeZone = IST
        return Pair(sdf.format(calendar.time), calendar.timeInMillis)
    }

    private fun monthIndex(month: String?): Int = when (month?.lowercase(Locale.ROOT)) {
        "january" -> Calendar.JANUARY; "february" -> Calendar.FEBRUARY; "march" -> Calendar.MARCH
        "april" -> Calendar.APRIL; "may" -> Calendar.MAY; "june" -> Calendar.JUNE
        "july" -> Calendar.JULY; "august" -> Calendar.AUGUST; "september" -> Calendar.SEPTEMBER
        "october" -> Calendar.OCTOBER; "november" -> Calendar.NOVEMBER; "december" -> Calendar.DECEMBER
        else -> Calendar.JANUARY
    }

    private fun extractTime(prompt: String): String? {
        val withMeridiem = Pattern.compile("(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)", Pattern.CASE_INSENSITIVE)
        val m1 = withMeridiem.matcher(prompt)
        if (m1.find()) {
            var hour = m1.group(1)?.toIntOrNull() ?: return null
            val min = m1.group(2) ?: "00"
            val amPm = m1.group(3)?.lowercase(Locale.ROOT).orEmpty()
            if (amPm.startsWith("p") && hour < 12) hour += 12
            if (amPm.startsWith("a") && hour == 12) hour = 0
            if (hour in 0..23) return String.format(Locale.US, "%02d:%s", hour, min)
        }
        val twentyFour = Pattern.compile("\\bat\\s+(\\d{1,2}):(\\d{2})\\b", Pattern.CASE_INSENSITIVE)
        val m2 = twentyFour.matcher(prompt)
        if (m2.find()) {
            val hour = m2.group(1)?.toIntOrNull() ?: return null
            val min = m2.group(2) ?: "00"
            if (hour in 0..23) return String.format(Locale.US, "%02d:%s", hour, min)
        }
        return null
    }

    private fun extractLocation(prompt: String, pendingField: String?): String? {
        val patterns = listOf(
            "(?:at|in)\\s+(seminar hall[^,.]*)",
            "(?:at|in)\\s+(lab[^,.]*)",
            "(?:at|in)\\s+(auditorium[^,.]*)",
            "(?:at|in)\\s+(block[^,.]*)",
            "(?:location|venue)(?: is|:)?\\s+(.+)",
            "take place(?:s)? (?:at|in) (.+)",
            "held (?:at|in) (.+)"
        )
        for (p in patterns) {
            val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(prompt)
            if (matcher.find()) {
                val extracted = matcher.group(1)?.trim()?.trimEnd('.', ',')
                if (!extracted.isNullOrBlank() && extracted.length <= 80) {
                    return extracted.replaceFirstChar { it.uppercase() }
                }
            }
        }
        if (pendingField == "location" && prompt.isNotBlank() && prompt.length <= 80) {
            val cleaned = prompt.trim().replace(Regex("^(in|at|the|venue|location)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (cleaned.isNotBlank()) return cleaned.replaceFirstChar { it.uppercase() }
        }
        return null
    }

    private fun extractCapacity(prompt: String): Int? {
        val patterns = listOf(
            Pattern.compile("(?:for|capacity(?: of)?|upto|up to)\\s+(\\d{1,4})\\s*(?:students|people|members|seats)?"),
            Pattern.compile("(\\d{1,4})\\s*(?:students|people|members|seats)")
        )
        for (p in patterns) {
            val matcher = p.matcher(prompt)
            if (matcher.find()) {
                val n = matcher.group(1)?.toIntOrNull() ?: continue
                if (n in 1..5000) return n
            }
        }
        return null
    }

    private fun extractEligibility(prompt: String): String? {
        val programs = listOf("mca", "bca", "btech", "mtech", "mba", "bba", "bsc", "msc", "freshers", "final year")
        for (p in programs) {
            if (prompt.contains(p)) {
                return p.uppercase(Locale.getDefault()) + if (p.contains("year") || p == "freshers") "" else " Students"
            }
        }
        return null
    }

    private fun extractTags(prompt: String): String? {
        val tags = mutableListOf<String>()
        val dictionary = mapOf(
            "python" to "Python", "java" to "Java", "coding" to "Coding", "programming" to "Programming",
            "workshop" to "Workshop", "hackathon" to "Hackathon", "ml" to "AI/ML",
            "machine learning" to "AI/ML", "ai" to "AI", "football" to "Football",
            "sports" to "Sports", "dbms" to "DBMS", "database" to "Database"
        )
        dictionary.forEach { (k, v) -> if (prompt.contains(k) && v !in tags) tags.add(v) }
        return tags.take(6).joinToString(", ").ifBlank { null }
    }

    private fun determineCategory(prompt: String): String? = when {
        listOf("workshop", "hackathon", "coding", "python", "ai").any { prompt.contains(it) } -> "Technical"
        prompt.contains("party") || prompt.contains("social") -> "Social"
        prompt.contains("study") || prompt.contains("exam") || prompt.contains("dbms") -> "Academic"
        prompt.contains("sports") || prompt.contains("match") || prompt.contains("football") -> "Sports"
        prompt.contains("cultural") || prompt.contains("music") || prompt.contains("dance") -> "Cultural"
        else -> null
    }
}
