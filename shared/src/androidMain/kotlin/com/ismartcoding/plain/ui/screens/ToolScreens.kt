package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.db.DBookmark
import com.ismartcoding.plain.db.DFeed
import com.ismartcoding.plain.db.DPomodoroItem
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.platform.epochMillis
import com.ismartcoding.plain.platform.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ RSS / Feeds

@Composable
fun FeedsScreen(onBack: () -> Unit) {
    val dao = remember { AppDb.instance.feedDao() }
    CrudListScreen(
        title = "RSS Feeds",
        onBack = onBack,
        load = { dao.getAll() },
        key = { it.id },
        addLabels = "Feed URL" to "Name (optional)",
        onAdd = { url, name ->
            val now = epochMillis()
            dao.upsert(DFeed(id = newId(), url = url, name = name, createdAt = now, updatedAt = now))
        },
        onDelete = { dao.deleteById(it.id) },
    ) { feed: DFeed -> TitleRow(feed.name.ifBlank { feed.url }, feed.url) }
}

// ------------------------------------------------------------------- Bookmarks

@Composable
fun BookmarksScreen(onBack: () -> Unit) {
    val dao = remember { AppDb.instance.bookmarkDao() }
    CrudListScreen(
        title = "Bookmarks",
        onBack = onBack,
        load = { dao.getAll() },
        key = { it.id },
        addLabels = "Title" to "URL",
        onAdd = { title, url ->
            val now = epochMillis()
            dao.upsert(DBookmark(id = newId(), title = title, url = url, createdAt = now, updatedAt = now))
        },
        onDelete = { dao.deleteById(it.id) },
    ) { b: DBookmark -> TitleRow(b.title.ifBlank { b.url }, b.url) }
}

// ------------------------------------------------------------------------ Tags

@Composable
fun TagsScreen(onBack: () -> Unit) {
    val dao = remember { AppDb.instance.tagDao() }
    CrudListScreen(
        title = "Tags",
        onBack = onBack,
        load = { dao.getByType(0) },
        key = { it.id },
        addLabels = "Tag name" to null,
        onAdd = { name, _ ->
            val now = epochMillis()
            dao.upsert(DTag(id = newId(), name = name, type = 0, createdAt = now, updatedAt = now))
        },
        onDelete = { dao.deleteById(it.id) },
    ) { t: DTag -> TitleRow(t.name, if (t.count > 0) "${t.count} items" else "") }
}

// --------------------------------------------------------------------- Pomodoro

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(onBack: () -> Unit) {
    val dao = remember { AppDb.instance.pomodoroDao() }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<DPomodoroItem>?>(null) }
    LaunchedEffect(refresh) {
        items = withContext(Dispatchers.IO) { runCatching { dao.recent(100) }.getOrDefault(emptyList()) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pomodoro") },
                navigationIcon = { BackButton(onBack) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    val now = epochMillis()
                    dao.upsert(DPomodoroItem(id = newId(), durationSeconds = 1500, kind = 0, startedAt = now - 1_500_000, completedAt = now, createdAt = now))
                    refresh++
                }
            }) { Icon(Icons.Filled.Add, contentDescription = "Log focus session") }
        },
    ) { inner ->
        val list = items
        if (list.isNullOrEmpty()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text("No sessions yet — tap + to log a 25-minute focus.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(list, key = { it.id }) { p ->
                    TitleRow(
                        "${p.durationSeconds / 60}-minute ${if (p.kind == 0) "focus" else "break"}",
                        if (p.completedAt != null) "completed" else "in progress",
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

// ------------------------------------------------------------ shared components

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CrudListScreen(
    title: String,
    onBack: () -> Unit,
    load: suspend () -> List<T>,
    key: (T) -> Any,
    addLabels: Pair<String, String?>,
    onAdd: suspend (String, String) -> Unit,
    onDelete: suspend (T) -> Unit,
    row: @Composable (T) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<List<T>?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        data = withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        },
    ) { inner ->
        val list = data
        if (list.isNullOrEmpty()) {
            androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text("Empty — tap + to add.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(list, key = key) { item ->
                    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { row(item) }
                        IconButton(onClick = { scope.launch { onDelete(item); refresh++ } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        InputDialog(
            title = "Add",
            label1 = addLabels.first,
            label2 = addLabels.second,
            onConfirm = { v1, v2 ->
                showAdd = false
                if (v1.isNotBlank()) scope.launch { onAdd(v1, v2); refresh++ }
            },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
    }
}

@Composable
private fun TitleRow(primary: String, secondary: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (secondary.isNotBlank()) {
            Text(secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    label1: String,
    label2: String?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var v1 by remember { mutableStateOf("") }
    var v2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = v1, onValueChange = { v1 = it }, label = { Text(label1) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (label2 != null) {
                    OutlinedTextField(value = v2, onValueChange = { v2 = it }, label = { Text(label2) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(v1, v2) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
