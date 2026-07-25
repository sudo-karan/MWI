package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ismartcoding.plain.features.media.DMediaItem
import com.ismartcoding.plain.features.media.MediaProvider
import com.ismartcoding.plain.features.media.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGalleryScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<DMediaItem>?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            runCatching { MediaProvider.query(MediaType.IMAGE, 0, 300, null) }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text("No photos (media permission may be needed).", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier.fillMaxSize().padding(inner),
            ) {
                items(list, key = { it.id }) { item ->
                    AsyncImage(
                        model = File(item.path),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.aspectRatio(1f).padding(1.dp),
                    )
                }
            }
        }
    }
}
