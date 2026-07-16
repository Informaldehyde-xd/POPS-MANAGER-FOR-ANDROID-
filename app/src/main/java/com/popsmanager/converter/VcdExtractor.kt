package com.popsmanager.util

import java.io.File
import java.io.RandomAccessFile

/**
 * Parses POPS VCD files (CUE2POPS v2.0 format) and extracts them back to
 * BIN/CUE, entirely in Kotlin — no native tool involved.
 *
 * This exact byte layout was independently verified against a real VCD
 * produced by our own bundled cue2pops (israpps/cue2pops, genuine v2.0):
 * every marker below (0xA0 at offset 2, 0xA1 at offset 12, 0xA2 at offset
 * 22, track type 0x41, etc.) was confirmed present at the documented offset
 * in an actual generated file. The previously-used native `pops2cue`
 * (reconstructed via disassembly) failed to parse these same, correctly-
 * formed files — that was a bug in that specific reconstruction, not in the
 * VCD files themselves. Implementing the parser directly avoids depending
 * on that broken tool at all.
 *
 * Header layout (first 0x410 bytes of the file):
 *   0x00: 'A' (0x41)      — first track type marker (data track)
 *   0x02: 0xA0            — disc type array marker
 *   0x07: 0x01            — first track number
 *   0x08: 0x20 (space)    — disc content array check (CD-XA001)
 *   0x0C: 0xA1            — disc content array marker
 *   0x11: BCD             — total track count
 *   0x16: 0xA2            — lead-in/out array marker
 *   0x1B-0x1D: BCD MM:SS:FF — lead-out
 *   0x1E onwards: 10-byte track descriptors:
 *     +0 type ('A'=data, else audio), +2 track# (BCD),
 *     +3,+4,+5 INDEX00 MM/SS/FF (BCD), +7,+8,+9 INDEX01 MM/SS/FF (BCD)
 *   0x400: "kHn" signature + version byte (>= 0x20 for v2.0)
 *   0x408 / 0x40C: sector count, duplicated (validation)
 * After 0x100000 (1MB): raw BIN disc data.
 */
object VcdExtractor {

    private const val HEADER_SIZE = 0x100000
    private const val MIN_VCD_SIZE = 0x10A560L

    data class TrackInfo(
        val trackNumber: Int,
        val type: String, // "MODE2/2352" or "AUDIO"
        val index00: Triple<Int, Int, Int>?, // mm, ss, ff — null if same as index01
        val index01: Triple<Int, Int, Int>
    )

    data class VcdInfo(
        val trackCount: Int,
        val tracks: List<TrackInfo>,
        val isValid: Boolean,
        val errors: List<String>
    )

    private fun u(b: Byte): Int = b.toInt() and 0xFF

    private fun bcdToDecimal(bcd: Int): Int = ((bcd shr 4) and 0x0F) * 10 + (bcd and 0x0F)

    private fun bcdDecrementMinute(mm: Int): Int = when (mm) {
        0x10 -> 0x09; 0x20 -> 0x19; 0x30 -> 0x29; 0x40 -> 0x39; 0x50 -> 0x49
        0x60 -> 0x59; 0x70 -> 0x69; 0x80 -> 0x79; 0x90 -> 0x89
        else -> mm - 1
    }

    /** Reverses the +2 second BCD offset cue2pops v2.0 applies to INDEX 00 timestamps. */
    private fun revertIndex00(header: ByteArray, pos: Int, trackIndex: Int) {
        if (trackIndex == 0) return
        val ss = u(header[pos + 4])
        val needsBcdSub = ss in setOf(0x10, 0x11, 0x20, 0x21, 0x30, 0x31, 0x40, 0x41, 0x50, 0x51)
        when {
            ss == 0x00 || ss == 0x01 -> {
                header[pos + 4] = (if (ss == 0x00) 0x58 else 0x59).toByte()
                header[pos + 3] = bcdDecrementMinute(u(header[pos + 3])).toByte()
            }
            needsBcdSub -> header[pos + 4] = (ss - 8).toByte()
            else -> header[pos + 4] = (ss - 2).toByte()
        }
    }

    /** Reverses the +2 second BCD offset cue2pops v2.0 applies to INDEX 01 timestamps. */
    private fun revertIndex01(header: ByteArray, pos: Int, trackIndex: Int) {
        val ss = u(header[pos + 8])
        if (trackIndex == 0) {
            header[pos + 8] = (ss - 2).toByte()
            return
        }
        val needsBcdSub = ss in setOf(0x10, 0x11, 0x20, 0x21, 0x30, 0x31, 0x40, 0x41, 0x50, 0x51)
        when {
            ss == 0x00 || ss == 0x01 -> {
                header[pos + 8] = (if (ss == 0x00) 0x58 else 0x59).toByte()
                header[pos + 7] = bcdDecrementMinute(u(header[pos + 7])).toByte()
            }
            needsBcdSub -> header[pos + 8] = (ss - 8).toByte()
            else -> header[pos + 8] = (ss - 2).toByte()
        }
    }

    private fun readU32LE(header: ByteArray, off: Int): Long =
        (u(header[off]).toLong()) or
        (u(header[off + 1]).toLong() shl 8) or
        (u(header[off + 2]).toLong() shl 16) or
        (u(header[off + 3]).toLong() shl 24)

    /** Reads and validates just the header portion (first 0x410 bytes) of a VCD file. */
    fun parseVcdHeader(vcdFile: File): VcdInfo {
        val errors = mutableListOf<String>()
        if (vcdFile.length() < MIN_VCD_SIZE) {
            return VcdInfo(0, emptyList(), false, listOf("Input file is too small to be a valid POPS VCD"))
        }

        val header = ByteArray(0x410)
        RandomAccessFile(vcdFile, "r").use { raf -> raf.readFully(header) }

        val sc1 = readU32LE(header, 0x408)
        val sc2 = readU32LE(header, 0x40C)
        if (sc1 != sc2) errors.add("Malformed disc image entry (sector count mismatch)")
        if (u(header[0x02]) != 0xA0) errors.add("Disc type array not found (expected 0xA0 at offset 0x02)")
        if (u(header[0x0C]) != 0xA1) errors.add("Disc content array not found (expected 0xA1 at offset 0x0C)")
        if (u(header[0x16]) != 0xA2) errors.add("Lead-in/out array not found (expected 0xA2 at offset 0x16)")
        if (u(header[0x00]) != 0x41) errors.add("First track must be a DATA track (expected 0x41 at offset 0x00)")
        if (u(header[0x08]) != 0x20) errors.add("Disc type is not CD-XA001 (expected 0x20 at offset 0x08)")
        if (u(header[0x07]) != 0x01) errors.add("First track is not TRACK 01 (expected 0x01 at offset 0x07)")

        if (u(header[0x400]) == 0x6B && u(header[0x401]) == 0x48 && u(header[0x402]) == 0x6E) {
            if (u(header[0x403]) < 0x20) errors.add("This VCD was made by CUE2POPS v1.X, which isn't supported.")
        } else {
            errors.add("CUE2POPS signature not found — this file wasn't produced by CUE2POPS.")
        }

        if (errors.isNotEmpty()) return VcdInfo(0, emptyList(), false, errors)

        val trackCount = bcdToDecimal(u(header[0x11]))
        val workHeader = header.copyOf()

        var pos = 0x1E
        for (i in 0 until trackCount) {
            revertIndex00(workHeader, pos, i)
            revertIndex01(workHeader, pos, i)
            pos += 10
        }

        val tracks = mutableListOf<TrackInfo>()
        pos = 0x1E
        for (i in 0 until trackCount) {
            val trackType = if (u(workHeader[pos]) == 0x41) "MODE2/2352" else "AUDIO"
            val trackNum = bcdToDecimal(u(workHeader[pos + 2]))
            val idx00 = Triple(bcdToDecimal(u(workHeader[pos + 3])), bcdToDecimal(u(workHeader[pos + 4])), bcdToDecimal(u(workHeader[pos + 5])))
            val idx01 = Triple(bcdToDecimal(u(workHeader[pos + 7])), bcdToDecimal(u(workHeader[pos + 8])), bcdToDecimal(u(workHeader[pos + 9])))
            val hasIndex00 = i > 0 && (
                workHeader[pos + 3] != workHeader[pos + 7] ||
                workHeader[pos + 4] != workHeader[pos + 8] ||
                workHeader[pos + 5] != workHeader[pos + 9]
            )
            tracks.add(TrackInfo(trackNum, trackType, if (hasIndex00) idx00 else null, idx01))
            pos += 10
        }

        return VcdInfo(trackCount, tracks, true, emptyList())
    }

    /** Builds a standard CUE sheet from the parsed track info. */
    fun generateCueSheet(info: VcdInfo, binFilename: String): String {
        val lines = mutableListOf<String>()
        lines.add("FILE \"$binFilename\" BINARY")
        lines.add("  TRACK 01 MODE2/2352")
        lines.add("    INDEX 01 00:00:00")
        for (i in 1 until info.tracks.size) {
            val track = info.tracks[i]
            lines.add("  TRACK ${track.trackNumber.toString().padStart(2, '0')} ${track.type}")
            track.index00?.let { (mm, ss, ff) ->
                lines.add("    INDEX 00 ${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}:${ff.toString().padStart(2, '0')}")
            }
            val (mm, ss, ff) = track.index01
            lines.add("    INDEX 01 ${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}:${ff.toString().padStart(2, '0')}")
        }
        return lines.joinToString("\n")
    }

    /** Streams the BIN data (everything after the 1MB header) straight to outputBin, without loading the whole file into memory. */
    fun extractBinData(vcdFile: File, outputBin: File) {
        RandomAccessFile(vcdFile, "r").use { raf ->
            raf.seek(HEADER_SIZE.toLong())
            outputBin.outputStream().use { out ->
                val buffer = ByteArray(1 shl 20) // 1MB buffer, so huge discs don't need to fit in memory at once
                while (true) {
                    val read = raf.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }
}
