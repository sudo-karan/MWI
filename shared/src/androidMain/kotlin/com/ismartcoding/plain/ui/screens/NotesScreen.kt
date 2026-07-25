package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.db.DNote
import com.ismartcoding.plain.platform.epochMillis
import com.ismartcoding.plain.platform.newId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val dao = remember { AppDb.instance.noteDao() }
    val notes by dao.flowAll().collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<DNote?>(null) }

    val current = editing
    if (current != null) {
        NoteEditor(
            note = current,
            onSave = { title, content ->
                scope.launch {
                    dao.upsert(current.copy(title = title, content = content, updatedAt = epochMillis()))
                    editing = null
                }
            },
            onCancel = { editing = null },
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val now = epochMillis()
                editing = DNote(id = newId(), title = "", content = "", createdAt = now, updatedAt = now)
            }) { Icon(Icons.Filled.Add, contentDescription = "New note") }
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            items(notes, key = { it.id }) { note ->
                Column(
                    Modifier.fillMaxWidth().clickable { editing = note }.padding(16.dp),
                ) {
                    NoteRow(note) { scope.launch { dao.deleteById(note.id) } }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun NoteRow(note: DNote, onDelete: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditor(note: DNote, onSave: (String, String) -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit note") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(title, content) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Save")
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
            )
        }
    }
}
