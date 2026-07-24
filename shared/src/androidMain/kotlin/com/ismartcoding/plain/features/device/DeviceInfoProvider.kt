package com.ismartcoding.plain.features.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.ismartcoding.plain.platform.AndroidApp
import com.ismartcoding.plain.preferences.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Reads device + battery info from the Android platform (spec §6 Apps/Device). */
object DeviceInfoProvider {

    fun info(): DeviceInfo {
        val ctx = AndroidApp.context
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val stat = StatFs(Environment.getDataDirectory().path)
        val savedName = runBlocking(Dispatchers.IO) { AppPreferences.getDeviceName() }

        return DeviceInfo(
            deviceName = savedName.ifEmpty { Build.MODEL },
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            product = Build.PRODUCT,
            device = Build.DEVICE,
            board = Build.BOARD,
            osVersion = Build.VERSION.RELEASE ?: "",
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            totalStorage = stat.totalBytes,
            availableStorage = stat.availableBytes,
            totalMemory = mem.totalMem,
            availableMemory = mem.availMem,
        )
    }

    fun battery(): BatteryInfo {
        val ctx = AndroidApp.context
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return BatteryInfo(
            level = if (scale > 0) level * 100 / scale else 0,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            temperatureC = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f,
            voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
            health = healthName(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1),
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "",
        )
    }

    private fun healthName(code: Int): String = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over_voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "cold"
        else -> "unknown"
    }
}
