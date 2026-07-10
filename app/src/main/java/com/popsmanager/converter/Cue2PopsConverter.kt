package com.popsmanager.converter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wraps the bundled `cue2pops` native binary (compiled from
 * https://github.com/israpps/cue2pops by the CI workflow, shipped as
 * libcue2pops.so so Android's exec() sandbox allows running it).
 *
 * cue2pops requires:
 *   - a MODE2/2352 raw BIN dump referenced by an ASCII .cue sheet
 *   - single-file dumps (merge multi-track sets first if you have a
 *     multi-track rip; most single-file BIN/CUE rips work as-is)
 *
 * Both cueFile and outputDir must be real filesystem paths (not SAF content://
 * Uris) since running a native binary requires actual files — the repository
 * layer copies files in from SAF first, and copies the result back out after.
 */
class Cue2PopsConverter(private val context: Context) {

    data class ConversionResult(
        val success: Boolean,
        val outputVcd: File?,
        val log: String
    )

    private val binaryPath: String
        get() = File(context.applicationInfo.nativeLibraryDir, "libcue2pops.so").absolutePath

    suspend fun convert(cueFile: File, outputDir: File): ConversionResult = withContext(Dispatchers.IO) {
        if (!cueFile.exists()) {
            return@withContext ConversionResult(false, null, "CUE file not found: ${cueFile.path}")
        }
        outputDir.mkdirs()
        val outputVcd = File(outputDir, cueFile.nameWithoutExtension + ".VCD")

        try {
            val process = ProcessBuilder(binaryPath, cueFile.absolutePath, outputVcd.absolutePath)
                .redirectErrorStream(true)
                .directory(cueFile.parentFile)
                .start()

            val log = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            val success = finished && process.exitValue() == 0 && outputVcd.exists()

            ConversionResult(success, if (success) outputVcd else null, log)
        } catch (e: Exception) {
            ConversionResult(false, null, "Conversion failed to start: ${e.message}")
        }
    }
}
