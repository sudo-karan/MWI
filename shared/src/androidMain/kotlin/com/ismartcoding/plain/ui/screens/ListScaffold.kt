package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generic "load-then-list" screen for the read-only viewers (Contacts/Calls/SMS/Apps). Handles the
 * top bar, a loading spinner, an empty state, and the divider between rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> ListScreen(
    title: String,
    onBack: () -> Unit,
    load: suspend () -> List<T>,
    key: (T) -> Any,
    emptyText: String = "Nothing here (permission may be needed).",
    row: @Composable (T) -> Unit,
) {
    var data by remember { mutableStateOf<List<T>?>(null) }
    LaunchedEffect(Unit) {
        data = withContext(Dispatchers.IO) { runCatching { load() }.getOrDefault(emptyList()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        val list = data
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(inner)) {
                items(list, key = key) { item ->
                    row(item)
                    HorizontalDivider()
                }
            }
        }
    }
}
