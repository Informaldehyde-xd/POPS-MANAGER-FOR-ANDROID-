package com.popsmanager.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.popsmanager.data.CoverArtFetcher
import com.popsmanager.data.GameRepository
import com.popsmanager.data.PopsGame
import com.popsmanager.data.PopsGameStatus
import com.popsmanager.data.ReverseItem
import com.popsmanager.data.TitleDatabase
import com.popsmanager.data.VcdEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val titleDb = TitleDatabase(application)
    private val artFetcher = CoverArtFetcher(application)

    private val _games = MutableStateFlow<List<PopsGame>>(emptyList())
    val games: StateFlow<List<PopsGame>> = _games.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pick a source folder with your BIN/CUE dumps to get started.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _destUri = MutableStateFlow<Uri?>(null)
    val destUri: StateFlow<Uri?> = _destUri.asStateFlow()

    private var sourceUri: Uri? = null

    // --- Reverse conversion (VCD -> BIN/CUE) ---
    private val _reverseItems = MutableStateFlow<List<ReverseItem>>(emptyList())
    val reverseItems: StateFlow<List<ReverseItem>> = _reverseItems.asStateFlow()

    private val _reverseSourceUri = MutableStateFlow<Uri?>(null)
    val reverseSourceUri: StateFlow<Uri?> = _reverseSourceUri.asStateFlow()

    private val _reverseDestUri = MutableStateFlow<Uri?>(null)
    val reverseDestUri: StateFlow<Uri?> = _reverseDestUri.asStateFlow()

    private val _reverseStatusMessage = MutableStateFlow("Pick a folder with .VCD files to convert back to BIN/CUE.")
    val reverseStatusMessage: StateFlow<String> = _reverseStatusMessage.asStateFlow()

    fun onReverseSourceSelected(treeUri: Uri) {
        _reverseSourceUri.value = treeUri
        viewModelScope.launch {
            _reverseStatusMessage.value = "Scanning for .VCD files..."
            val found = repository.scanVcdFolder(treeUri)
            _reverseItems.value = found.map { ReverseItem(it.documentId, it.displayName) }
            _reverseStatusMessage.value = "Found ${found.size} VCD file(s)."
        }
    }

    fun onReverseDestSelected(treeUri: Uri) {
        _reverseDestUri.value = treeUri
        _reverseStatusMessage.value = "Destination set. Tap Convert on any VCD below."
    }

    fun convertVcdToBinCue(item: ReverseItem) {
        val dest = _reverseDestUri.value ?: return
        viewModelScope.launch {
            updateReverseItem(item.documentId) { it.copy(status = PopsGameStatus.CONVERTING) }
            val (success, error) = repository.convertVcdToBinCue(
                vcdUri = Uri.parse(item.documentId),
                vcdName = item.displayName,
                destUri = dest
            )
            updateReverseItem(item.documentId) {
                it.copy(status = if (success) PopsGameStatus.INSTALLED else PopsGameStatus.ERROR, lastError = error)
            }
        }
    }

    fun convertAllVcds() {
        viewModelScope.launch {
            _reverseItems.value.filter { it.status == PopsGameStatus.PENDING }.forEach { convertVcdToBinCue(it) }
        }
    }

    private fun updateReverseItem(documentId: String, transform: (ReverseItem) -> ReverseItem) {
        _reverseItems.value = _reverseItems.value.map { if (it.documentId == documentId) transform(it) else it }
    }

    fun onSourceFolderSelected(treeUri: Uri) {
        sourceUri = treeUri
        viewModelScope.launch {
            _isScanning.value = true
            _statusMessage.value = "Scanning for BIN/CUE pairs..."
            val found = repository.scanSourceFolder(treeUri)
            _games.value = found
            _statusMessage.value = "Found ${found.size} game(s). Loading title database..."

            titleDb.ensureLoaded()
            if (titleDb.entryCount == 0) {
                _statusMessage.value = "Couldn't load the online title database: " +
                    (titleDb.lastError ?: "unknown error") + " — you can still set titles manually."
            }

            val updated = found.map { game ->
                if (game.gameId == null) {
                    game.copy(status = PopsGameStatus.NO_MATCH)
                } else {
                    val title = titleDb.lookupTitle(game.gameId)
                    if (title != null) {
                        game.copy(matchedTitle = title, status = PopsGameStatus.MATCHED)
                    } else {
                        game.copy(status = PopsGameStatus.NO_MATCH)
                    }
                }
            }
            _games.value = updated
            _isScanning.value = false
            if (titleDb.entryCount != 0) {
                _statusMessage.value = "Done. ${updated.count { it.status == PopsGameStatus.MATCHED }} of ${updated.size} matched."
            }
        }
    }

    fun onDestFolderSelected(treeUri: Uri) {
        _destUri.value = treeUri
        _statusMessage.value = "Destination set. Tap a matched game to convert and install it."
    }

    fun setManualTitle(game: PopsGame, title: String) {
        if (title.isBlank()) return
        updateGame(game.cueDocumentId) { it.copy(matchedTitle = title, status = PopsGameStatus.MATCHED) }
    }

    fun searchTitles(query: String): List<Pair<String, String>> = titleDb.searchTitles(query)

    fun convertAndInstall(game: PopsGame) {
        val dest = _destUri.value ?: return
        val gameId = game.gameId ?: return
        val title = game.matchedTitle ?: return

        viewModelScope.launch {
            updateGame(game.cueDocumentId) { it.copy(status = PopsGameStatus.CONVERTING) }

            val artPath = artFetcher.fetchCover(gameId)
            if (artPath != null) {
                updateGame(game.cueDocumentId) { it.copy(coverArtLocalPath = artPath) }
            }

            updateGame(game.cueDocumentId) { it.copy(status = PopsGameStatus.INSTALLING) }
            val (success, error) = repository.convertAndInstall(
                cueUri = Uri.parse(game.cueDocumentId),
                cueName = game.cueDisplayName,
                binUri = game.binUri,
                gameId = gameId,
                title = title,
                destUri = dest,
                localArtPath = artPath
            )

            updateGame(game.cueDocumentId) {
                it.copy(status = if (success) PopsGameStatus.INSTALLED else PopsGameStatus.ERROR, lastError = error)
            }
        }
    }

    fun convertAndInstallAllMatched() {
        viewModelScope.launch {
            _games.value.filter { it.status == PopsGameStatus.MATCHED }.forEach { convertAndInstall(it) }
        }
    }

    private fun updateGame(cueDocumentId: String, transform: (PopsGame) -> PopsGame) {
        _games.value = _games.value.map { if (it.cueDocumentId == cueDocumentId) transform(it) else it }
    }
}
