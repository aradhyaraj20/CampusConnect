package com.example.campusconnect.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies a picked image into app-private storage. A content:// URI from the photo picker only
 * works while the picking app still holds a grant, which is usually gone after a restart, so
 * keeping our own copy is the only reliable way to show it later.
 */
object ImageStore {
    const val EVENT_COVERS = "event_covers"
    const val RECAPS = "recaps"
    private const val MAX_BYTES = 15L * 1024 * 1024

    /** Returns a file:// URI string for the copy, or null if the image could not be read or is too large. */
    suspend fun import(context: Context, source: Uri, folder: String): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val ext = when (resolver.getType(source)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            else -> "jpg"
        }
        val dir = File(context.filesDir, folder).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.$ext")
        val copied = runCatching {
            resolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_BYTES) return@use false
                        output.write(buffer, 0, read)
                    }
                    true
                }
            } ?: false
        }.getOrDefault(false)
        if (!copied) {
            target.delete()
            return@withContext null
        }
        Uri.fromFile(target).toString()
    }

    /** Deletes a copy made by [import]; anything outside our files dir (seed URLs, etc.) is left alone. */
    suspend fun delete(context: Context, uri: String?) = withContext(Dispatchers.IO) {
        val path = uri?.let { Uri.parse(it) }?.takeIf { it.scheme == "file" }?.path ?: return@withContext
        val file = File(path)
        if (file.canonicalPath.startsWith(context.filesDir.canonicalPath + File.separator)) file.delete()
    }
}
