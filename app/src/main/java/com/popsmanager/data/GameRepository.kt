package com.popsmanager.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.popsmanager.converter.Cue2PopsConverter
import com.popsmanager.popstarter.PopstarterInstaller
import com.popsmanager.util.Ps1IdReader
import com.popsmanager.util.VcdExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One .VCD file found while scanning a folder for reverse conversion. */
data class VcdEntry(
    val documentId: String,  // SAF uri string of the .vcd file
    val displayName: String
)

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
                return@withContext false to "Conversion failed: ${result.log.take(2000)}"
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

    /** Recursively finds .VCD files under a folder, for reverse conversion back to BIN/CUE. */
    suspend fun scanVcdFolder(treeUri: Uri): List<VcdEntry> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        val found = mutableListOf<VcdEntry>()
        collectVcds(root, found)
        found
    }

    private fun collectVcds(dir: DocumentFile, out: MutableList<VcdEntry>) {
        for (child in dir.listFiles()) {
            if (child.isDirectory) {
                collectVcds(child, out)
                continue
            }
            val name = child.name ?: continue
            if (name.endsWith(".vcd", ignoreCase = true)) {
                out.add(VcdEntry(documentId = child.uri.toString(), displayName = name))
            }
        }
    }

    /**
     * Converts a .VCD back to .bin/.cue and copies both onto the destination folder.
     * Parsed and rebuilt entirely in Kotlin (see VcdExtractor) rather than via a
     * native tool — the previously-used native pops2cue had a real, unresolved
     * compatibility bug with our verified-correct VCD files.
     */
    suspend fun convertVcdToBinCue(
        vcdUri: Uri,
        vcdName: String,
        destUri: Uri
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val jobDir = File(workDir, "reverse_" + vcdName.replace(Regex("[^A-Za-z0-9_-]"), "_")).apply { mkdirs() }
        try {
            val localVcd = File(jobDir, vcdName)
            copyUriTo(vcdUri, localVcd) ?: return@withContext false to "Could not read the .VCD file."

            val info = VcdExtractor.parseVcdHeader(localVcd)
            if (!info.isValid) {
                return@withContext false to "Invalid VCD:\n${info.errors.joinToString("\n")}"
            }

            val binFileName = vcdName.substringBeforeLast('.', vcdName) + ".bin"
            val localBin = File(jobDir, binFileName)
            VcdExtractor.extractBinData(localVcd, localBin)

            val cueSheet = VcdExtractor.generateCueSheet(info, binFileName)
            val localCue = File(jobDir, vcdName.substringBeforeLast('.', vcdName) + ".cue")
            localCue.writeText(cueSheet)

            val destRoot = DocumentFile.fromTreeUri(context, destUri)
                ?: return@withContext false to "Could not open destination folder."

            fun copyOut(file: File): Boolean {
                destRoot.findFile(file.name)?.delete()
                val target = destRoot.createFile("application/octet-stream", file.name) ?: return false
                context.contentResolver.openOutputStream(target.uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                }
                return true
            }

            if (!copyOut(localCue)) return@withContext false to "Could not write the .cue file to the destination."
            if (!copyOut(localBin)) return@withContext false to "Could not write the .bin file to the destination."

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
