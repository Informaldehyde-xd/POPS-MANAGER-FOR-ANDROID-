package com.popsmanager.util

/**
 * PS1 serials look like: SLUS-01476, SLES-03889, SCUS-94635, SCES-02771,
 * SLPS-01095, SLPM-87171, SCPS-10032, SIPS-60002. Unlike PS2's "SLUS_212.42"
 * form, PS1 conventionally uses a plain hyphen + 5 contiguous digits — and
 * this is also exactly the form both our title database (libretro's
 * ps1.idlst) and cover art source (xlenore/psx-covers) key on, so we keep
 * IDs in this form throughout rather than converting back and forth.
 */
object GameIdUtil {

    private val ID_REGEX = Regex(
        "(SLUS|SLES|SCUS|SCES|SLPS|SLPM|SCPS|SIPS|SLED|SCED|LSP)[-_](\\d{3})\\.?(\\d{2})",
        RegexOption.IGNORE_CASE
    )

    /** Returns the normalized serial (e.g. "SLUS-01476") found anywhere in the given text, or null. */
    fun extractGameId(text: String): String? {
        val match = ID_REGEX.find(text) ?: return null
        val (prefix, part1, part2) = match.destructured
        return "${prefix.uppercase()}-$part1$part2"
    }

    /** Builds the POPStarter filename convention: SERIAL.Title.VCD */
    fun buildVcdFilename(gameId: String, title: String): String {
        val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "").trim()
        return "$gameId.$safeTitle.VCD"
    }
}
