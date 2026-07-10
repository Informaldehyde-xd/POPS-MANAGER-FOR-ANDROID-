package com.popsmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Title lookup backed by libretro's own PS1 serial/title list (dat/ps1.idlst),
 * a plain text file of lines like: SLUS-01476 "Capcom vs. SNK - ..." — free,
 * no signup, no API key, hosted directly on GitHub.
 */
class TitleDatabase(private val context: Context) {

    companion object {
        private const val IDLST_URL =
            "https://raw.githubusercontent.com/libretro/libretro-database/master/dat/ps1.idlst"
        private const val CACHE_FILENAME = "ps1_idlst_cache.txt"
        private val LINE_REGEX = Regex("^(\\S+)\\s+\"(.+)\"\\s*$")
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private var idToTitle: Map<String, String> = emptyMap()
    private var loaded = false

    val entryCount: Int get() = idToTitle.size

    var lastError: String? = null
        private set

    suspend fun ensureLoaded() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            val cacheFile = File(context.filesDir, CACHE_FILENAME)
            var text = ""
            var succeeded = false

            try {
                val request = Request.Builder().url(IDLST_URL).build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            text = body
                            succeeded = true
                        }
                    } else {
                        lastError = "Server returned ${resp.code}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message
            }

            if (succeeded) {
                cacheFile.writeText(text)
            } else if (cacheFile.exists()) {
                text = cacheFile.readText()
                succeeded = text.isNotBlank()
            } else {
                lastError = lastError ?: "Could not reach the online title database."
            }

            idToTitle = parseIdlst(text)
            if (succeeded && idToTitle.isEmpty()) {
                lastError = "Connected, but couldn't parse any entries (format may have changed)."
            }
            loaded = true
        }
    }

    /** Normalizes a serial like "SLUS_014.76" or "SLUS-01476" to the idlst's "SLUS-01476" form. */
    private fun normalize(gameId: String): String {
        val prefix = gameId.takeWhile { it.isLetter() }
        val digits = gameId.dropWhile { it.isLetter() }.filter { it.isDigit() }
        return "$prefix-$digits"
    }

    fun lookupTitle(gameId: String): String? = idToTitle[normalize(gameId).uppercase()]

    fun searchTitles(query: String, limit: Int = 20): List<Pair<String, String>> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return idToTitle.entries
            .asSequence()
            .filter { it.value.lowercase().contains(q) }
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }

    private fun parseIdlst(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        val map = HashMap<String, String>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach
            val match = LINE_REGEX.find(line) ?: return@forEach
            val id = match.groupValues[1].trim().uppercase()
            val title = match.groupValues[2].trim()
            if (id.isNotEmpty() && title.isNotEmpty()) {
                // Keep the first entry per serial (idlst can list disc variants; first is fine for our purposes)
                map.putIfAbsent(id, title)
            }
        }
        return map
    }
}
