package com.popsmanager.util

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Reads the real PS1 Game ID directly out of a raw BIN dump, given as a SAF
 * content:// Uri (since the source folder is picked via
 * ACTION_OPEN_DOCUMENT_TREE, not a raw filesystem path).
 *
 * PS1 discs carry the same idea as PS2's SYSTEM.CNF: a small boot config with
 * a line like "BOOT = cdrom:\SLUS_014.76;1". Rather than precisely parsing
 * the CD-ROM XA / Mode 2 Form 1 sector structure (2352 bytes/sector, 24-byte
 * header before the 2048 bytes of user data) — which varies slightly between
 * dumping tools and is easy to get subtly wrong — this scans the first few MB
 * of raw bytes directly for that boot line's distinctive text, the same
 * brute-force approach community PS1/POPS tools (and our own PS2 app's
 * fallback) use in practice. It's simpler and more tolerant of dump
 * variations than strict sector math.
 */
object Ps1IdReader {

    private const val SCAN_SIZE = 6 * 1024 * 1024 // first 6MB is always enough on a real PS1 disc

    fun readGameId(context: Context, binUri: Uri): String? {
        return try {
            context.contentResolver.openFileDescriptor(binUri, "r")?.use { pfd ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { stream ->
                    val bytes = ByteArray(SCAN_SIZE)
                    var totalRead = 0
                    while (totalRead < bytes.size) {
                        val n = stream.read(bytes, totalRead, bytes.size - totalRead)
                        if (n < 0) break
                        totalRead += n
                    }
                    val text = String(bytes, 0, totalRead, Charsets.US_ASCII)
                    GameIdUtil.extractGameId(text)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
