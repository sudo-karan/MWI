package com.ismartcoding.plain.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.resources.Res
import com.ismartcoding.plain.resources.approve
import com.ismartcoding.plain.resources.back
import com.ismartcoding.plain.resources.cancel
import com.ismartcoding.plain.resources.feature_web_console
import com.ismartcoding.plain.resources.reject
import com.ismartcoding.plain.resources.save
import com.ismartcoding.plain.resources.start_web_console
import com.ismartcoding.plain.resources.stop_web_console
import com.ismartcoding.plain.resources.web_change_password
import com.ismartcoding.plain.resources.web_console_hint
import com.ismartcoding.plain.resources.web_new_password
import com.ismartcoding.plain.resources.web_password_hint
import com.ismartcoding.plain.resources.web_set_password_title
import com.ismartcoding.plain.resources.web_console_status_off
import com.ismartcoding.plain.resources.web_console_status_on
import com.ismartcoding.plain.resources.web_login_password
import com.ismartcoding.plain.resources.web_offline_hint
import com.ismartcoding.plain.resources.web_open_in_browser
import com.ismartcoding.plain.resources.web_pending_login
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
    val password by WebServerController.loginPassword.collectAsState()
    val approvals by WebServerController.pendingApprovals.collectAsState()
    val lanIp = remember(running) { WebServerController.lanAddress() }

    var showPasswordDialog by remember { mutableStateOf(false) }

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
                        password?.let { pw ->
                            Text(
                                text = stringResource(Res.string.web_login_password),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = pw,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton(
                                onClick = { showPasswordDialog = true },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(stringResource(Res.string.web_change_password))
                            }
                        }
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

            // On-device 2FA: approve/reject browser logins (spec §5).
            approvals.forEach { approval ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(Res.string.web_pending_login), fontWeight = FontWeight.SemiBold)
                        val who = listOf(approval.browserName, approval.osName)
                            .filter { it.isNotBlank() }.joinToString(" · ")
                        if (who.isNotBlank()) {
                            Text(who, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { WebServerController.approve(approval.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(Res.string.approve)) }
                            Button(
                                onClick = { WebServerController.reject(approval.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(Res.string.reject)) }
                        }
                    }
                }
            }

            if (showPasswordDialog) {
                var newPassword by remember { mutableStateOf("") }
                val valid = newPassword.trim().length >= 6
                AlertDialog(
                    onDismissRequest = { showPasswordDialog = false },
                    title = { Text(stringResource(Res.string.web_set_password_title)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                singleLine = true,
                                label = { Text(stringResource(Res.string.web_new_password)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(Res.string.web_password_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = valid,
                            onClick = {
                                WebServerController.setPassword(newPassword.trim())
                                showPasswordDialog = false
                            },
                        ) { Text(stringResource(Res.string.save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPasswordDialog = false }) {
                            Text(stringResource(Res.string.cancel))
                        }
                    },
                )
            }
        }
    }
}
