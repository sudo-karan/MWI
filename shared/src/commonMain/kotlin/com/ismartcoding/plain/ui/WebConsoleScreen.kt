package com.ismartcoding.plain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.resources.Res
import com.ismartcoding.plain.resources.back
import com.ismartcoding.plain.resources.feature_web_console
import com.ismartcoding.plain.resources.start_web_console
import com.ismartcoding.plain.resources.stop_web_console
import com.ismartcoding.plain.resources.web_console_hint
import com.ismartcoding.plain.resources.web_console_status_off
import com.ismartcoding.plain.resources.web_console_status_on
import com.ismartcoding.plain.resources.web_offline_hint
import com.ismartcoding.plain.resources.web_open_in_browser
import com.ismartcoding.plain.resources.web_starting
import com.ismartcoding.plain.web.WebServerController
import org.jetbrains.compose.resources.stringResource

/**
 * The one Phase-2 feature wired end-to-end: start/stop the embedded web server and show the LAN URL
 * to open in a desktop browser. Backed by [WebServerController] (expect/actual → foreground service).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebConsoleScreen(onBack: () -> Unit) {
    val running by WebServerController.running.collectAsState()
    val port by WebServerController.sslPort.collectAsState()
    val lanIp = remember(running) { WebServerController.lanAddress() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.feature_web_console)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (running) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            if (running) Res.string.web_console_status_on else Res.string.web_console_status_off,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (running) {
                        Text(stringResource(Res.string.web_open_in_browser), style = MaterialTheme.typography.bodyMedium)
                        val label = when {
                            lanIp == null -> stringResource(Res.string.web_offline_hint)
                            port <= 0 -> stringResource(Res.string.web_starting)
                            else -> "https://$lanIp:$port"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(stringResource(Res.string.web_console_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (running) {
                Button(
                    onClick = { WebServerController.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.stop_web_console))
                }
            } else {
                Button(onClick = { WebServerController.start() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(Res.string.start_web_console))
                }
            }
        }
    }
}
