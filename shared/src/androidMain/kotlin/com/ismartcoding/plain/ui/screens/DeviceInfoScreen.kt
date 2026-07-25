package com.ismartcoding.plain.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ismartcoding.plain.features.device.BatteryInfo
import com.ismartcoding.plain.features.device.DeviceInfo
import com.ismartcoding.plain.features.device.DeviceInfoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(onBack: () -> Unit) {
    var info by remember { mutableStateOf<DeviceInfo?>(null) }
    var battery by remember { mutableStateOf<BatteryInfo?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            info = runCatching { DeviceInfoProvider.info() }.getOrNull()
            battery = runCatching { DeviceInfoProvider.battery() }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.padding(inner).padding(horizontal = 16.dp)) {
            info?.let { i ->
                item { Row2("Device name", i.deviceName) }
                item { Row2("Model", i.model) }
                item { Row2("Manufacturer", i.manufacturer) }
                item { Row2("Android", "${i.osVersion} (API ${i.sdkInt})") }
                item { Row2("ABIs", i.abis.joinToString(", ")) }
                item { Row2("Storage", "${fmtBytes(i.availableStorage)} free / ${fmtBytes(i.totalStorage)}") }
                item { Row2("Memory", "${fmtBytes(i.availableMemory)} free / ${fmtBytes(i.totalMemory)}") }
            }
            battery?.let { b ->
                item {
                    Row2("Battery", "${b.level}%${if (b.charging) " · charging" else ""} · ${b.health}")
                }
            }
        }
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}
