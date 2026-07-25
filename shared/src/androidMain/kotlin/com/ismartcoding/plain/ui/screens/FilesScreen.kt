package com.ismartcoding.plain.ui.screens

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.features.file.FileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(onBack: () -> Unit) {
    val root = remember { Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0" }
    var path by remember { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<DFile>>(emptyList()) }

    LaunchedEffect(path) {
        entries = withContext(Dispatchers.IO) {
            runCatching { FileService.files(path) }.getOrDefault(emptyList())
        }
    }

    fun up() {
        if (path != root) path = File(path).parentFile?.absolutePath ?: root else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = path.substringAfterLast('/').ifEmpty { "Files" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { up() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            items(entries, key = { it.path }) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = entry.isDir) { if (entry.isDir) path = entry.path }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (entry.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.isDir) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = if (entry.isDir) "${entry.childCount} items" else fmtBytes(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
