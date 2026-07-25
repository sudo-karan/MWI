package com.ismartcoding.plain.ui.screens

import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.features.file.DFile
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.features.notification.MwiNotificationListenerService
import com.ismartcoding.plain.webserver.AndroidWebServer
import java.io.File

// ------------------------------------------------------------------- Documents

@Composable
fun DocumentsScreen(onBack: () -> Unit) {
    val root = File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS).absolutePath
    DirectoryBrowser("Documents", root, { FileService.files(it) }, onBack)
}

// -------------------------------------------------------------------- App Files

@Composable
fun AppFilesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val root = context.filesDir.absolutePath
    DirectoryBrowser("App Files", root, { rawList(it) }, onBack)
}

/** Raw listing for the app's own private storage (not sandbox-gated — it is the app's own dir). */
private fun rawList(path: String): List<DFile> =
    File(path).listFiles()?.map { f ->
        DFile(
            name = f.name,
            path = f.absolutePath,
            isDir = f.isDirectory,
            size = if (f.isDirectory) 0 else f.length(),
            updatedAt = f.lastModified(),
            childCount = if (f.isDirectory) (f.list()?.size ?: 0) else 0,
        )
    }?.sortedWith(compareByDescending<DFile> { it.isDir }.thenBy { it.name.lowercase() }) ?: emptyList()

// ---------------------------------------------------------------- Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val notifications by MwiNotificationListenerService.store.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        if (notifications.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inner).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    "No mirrored notifications yet. Grant notification access to MWI to see them here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }) { Text("Grant notification access") }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(notifications, key = { it.key }) { n ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(n.title.ifBlank { n.packageName }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (n.text.isNotBlank()) {
                            Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Text(n.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ------------------------------------------------------------------- Developer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperScreen(onBack: () -> Unit) {
    val running by AndroidWebServer.running.collectAsState()
    val sslPort by AndroidWebServer.sslPort.collectAsState()
    val tables = listOf(
        "sessions", "tags", "tag_relations", "notes", "feeds", "feed_entries", "books",
        "book_chapters", "pomodoro_items", "bookmark_groups", "bookmarks", "app_files",
        "image_embeddings", "archived_conversations", "video_play_progress", "chats",
        "chat_channels", "peers",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            item { KeyValue("Web server", if (running) "running" else "stopped") }
            item { KeyValue("SSL port", if (sslPort > 0) sslPort.toString() else "—") }
            item { KeyValue("URL token", if (AndroidWebServer.urlToken != null) "issued" else "—") }
            item {
                Text(
                    "Database tables (${tables.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
            }
            items(tables) { t ->
                Text("• $t", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}
