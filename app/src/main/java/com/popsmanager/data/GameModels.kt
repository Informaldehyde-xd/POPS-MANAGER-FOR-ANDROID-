package com.popsmanager.data

import android.net.Uri

/** Represents one BIN/CUE game found in the source folder. */
data class PopsGame(
    val cueDocumentId: String,   // SAF uri string of the .cue file — unique key
    val cueDisplayName: String,  // current .cue filename as it is on disk
    val binUri: Uri,             // paired .bin file's SAF uri
    val gameId: String?,         // detected serial, e.g. "SLUS-01234" (null if not recognized)
    var matchedTitle: String? = null,
    var coverArtLocalPath: String? = null,
    var status: PopsGameStatus = PopsGameStatus.PENDING,
    var lastError: String? = null
)

enum class PopsGameStatus {
    PENDING,        // not looked up yet
    LOOKING_UP,     // querying the title database
    MATCHED,        // title found, ready to convert & install
    NO_MATCH,       // no title found for this Game ID (or no ID detected at all)
    CONVERTING,     // running cue2pops
    INSTALLING,     // copying the VCD (and art) onto the destination drive
    INSTALLED,      // done
    ERROR
}
