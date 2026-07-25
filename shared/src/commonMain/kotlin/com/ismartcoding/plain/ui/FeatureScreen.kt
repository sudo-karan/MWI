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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.resources.Res
import com.ismartcoding.plain.resources.back
import com.ismartcoding.plain.resources.coming_soon
import com.ismartcoding.plain.resources.feature_not_wired
import org.jetbrains.compose.resources.stringResource

/**
 * Renders the destination for a home-grid [Feature]. Platform-specific because the real screens
 * (Device info, Notes, Files, …) call Android providers directly; the actual falls back to
 * [FeaturePlaceholder] for tiles not yet wired.
 */
@Composable
expect fun FeatureScreen(feature: Feature, onBack: () -> Unit)

/** Shared "not wired yet" screen, reused by the platform [FeatureScreen] for pending tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturePlaceholder(feature: Feature, onBack: () -> Unit) {
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
                Icon(feature.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.coming_soon), style = MaterialTheme.typography.titleMedium)
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
