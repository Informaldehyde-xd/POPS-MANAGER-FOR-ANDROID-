package com.popsmanager.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.popsmanager.converter.Cue2PopsConverter
import com.popsmanager.popstarter.PopstarterInstaller
import com.popsmanager.util.Ps1IdReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GameRepository(private val context: Context) {

    private val converter = Cue2PopsConverter(context)
    private val installer = PopstarterInstaller(context)
    private val workDir = File(context.cacheDir, "pops_work").apply { mkdirs() }

    /** Recursively finds .cue files under the source tree, pairing each with its .bin. */
    suspend fun scanSourceFolder(treeUri: Uri): List<PopsGame> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val found = mutableListOf<PopsGame>()
        collect(root, found)
        found
    }

    private fun collect(dir: DocumentFile, out: MutableList<PopsGame>) {
        val children = dir.listFiles()
        val byBaseName = children.associateBy { it.name?.substringBeforeLast('.', "")?.lowercase() }

        for (child in children) {
            if (child.isDirectory) {
                collect(child, out)
                continue
            }
            val name = child.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext != "cue") continue

            val baseName = name.substringBeforeLast('.', "").lowercase()
            val binMatch = byBaseName[baseName]
                ?.takeIf { it.name?.substringAfterLast('.', "")?.lowercase() == "bin" }
                ?: children.firstOrNull { it.name?.endsWith(".bin", ignoreCase = true) == true }

            if (binMatch == null) continue // no paired BIN found, skip this cue

            val gameId = Ps1IdReader.readGameId(context, binMatch.uri)

            out.add(
                PopsGame(
                    cueDocumentId = child.uri.toString(),
                    cueDisplayName = name,
                    binUri = binMatch.uri,
                    gameId = gameId
                )
            )
        }
    }

    /**
     * Converts one game's CUE/BIN into a VCD and installs it (plus cover art, if provided)
     * onto the destination drive. Copies files through app-private cache since the native
     * converter needs real filesystem paths, not SAF content:// Uris.
     */
    suspend fun convertAndInstall(
        cueUri: Uri,
        cueName: String,
        binUri: Uri,
        gameId: String,
        title: String,
        destUri: Uri,
        localArtPath: String?
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val jobDir = File(workDir, gameId.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }
        try {
            val localCue = File(jobDir, cueName)
            val localBin = File(jobDir, cueName.substringBeforeLast('.', cueName) + ".bin")

            copyUriTo(cueUri, localCue) ?: return@withContext false to "Could not read the .cue file."
            copyUriTo(binUri, localBin) ?: return@withContext false to "Could not read the .bin file."

            // cue2pops reads the BIN filename referenced inside the .cue sheet — make sure
            // it matches what we actually copied, in case the source names didn't align.
            rewriteCueBinReference(localCue, localBin.name)

            val result = converter.convert(localCue, jobDir)
            if (!result.success || result.outputVcd == null) {
                return@withContext false to "Conversion failed: ${result.log.take(300)}"
            }

            val installResult = installer.installGame(destUri, result.outputVcd, gameId, title)
            if (!installResult.success) {
                return@withContext false to installResult.message
            }

            if (localArtPath != null) {
                installer.installCoverArt(destUri, gameId, localArtPath)
            }

            true to null
        } catch (e: Exception) {
            false to (e.message ?: e.javaClass.simpleName)
        } finally {
            jobDir.deleteRecursively()
        }
    }

    private fun copyUriTo(uri: Uri, dest: File): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            if (dest.exists()) dest else null
        } catch (e: Exception) {
            null
        }
    }

    /** Ensures the .cue file's FILE line points at the BIN we actually copied alongside it. */
    private fun rewriteCueBinReference(cueFile: File, binFileName: String) {
        try {
            val lines = cueFile.readLines()
            val rewritten = lines.map { line ->
                if (line.trim().startsWith("FILE", ignoreCase = true)) {
                    val suffix = line.trim().substringAfterLast('"', "").let {
                        line.trim().substringAfter('"').substringAfter('"')
                    }
                    "FILE \"$binFileName\"" + suffix
                } else line
            }
            cueFile.writeText(rewritten.joinToString("\n"))
        } catch (e: Exception) {
            // if this fails, conversion will just fail with a clear log message instead
        }
    }
}
