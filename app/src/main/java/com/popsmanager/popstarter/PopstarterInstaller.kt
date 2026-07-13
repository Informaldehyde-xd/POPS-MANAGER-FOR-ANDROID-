package com.popsmanager.popstarter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.popsmanager.util.GameIdUtil
import com.popsmanager.util.ImageFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Installs a converted game into a USB drive's /POPS directory, following
 * the standard POPStarter layout used by OPL / FMCB:
 *
 *   USB:/POPS/POPSTARTER.ELF        (user-supplied — not redistributable by us)
 *   USB:/POPS/POPS_IOX.PAK          (same as above)
 *   USB:/POPS/SLUS-01234.Crash Bandicoot.VCD
 *   USB:/POPS/ART/SLUS-01234_COV.jpg
 *
 * The target is a USB drive, so this goes through Storage Access Framework
 * (DocumentFile / persisted Uri permission) rather than raw java.io.File
 * paths — the user picks the USB root once via ACTION_OPEN_DOCUMENT_TREE.
 */
class PopstarterInstaller(private val context: Context) {

    data class InstallResult(val success: Boolean, val message: String)

    /**
     * @param usbRootUri  Uri returned from ACTION_OPEN_DOCUMENT_TREE, pointing
     *                    at the root of the USB drive (or wherever /POPS should live)
     * @param vcdFile     the converted .VCD produced by Cue2PopsConverter
     * @param gameId      detected serial, e.g. "SLUS-01234"
     * @param title       cleaned-up display title, e.g. "Crash Bandicoot"
     */
    suspend fun installGame(
        usbRootUri: Uri,
        vcdFile: File,
        gameId: String,
        title: String
    ): InstallResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, usbRootUri)
            ?: return@withContext InstallResult(false, "Could not open USB root — re-pick the folder.")

        val popsDir = root.findFile("POPS") ?: root.createDirectory("POPS")
            ?: return@withContext InstallResult(false, "Could not create /POPS directory.")

        val targetName = GameIdUtil.buildVcdFilename(gameId, title)

        popsDir.findFile(targetName)?.delete()
        val targetFile = popsDir.createFile("application/octet-stream", targetName)
            ?: return@withContext InstallResult(false, "Could not create $targetName on the drive.")

        try {
            context.contentResolver.openOutputStream(targetFile.uri)?.use { out ->
                vcdFile.inputStream().use { it.copyTo(out) }
            }
            InstallResult(true, "Installed $targetName")
        } catch (e: Exception) {
            InstallResult(false, "Copy failed: ${e.message}")
        }
    }

    /** Saves cover art into /POPS/ART on the drive, normalized to a 200x200 8-bit PNG. */
    suspend fun installCoverArt(usbRootUri: Uri, gameId: String, localArtPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val root = DocumentFile.fromTreeUri(context, usbRootUri) ?: return@withContext false
                val popsDir = root.findFile("POPS") ?: root.createDirectory("POPS") ?: return@withContext false
                val artDir = popsDir.findFile("ART") ?: popsDir.createDirectory("ART") ?: return@withContext false

                val sourceBytes = File(localArtPath).readBytes()
                val normalizedPng = ImageFormatter.normalizeToPng200x200(sourceBytes) ?: return@withContext false

                val fileName = "${gameId}_COV.png"
                artDir.findFile(fileName)?.delete()
                val newFile = artDir.createFile("image/png", fileName) ?: return@withContext false

                context.contentResolver.openOutputStream(newFile.uri)?.use { out ->
                    out.write(normalizedPng)
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /** Call once per USB drive: copies user-supplied POPSTARTER.ELF + POPS_IOX.PAK in. */
    suspend fun installBootFiles(usbRootUri: Uri, elf: File, iopPak: File): InstallResult =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, usbRootUri)
                ?: return@withContext InstallResult(false, "Could not open USB root.")
            val popsDir = root.findFile("POPS") ?: root.createDirectory("POPS")
                ?: return@withContext InstallResult(false, "Could not create /POPS directory.")

            fun copyIn(src: File, name: String): Boolean {
                popsDir.findFile(name)?.delete()
                val target = popsDir.createFile("application/octet-stream", name) ?: return false
                context.contentResolver.openOutputStream(target.uri)?.use { out ->
                    src.inputStream().use { it.copyTo(out) }
                }
                return true
            }

            val ok = copyIn(elf, "POPSTARTER.ELF") && copyIn(iopPak, "POPS_IOX.PAK")
            InstallResult(ok, if (ok) "Boot files installed." else "Failed to copy boot files.")
        }
}
