package com.ismartcoding.plain.ui

import androidx.compose.runtime.Composable
import com.ismartcoding.plain.ui.screens.AppsScreen
import com.ismartcoding.plain.ui.screens.AudioScreen
import com.ismartcoding.plain.ui.screens.BookmarksScreen
import com.ismartcoding.plain.ui.screens.CallsScreen
import com.ismartcoding.plain.ui.screens.ContactsScreen
import com.ismartcoding.plain.ui.screens.DeviceInfoScreen
import com.ismartcoding.plain.ui.screens.FeedsScreen
import com.ismartcoding.plain.ui.screens.FilesScreen
import com.ismartcoding.plain.ui.screens.MediaGalleryScreen
import com.ismartcoding.plain.ui.screens.NearbyScreen
import com.ismartcoding.plain.ui.screens.NotesScreen
import com.ismartcoding.plain.ui.screens.PomodoroScreen
import com.ismartcoding.plain.ui.screens.SettingsScreen
import com.ismartcoding.plain.ui.screens.SmsScreen
import com.ismartcoding.plain.ui.screens.SoundMeterScreen
import com.ismartcoding.plain.ui.screens.TagsScreen
import com.ismartcoding.plain.ui.screens.VideoScreen

@Composable
actual fun FeatureScreen(feature: Feature, onBack: () -> Unit) {
    when (feature.id) {
        "device_info" -> DeviceInfoScreen(onBack)
        "notes" -> NotesScreen(onBack)
        "files" -> FilesScreen(onBack)
        "photos" -> MediaGalleryScreen(onBack)
        "contacts" -> ContactsScreen(onBack)
        "calls" -> CallsScreen(onBack)
        "messages" -> SmsScreen(onBack)
        "apps" -> AppsScreen(onBack)
        "rss" -> FeedsScreen(onBack)
        "bookmarks" -> BookmarksScreen(onBack)
        "tags" -> TagsScreen(onBack)
        "pomodoro" -> PomodoroScreen(onBack)
        "settings" -> SettingsScreen(onBack)
        "audio" -> AudioScreen(onBack)
        "videos" -> VideoScreen(onBack)
        "sound_meter" -> SoundMeterScreen(onBack)
        "nearby" -> NearbyScreen(onBack)
        else -> FeaturePlaceholder(feature, onBack)
    }
}
