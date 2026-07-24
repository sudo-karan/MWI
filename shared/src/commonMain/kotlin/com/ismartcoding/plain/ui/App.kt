package com.ismartcoding.plain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.resources.Res
import com.ismartcoding.plain.resources.back
import com.ismartcoding.plain.resources.coming_soon
import com.ismartcoding.plain.resources.feature_not_wired
import com.ismartcoding.plain.ui.theme.MwiTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Root composable. A deliberately tiny in-memory router for the Phase-1 shell: the home grid, or a
 * per-feature screen. Real destinations replace [FeaturePlaceholder] as each build phase lands.
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
                else -> FeaturePlaceholder(feature = current, onBack = { selected = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturePlaceholder(feature: Feature, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(feature.label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(Res.string.coming_soon),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.feature_not_wired),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
