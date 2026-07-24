package com.ismartcoding.plain.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.ismartcoding.plain.resources.Res
import com.ismartcoding.plain.resources.feature_app_files
import com.ismartcoding.plain.resources.feature_apps
import com.ismartcoding.plain.resources.feature_audio
import com.ismartcoding.plain.resources.feature_bookmarks
import com.ismartcoding.plain.resources.feature_calls
import com.ismartcoding.plain.resources.feature_cast
import com.ismartcoding.plain.resources.feature_chat
import com.ismartcoding.plain.resources.feature_contacts
import com.ismartcoding.plain.resources.feature_developer
import com.ismartcoding.plain.resources.feature_device_info
import com.ismartcoding.plain.resources.feature_dlna
import com.ismartcoding.plain.resources.feature_documents
import com.ismartcoding.plain.resources.feature_files
import com.ismartcoding.plain.resources.feature_messages
import com.ismartcoding.plain.resources.feature_nearby
import com.ismartcoding.plain.resources.feature_notes
import com.ismartcoding.plain.resources.feature_notifications
import com.ismartcoding.plain.resources.feature_photos
import com.ismartcoding.plain.resources.feature_pomodoro
import com.ismartcoding.plain.resources.feature_qr
import com.ismartcoding.plain.resources.feature_rss
import com.ismartcoding.plain.resources.feature_screen_mirror
import com.ismartcoding.plain.resources.feature_settings
import com.ismartcoding.plain.resources.feature_sound_meter
import com.ismartcoding.plain.resources.feature_tags
import com.ismartcoding.plain.resources.feature_videos
import com.ismartcoding.plain.resources.feature_web_console
import org.jetbrains.compose.resources.StringResource

/** High-level grouping used for section headers on the home grid. */
enum class FeatureCategory { HUB, MEDIA, COMMS, TOOLS, SYSTEM }

/**
 * A tile on the home feature grid (spec §7A). `implemented` distinguishes what already works in
 * the current build from what is scaffolded — the UI can badge the difference honestly.
 */
data class Feature(
    val id: String,
    val label: StringResource,
    val icon: ImageVector,
    val category: FeatureCategory,
    val implemented: Boolean = false,
)

/** The full home grid. Order mirrors the spec's feature list. */
val AllFeatures: List<Feature> = listOf(
    Feature("web_console", Res.string.feature_web_console, Icons.Outlined.Dns, FeatureCategory.HUB, implemented = true),

    Feature("files", Res.string.feature_files, Icons.Outlined.Folder, FeatureCategory.MEDIA),
    Feature("photos", Res.string.feature_photos, Icons.Outlined.Photo, FeatureCategory.MEDIA),
    Feature("videos", Res.string.feature_videos, Icons.Outlined.Movie, FeatureCategory.MEDIA),
    Feature("audio", Res.string.feature_audio, Icons.Outlined.MusicNote, FeatureCategory.MEDIA),

    Feature("contacts", Res.string.feature_contacts, Icons.Outlined.Contacts, FeatureCategory.COMMS),
    Feature("messages", Res.string.feature_messages, Icons.Outlined.Sms, FeatureCategory.COMMS),
    Feature("calls", Res.string.feature_calls, Icons.Outlined.Call, FeatureCategory.COMMS),
    Feature("notifications", Res.string.feature_notifications, Icons.Outlined.Notifications, FeatureCategory.COMMS),
    Feature("chat", Res.string.feature_chat, Icons.Outlined.Chat, FeatureCategory.COMMS),
    Feature("nearby", Res.string.feature_nearby, Icons.Outlined.Devices, FeatureCategory.COMMS),

    Feature("apps", Res.string.feature_apps, Icons.Outlined.Apps, FeatureCategory.SYSTEM),
    Feature("screen_mirror", Res.string.feature_screen_mirror, Icons.Outlined.ScreenShare, FeatureCategory.SYSTEM),
    Feature("device_info", Res.string.feature_device_info, Icons.Outlined.PhoneAndroid, FeatureCategory.SYSTEM),

    Feature("notes", Res.string.feature_notes, Icons.Outlined.EditNote, FeatureCategory.TOOLS),
    Feature("rss", Res.string.feature_rss, Icons.Outlined.RssFeed, FeatureCategory.TOOLS),
    Feature("documents", Res.string.feature_documents, Icons.Outlined.Article, FeatureCategory.TOOLS),
    Feature("app_files", Res.string.feature_app_files, Icons.Outlined.FolderSpecial, FeatureCategory.TOOLS),
    Feature("bookmarks", Res.string.feature_bookmarks, Icons.Outlined.Bookmark, FeatureCategory.TOOLS),
    Feature("tags", Res.string.feature_tags, Icons.Outlined.Label, FeatureCategory.TOOLS),
    Feature("pomodoro", Res.string.feature_pomodoro, Icons.Outlined.Timer, FeatureCategory.TOOLS),
    Feature("sound_meter", Res.string.feature_sound_meter, Icons.Outlined.GraphicEq, FeatureCategory.TOOLS),
    Feature("qr", Res.string.feature_qr, Icons.Outlined.QrCodeScanner, FeatureCategory.TOOLS),
    Feature("cast", Res.string.feature_cast, Icons.Outlined.Cast, FeatureCategory.TOOLS),
    Feature("dlna", Res.string.feature_dlna, Icons.Outlined.CastConnected, FeatureCategory.TOOLS),

    Feature("developer", Res.string.feature_developer, Icons.Outlined.Code, FeatureCategory.SYSTEM),
    Feature("settings", Res.string.feature_settings, Icons.Outlined.Settings, FeatureCategory.SYSTEM),
)
