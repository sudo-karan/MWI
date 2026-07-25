package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.features.app.AppsProvider
import com.ismartcoding.plain.features.call.CallsProvider
import com.ismartcoding.plain.features.contact.ContactsProvider
import com.ismartcoding.plain.features.sms.SmsProvider

@Composable
fun ContactsScreen(onBack: () -> Unit) = ListScreen(
    title = "Contacts",
    onBack = onBack,
    load = { ContactsProvider.contacts(0, 500) },
    key = { it.id },
) { c ->
    TwoLineRow(
        primary = c.displayName.ifBlank { "(no name)" },
        secondary = c.phones.firstOrNull()?.value ?: c.emails.firstOrNull()?.value ?: "",
    )
}

@Composable
fun CallsScreen(onBack: () -> Unit) = ListScreen(
    title = "Calls",
    onBack = onBack,
    load = { CallsProvider.calls(0, 500) },
    key = { it.id },
) { call ->
    TwoLineRow(
        primary = call.name.ifBlank { call.number.ifBlank { "Unknown" } },
        secondary = "${call.type} · ${fmtDuration(call.duration)}",
    )
}

@Composable
fun SmsScreen(onBack: () -> Unit) = ListScreen(
    title = "Messages",
    onBack = onBack,
    load = { SmsProvider.conversations(0, 500) },
    key = { it.threadId },
) { conv ->
    TwoLineRow(
        primary = conv.address.ifBlank { "(unknown)" } + if (conv.unreadCount > 0) "  •${conv.unreadCount}" else "",
        secondary = conv.snippet,
    )
}

@Composable
fun AppsScreen(onBack: () -> Unit) = ListScreen(
    title = "Apps",
    onBack = onBack,
    load = { AppsProvider.packages(0, 1000) },
    key = { it.packageName },
) { pkg ->
    TwoLineRow(
        primary = pkg.label + if (pkg.system) "  ·sys" else "",
        secondary = "${pkg.packageName} · v${pkg.versionName}",
    )
}

@Composable
private fun TwoLineRow(primary: String, secondary: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(primary, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (secondary.isNotBlank()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun fmtDuration(seconds: Long): String {
    if (seconds <= 0) return "—"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
