package com.ismartcoding.plain.ui

import androidx.compose.runtime.Composable
import com.ismartcoding.plain.ui.screens.DeviceInfoScreen
import com.ismartcoding.plain.ui.screens.FilesScreen
import com.ismartcoding.plain.ui.screens.NotesScreen

@Composable
actual fun FeatureScreen(feature: Feature, onBack: () -> Unit) {
    when (feature.id) {
        "device_info" -> DeviceInfoScreen(onBack)
        "notes" -> NotesScreen(onBack)
        "files" -> FilesScreen(onBack)
        else -> FeaturePlaceholder(feature, onBack)
    }
}
