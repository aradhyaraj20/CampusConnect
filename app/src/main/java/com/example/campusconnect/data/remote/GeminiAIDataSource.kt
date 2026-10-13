package com.example.campusconnect.data.remote

import com.example.campusconnect.core.ai.AIConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class GeminiAIDataSource(
    private val apiKeyProvider: () -> String = { AIConfig.apiKey },
    private val modelProvider: () -> String = { AIConfig.model }
) : AIDataSource {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")

    override suspend fun extractEventDraft(
        conversation: List<Pair<Boolean, String>>,
        pendingField: String?
    ): EventDraftExtraction {
        val today = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US).apply { timeZone = ist }
            .format(Calendar.getInstance(ist).time)
        val transcript = conversation.joinToString("\n") { (isUser, text) ->
            if (isUser) "Organizer: $text" else "Assistant: $text"
        }
        val prompt = """
            You extract campus event fields from an organizer conversation.
            Today is $today (Asia/Kolkata). Pending field: ${pendingField ?: "none"}.
            Return ONLY compact JSON with keys:
            title, date (YYYY-MM-DD or null), time (HH:mm 24h or null), location,
            capacity (int or null), eligibility, category, tags (array), description.
            Never invent date, time, location or capacity. Use null when unknown.
            Conversation:
            $transcript
        """.trimIndent()
        val raw = generate(prompt) ?: throw IllegalStateException("Empty Gemini response")
        return parseDraft(raw)
    }

    override suspend fun answerWithFacts(systemPrompt: String, userQuery: String, facts: String): String? {
        return generate("$systemPrompt\n\nApplication facts:\n$facts\n\nUser question:\n$userQuery")
    }

    private fun generate(prompt: String): String? {
        val key = apiKeyProvider()
        if (key.isBlank()) throw IllegalStateException("Gemini API key missing")
        val model = modelProvider().ifBlank { AIConfig.DEFAULT_MODEL }
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 1024))
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
            if (conn.responseCode !in 200..299) throw IllegalStateException("Gemini HTTP ${conn.responseCode}")
            return JSONObject(response)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
                .getJSONObject(0).optString("text").takeIf { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }

    private fun parseDraft(raw: String): EventDraftExtraction {
        val json = JSONObject(raw.substringAfter("{").substringBeforeLast("}").let { "{$it}" })
        val parsedDate = json.optString("date").takeIf { it.isNotBlank() && it != "null" }?.let { parseIso(it) }
        val tags = json.optJSONArray("tags")
        val tagList = mutableListOf<String>()
        if (tags != null) for (i in 0 until tags.length()) tagList.add(tags.optString(i))
        val capacity = if (json.has("capacity") && !json.isNull("capacity")) json.optInt("capacity") else null
        return EventDraftExtraction(
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
            dateDisplay = parsedDate?.first,
            dateTimestamp = parsedDate?.second ?: 0L,
            dateFound = parsedDate != null,
            time = json.optString("time").takeIf { it.matches(Regex("\\d{2}:\\d{2}")) },
            location = json.optString("location").takeIf { it.isNotBlank() && it != "null" },
            capacity = capacity?.takeIf { it > 0 },
            eligibility = json.optString("eligibility").takeIf { it.isNotBlank() && it != "null" },
            category = json.optString("category").takeIf { it.isNotBlank() && it != "null" },
            tags = tagList.joinToString(", ").ifBlank { null },
            description = json.optString("description").takeIf { it.isNotBlank() && it != "null" },
            source = "gemini"
        )
    }

    private fun parseIso(iso: String): Pair<String, Long>? = try {
        val parts = iso.split("-")
        if (parts.size != 3) null
        else {
            val cal = Calendar.getInstance(ist)
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply { timeZone = ist }
            Pair(sdf.format(cal.time), cal.timeInMillis)
        }
    } catch (_: Exception) { null }
}
