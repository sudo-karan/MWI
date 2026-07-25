package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.db.ChatContent
import com.ismartcoding.plain.db.DChat
import com.ismartcoding.plain.db.DChatChannel
import com.ismartcoding.plain.platform.epochMillis
import com.ismartcoding.plain.platform.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(onBack: () -> Unit) {
    var channel by remember { mutableStateOf<DChatChannel?>(null) }
    val current = channel
    if (current == null) {
        ChannelListScreen(onOpen = { channel = it }, onBack = onBack)
    } else {
        ChannelChatScreen(current, onBack = { channel = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelListScreen(onOpen: (DChatChannel) -> Unit, onBack: () -> Unit) {
    val dao = remember { AppDb.instance.chatChannelDao() }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var channels by remember { mutableStateOf<List<DChatChannel>?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) {
        channels = withContext(Dispatchers.IO) { runCatching { dao.getAll() }.getOrDefault(emptyList()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, contentDescription = "New channel") }
        },
    ) { inner ->
        val list = channels
        if (list.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text("No channels — tap + to create one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(list, key = { it.id }) { channel ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            channel.name.ifBlank { "(unnamed)" },
                            Modifier.weight(1f).clickable { onOpen(channel) }.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { scope.launch { dao.deleteById(channel.id); refresh++ } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("New channel") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    showAdd = false
                    if (name.isNotBlank()) scope.launch {
                        val now = epochMillis()
                        dao.upsert(DChatChannel(id = newId(), name = name, createdAt = now, updatedAt = now))
                        refresh++
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelChatScreen(channel: DChatChannel, onBack: () -> Unit) {
    val dao = remember { AppDb.instance.chatDao() }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableStateOf(0) }
    var messages by remember { mutableStateOf<List<DChat>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    LaunchedEffect(refresh) {
        messages = withContext(Dispatchers.IO) { runCatching { dao.getByChannel(channel.id, 200, 0) }.getOrDefault(emptyList()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(channel.name.ifBlank { "Chat" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message") },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            input = ""
                            scope.launch {
                                val now = epochMillis()
                                dao.upsert(DChat(id = newId(), channelId = channel.id, isMe = true, content = ChatContent.Text(text), createdAt = now, updatedAt = now))
                                refresh++
                            }
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send") }
                }
            }
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(horizontal = 12.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id }) { msg ->
                val text = (msg.content as? ChatContent.Text)?.text ?: "[attachment]"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start) {
                    Surface(
                        color = if (msg.isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.widthIn(max = 280.dp),
                    ) {
                        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}
