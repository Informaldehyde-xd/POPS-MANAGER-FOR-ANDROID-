package com.popsmanager.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Front cover art comes from xlenore/psx-covers on GitHub — a direct,
 * verified, single-file-per-serial URL pattern (the same source and same
 * author as the PS2 cover source already working in the OPL app; this is
 * also the exact source DuckStation's own built-in Cover Downloader uses).
 * Fast: one direct request, short timeout, no crawling or lookup needed.
 */
class CoverArtFetcher(private val context: Context) {

    companion object {
        private const val PSX_COVERS_BASE =
            "https://raw.githubusercontent.com/xlenore/psx-covers/main/covers/default/"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val artDir = File(context.filesDir, "cover_art").apply { mkdirs() }

    /** Normalizes a serial like "SLUS_014.76" to the covers repo's "SLUS-01476" form. */
    private fun toSerialFormat(gameId: String): String {
        val prefix = gameId.takeWhile { it.isLetter() }
        val digits = gameId.dropWhile { it.isLetter() }.filter { it.isDigit() }
        return "$prefix-$digits"
    }

    suspend fun fetchCover(gameId: String): String? = withContext(Dispatchers.IO) {
        val serial = toSerialFormat(gameId)
        val cached = File(artDir, "$serial.jpg")
        if (cached.exists()) return@withContext cached.absolutePath

        try {
            val request = Request.Builder().url("$PSX_COVERS_BASE$serial.jpg").build()
            client.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        cached.writeBytes(bytes)
                        return@withContext cached.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            // fall through to null
        }
        null
    }

    /** Copies a user-picked image in as the cover art, overriding whatever was auto-fetched. */
    suspend fun saveManualCover(gameId: String, sourceBytes: ByteArray, ext: String): String =
        withContext(Dispatchers.IO) {
            val serial = toSerialFormat(gameId)
            val file = File(artDir, "$serial.$ext")
            file.writeBytes(sourceBytes)
            file.absolutePath
        }
}
