package com.popsmanager.converter

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wraps the bundled `pops2cue` native binary (compiled from
 * https://github.com/bucanero/pops2cue by the CI workflow, shipped as
 * libpops2cue.so, same exec() trick as Cue2PopsConverter).
 *
 * Verified usage from the tool's own source/README:
 *   ./pops2cue <input_VCD> <option1> <option2>
 *   options: -nobin (cuesheet only), -noindex00 (omit INDEX 00 entries)
 *   Accepts VCDs made with CUE2POPS v2.0 — i.e. exactly what our own
 *   Cue2PopsConverter produces.
 *
 * vcdFile and outputDir must be real filesystem paths (not SAF content://
 * Uris) — the repository layer copies files in/out through app-private cache.
 */
class Vcd2BinCueConverter(private val context: Context) {

    data class ReverseResult(
        val success: Boolean,
        val outputCue: File?,
        val outputBin: File?,
        val log: String
    )

    private val binaryPath: String
        get() = File(context.applicationInfo.nativeLibraryDir, "libpops2cue.so").absolutePath

    suspend fun convert(vcdFile: File, outputDir: File): ReverseResult = withContext(Dispatchers.IO) {
        if (!vcdFile.exists()) {
            return@withContext ReverseResult(false, null, null, "VCD file not found: ${vcdFile.path}")
        }
        outputDir.mkdirs()

        try {
            // pops2cue writes its output next to the input VCD, named after it —
            // run it inside outputDir so that's exactly where the results land.
            val localVcd = File(outputDir, vcdFile.name)
            if (localVcd != vcdFile) vcdFile.copyTo(localVcd, overwrite = true)

            val process = ProcessBuilder(binaryPath, localVcd.absolutePath)
                .redirectErrorStream(true)
                .directory(outputDir)
                .start()

            val log = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(10, TimeUnit.MINUTES)
            val exitCode = if (finished) process.exitValue() else -1

            val cueOut = outputDir.listFiles()?.firstOrNull { it.extension.equals("cue", ignoreCase = true) }
            val binOut = outputDir.listFiles()?.firstOrNull { it.extension.equals("bin", ignoreCase = true) }

            // Same reconstructed codebase as Cue2PopsConverter's tool, which is
            // confirmed to exit nonzero even on genuine success — trust the
            // actual .cue file existing rather than the exit code.
            val success = finished && cueOut != null
            val fullLog = "exit code: $exitCode\n$log"
            ReverseResult(success, cueOut, binOut, fullLog)
        } catch (e: Exception) {
            ReverseResult(false, null, null, "Reverse conversion failed to start: ${e.message}")
        }
    }
}
