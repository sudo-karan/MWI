package com.ismartcoding.plain.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ismartcoding.plain.features.media.DMediaItem
import com.ismartcoding.plain.features.media.MediaProvider
import com.ismartcoding.plain.features.media.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// --------------------------------------------------------------------- Audio

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var items by remember { mutableStateOf<List<DMediaItem>?>(null) }
    var nowPlaying by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            runCatching { MediaProvider.query(MediaType.AUDIO, 0, 500, null) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Audio") }, navigationIcon = { BackNav(onBack) }) },
        bottomBar = {
            nowPlaying?.let { title ->
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
                        }
                    }
                }
            }
        },
    ) { inner ->
        MediaList(inner, items, "No audio (media permission may be needed).") { a ->
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(a.path))))
            player.prepare()
            player.play()
            nowPlaying = a.title
        }
    }
}

// --------------------------------------------------------------------- Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<DMediaItem>?>(null) }
    var playing by remember { mutableStateOf<DMediaItem?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            runCatching { MediaProvider.query(MediaType.VIDEO, 0, 500, null) }.getOrDefault(emptyList())
        }
    }

    val current = playing
    if (current != null) {
        VideoPlayer(current, onClose = { playing = null })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Videos") }, navigationIcon = { BackNav(onBack) }) },
    ) { inner ->
        MediaList(inner, items, "No videos (media permission may be needed).") { playing = it }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoPlayer(item: DMediaItem, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(item.path))))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackNav(onClose) },
            )
        },
    ) { inner ->
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
            modifier = Modifier.fillMaxSize().padding(inner),
        )
    }
}

// ------------------------------------------------------------------ shared

@Composable
private fun BackNav(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
private fun MediaList(
    inner: androidx.compose.foundation.layout.PaddingValues,
    items: List<DMediaItem>?,
    emptyText: String,
    onClick: (DMediaItem) -> Unit,
) {
    when {
        items == null -> {}
        items.isEmpty() -> androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().padding(inner), Alignment.Center,
        ) { Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            items(items, key = { it.id }) { item ->
                Column(
                    Modifier.fillMaxWidth().clickable { onClick(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(item.title.ifBlank { item.path.substringAfterLast('/') }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(fmtBytes(item.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}
