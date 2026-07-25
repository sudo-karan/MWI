package com.ismartcoding.plain.features.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ismartcoding.plain.platform.AndroidApp

/** Lists installed packages and drives basic app control (spec §6 Apps/Device). */
object AppsProvider {

    private val pm: PackageManager get() = AndroidApp.context.packageManager

    fun packages(offset: Int, limit: Int, includeSystem: Boolean = true): List<DPackage> =
        allPackages()
            .filter { includeSystem || (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }
            .map { it.toDPackage() }
            .sortedBy { it.label.lowercase() }
            .drop(offset)
            .take(limit)

    fun count(includeSystem: Boolean = true): Int =
        allPackages().count { includeSystem || (it.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) == 0 }

    fun app(packageName: String): DPackage? = runCatching {
        packageInfo(packageName).toDPackage()
    }.getOrNull()

    /** Launch the system uninstall dialog (REQUEST_DELETE_PACKAGES). */
    fun uninstall(packageName: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidApp.context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun launch(packageName: String): Boolean = runCatching {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidApp.context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun openAccessibilitySettings(): Boolean = runCatching {
        AndroidApp.context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    // ---- internals ----

    @Suppress("DEPRECATION", "QueryPermissionsNeeded")
    private fun allPackages(): List<PackageInfo> = pm.getInstalledPackages(0)

    @Suppress("DEPRECATION")
    private fun packageInfo(packageName: String): PackageInfo = pm.getPackageInfo(packageName, 0)

    private fun PackageInfo.toDPackage(): DPackage {
        val ai = applicationInfo
        return DPackage(
            packageName = packageName,
            label = ai?.let { pm.getApplicationLabel(it).toString() } ?: packageName,
            versionName = versionName ?: "",
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode
            else @Suppress("DEPRECATION") versionCode.toLong(),
            system = (ai?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0,
            enabled = ai?.enabled ?: true,
            firstInstall = firstInstallTime,
            lastUpdate = lastUpdateTime,
        )
    }
}
