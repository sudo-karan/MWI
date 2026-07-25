package com.ismartcoding.plain.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ismartcoding.plain.ui.theme.MwiTheme

/**
 * Root composable. A tiny in-memory router: the home grid, the Web Console, or a per-feature
 * screen ([FeatureScreen], platform-provided). Real destinations replace the placeholder tile by
 * tile as each feature's UI lands.
 */
@Composable
fun App() {
    MwiTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            var selected: Feature? by remember { mutableStateOf(null) }
            val current = selected
            when {
                current == null -> HomeScreen(onFeatureClick = { selected = it })
                current.id == "web_console" -> WebConsoleScreen(onBack = { selected = null })
                else -> FeatureScreen(feature = current, onBack = { selected = null })
            }
        }
    }
}
