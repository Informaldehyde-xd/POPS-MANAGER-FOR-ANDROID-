@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.popsmanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.popsmanager.data.PopsGame
import com.popsmanager.data.PopsGameStatus
import com.popsmanager.ui.LibraryViewModel
import com.popsmanager.ui.theme.PopsAccent
import com.popsmanager.ui.theme.PopsManagerTheme
import com.popsmanager.ui.theme.PopsOnSurfaceMuted
import com.popsmanager.ui.theme.PopsPrimary
import com.popsmanager.ui.theme.PopsSurfaceElevated

class MainActivity : ComponentActivity() {

    private val viewModel: LibraryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourcePicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onSourceFolderSelected(uri)
            }
        }
        val destPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                viewModel.onDestFolderSelected(uri)
            }
        }

        setContent {
            PopsManagerTheme {
                var titleEditGame by remember { mutableStateOf<PopsGame?>(null) }

                LibraryScreen(
                    viewModel = viewModel,
                    onPickSource = { sourcePicker.launch(null) },
                    onPickDest = { destPicker.launch(null) },
                    onEditTitle = { game -> titleEditGame = game }
                )

                if (titleEditGame != null) {
                    TitleEditDialog(
                        game = titleEditGame!!,
                        onSearch = { query -> viewModel.searchTitles(query) },
                        onSave = { title ->
                            viewModel.setManualTitle(titleEditGame!!, title)
                            titleEditGame = null
                        },
                        onCancel = { titleEditGame = null }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPickSource: () -> Unit,
    onPickDest: () -> Unit,
    onEditTitle: (PopsGame) -> Unit
) {
    val games by viewModel.games.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val status by viewModel.statusMessage.collectAsState()
    val destUri by viewModel.destUri.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("POPS MANAGER", style = MaterialTheme.typography.titleLarge, color = PopsAccent)
                        Text("PS1 Library & Installer", style = MaterialTheme.typography.labelSmall, color = PopsOnSurfaceMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PopsSurfaceElevated,
                    titleContentColor = PopsAccent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier.padding(padding).fillMaxSize().background(
                Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, PopsSurfaceElevated.copy(alpha = 0.35f)))
            )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                Button(
                    onClick = onPickSource,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PopsPrimary)
                ) {
                    Text("1. PICK SOURCE FOLDER (BIN/CUE DUMPS)", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = onPickDest,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destUri != null) PopsSurfaceElevated else PopsPrimary
                    )
                ) {
                    Text(
                        if (destUri != null) "2. DESTINATION SET \u2713 (tap to change)" else "2. PICK USB/HDD DESTINATION",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (destUri != null) PopsAccent else Color.White
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = PopsOnSurfaceMuted)

                if (isScanning) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = PopsAccent, trackColor = PopsSurfaceElevated)
                }

                if (destUri != null && games.any { it.status == PopsGameStatus.MATCHED }) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.convertAndInstallAllMatched() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PopsSurfaceElevated)
                    ) {
                        Text("Convert + Install All Matched", color = PopsAccent)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (games.isEmpty()) {
                    EmptyLibraryPlaceholder()
                } else {
                    LazyColumn {
                        items(games) { game ->
                            GameRow(
                                game = game,
                                destReady = destUri != null,
                                onConvertInstall = { viewModel.convertAndInstall(game) },
                                onTap = { onEditTitle(game) }
                            )
                            Divider(color = PopsSurfaceElevated)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyLibraryPlaceholder() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(PopsSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text("\uD83D\uDCBF", style = MaterialTheme.typography.displayMedium)
        }
        Spacer(Modifier.height(20.dp))
        Text("No games loaded yet", style = MaterialTheme.typography.titleMedium, color = PopsAccent)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a source folder containing your BIN/CUE dumps to get started.",
            style = MaterialTheme.typography.bodySmall,
            color = PopsOnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GameRow(game: PopsGame, destReady: Boolean, onConvertInstall: () -> Unit, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PopsSurfaceElevated.copy(alpha = 0.5f))
            .clickable { onTap() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (game.coverArtLocalPath != null) {
            AsyncImage(
                model = game.coverArtLocalPath,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp))
            )
        } else {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(PopsSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDCBF", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                game.matchedTitle ?: game.cueDisplayName.substringBeforeLast('.', game.cueDisplayName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                game.gameId ?: "Unrecognized — tap to set title",
                style = MaterialTheme.typography.bodySmall,
                color = PopsOnSurfaceMuted
            )
            Text(statusLabel(game.status), style = MaterialTheme.typography.labelSmall, color = PopsAccent)
            if (game.status == PopsGameStatus.ERROR && game.lastError != null) {
                Text(game.lastError!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }

        if (game.status == PopsGameStatus.MATCHED && destReady) {
            Button(onClick = onConvertInstall, colors = ButtonDefaults.buttonColors(containerColor = PopsPrimary)) {
                Text("Install")
            }
        }
    }
}

@Composable
fun TitleEditDialog(
    game: PopsGame,
    onSearch: (String) -> List<Pair<String, String>>,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(game.matchedTitle ?: "") }
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Set Title", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(game.gameId ?: "Unrecognized", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; results = onSearch(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Type or search a title...") }
                )

                results.take(6).forEach { (_, title) ->
                    TextButton(onClick = { text = title; results = emptyList() }, modifier = Modifier.fillMaxWidth()) {
                        Text(title, modifier = Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save Title") }
                }
            }
        }
    }
}

private fun statusLabel(status: PopsGameStatus): String = when (status) {
    PopsGameStatus.PENDING -> "Pending"
    PopsGameStatus.LOOKING_UP -> "Looking up title..."
    PopsGameStatus.MATCHED -> "Match found — ready to install"
    PopsGameStatus.NO_MATCH -> "No match found in database"
    PopsGameStatus.CONVERTING -> "Converting (cue2pops)..."
    PopsGameStatus.INSTALLING -> "Installing to drive..."
    PopsGameStatus.INSTALLED -> "Installed \u2713"
    PopsGameStatus.ERROR -> "Error — try again"
}
