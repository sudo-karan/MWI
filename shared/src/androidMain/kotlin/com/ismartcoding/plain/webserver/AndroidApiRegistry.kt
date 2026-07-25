package com.ismartcoding.plain.webserver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ismartcoding.plain.features.app.AppsProvider
import com.ismartcoding.plain.features.call.CallsProvider
import com.ismartcoding.plain.features.contact.ContactsProvider
import com.ismartcoding.plain.features.device.DeviceInfoProvider
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.features.notification.MwiNotificationListenerService
import com.ismartcoding.plain.features.media.MediaProvider
import com.ismartcoding.plain.features.media.MediaType
import com.ismartcoding.plain.features.sms.SimProvider
import com.ismartcoding.plain.features.sms.SmsProvider
import com.ismartcoding.plain.platform.AndroidApp
import com.ismartcoding.plain.preferences.AppPreferences
import com.ismartcoding.plain.web.WebEventType
import com.ismartcoding.plain.web.api.ApiRegistry
import com.ismartcoding.plain.web.media.MediaQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the operation → resolver [ApiRegistry] with the Android domain providers (spec §6).
 * Resolvers run on the IO dispatcher. Current surface: Device + Files (read + write) + the file URL
 * token; more domains register here as they land.
 */
object AndroidApiRegistry {
    private val json = Json { encodeDefaults = true }

    fun build(): ApiRegistry = ApiRegistry()
        // Device
        .register("deviceInfo") { io { json.encodeToJsonElement(DeviceInfoProvider.info()) } }
        .register("battery") { io { json.encodeToJsonElement(DeviceInfoProvider.battery()) } }
        // Files — read
        .register("mounts") { io { json.encodeToJsonElement(FileService.mounts()) } }
        .register("files") { v -> io { json.encodeToJsonElement(FileService.files(v.str("path"))) } }
        .register("fileInfo") { v -> io { json.encodeToJsonElement(FileService.fileInfo(v.str("path"))) } }
        // Files — write
        .register("deleteFiles") { v -> io { JsonPrimitive(FileService.deleteFiles(v.strList("paths"))) } }
        .register("createDir") { v -> io { JsonPrimitive(FileService.createDir(v.str("path"))) } }
        .register("renameFile") { v -> io { JsonPrimitive(FileService.renameFile(v.str("path"), v.str("newName"))) } }
        .register("copyFile") { v -> io { JsonPrimitive(FileService.copyFile(v.str("src"), v.str("dst"))) } }
        .register("moveFile") { v -> io { JsonPrimitive(FileService.moveFile(v.str("src"), v.str("dst"))) } }
        .register("writeTextFile") { v -> io { JsonPrimitive(FileService.writeTextFile(v.str("path"), v.str("content"))) } }
        // File URL token (for building /fs, /zip, /upload URLs)
        .register("urlToken") { io { JsonPrimitive(AndroidWebServer.urlToken ?: "") } }
        // Media
        .register("images") { v -> io { json.encodeToJsonElement(mediaQuery(MediaType.IMAGE, v)) } }
        .register("imageCount") { v -> io { JsonPrimitive(MediaProvider.count(MediaType.IMAGE, v.optStr("bucketId"))) } }
        .register("videos") { v -> io { json.encodeToJsonElement(mediaQuery(MediaType.VIDEO, v)) } }
        .register("videoCount") { v -> io { JsonPrimitive(MediaProvider.count(MediaType.VIDEO, v.optStr("bucketId"))) } }
        .register("audios") { v -> io { json.encodeToJsonElement(mediaQuery(MediaType.AUDIO, v)) } }
        .register("audioCount") { v -> io { JsonPrimitive(MediaProvider.count(MediaType.AUDIO, v.optStr("bucketId"))) } }
        .register("mediaBuckets") { v -> io { json.encodeToJsonElement(MediaProvider.buckets(mediaType(v))) } }
        // Contacts
        .register("contacts") { v -> io { json.encodeToJsonElement(ContactsProvider.contacts(MediaQuery.clampOffset(v.optInt("offset")), MediaQuery.clampLimit(v.optInt("limit")))) } }
        .register("contactCount") { io { JsonPrimitive(ContactsProvider.count()) } }
        .register("contactSources") { io { json.encodeToJsonElement(ContactsProvider.sources()) } }
        .register("contactGroups") { io { json.encodeToJsonElement(ContactsProvider.groups()) } }
        .register("deleteContacts") { v -> io { JsonPrimitive(ContactsProvider.deleteContacts(v.strList("ids"))) } }
        // SMS
        .register("sms") { v -> io { json.encodeToJsonElement(SmsProvider.messages(v.optStr("threadId"), MediaQuery.clampOffset(v.optInt("offset")), MediaQuery.clampLimit(v.optInt("limit")))) } }
        .register("smsCount") { io { JsonPrimitive(SmsProvider.count()) } }
        .register("smsConversations") { v -> io { json.encodeToJsonElement(SmsProvider.conversations(MediaQuery.clampOffset(v.optInt("offset")), MediaQuery.clampLimit(v.optInt("limit")))) } }
        .register("smsConversationCount") { io { JsonPrimitive(SmsProvider.conversationCount()) } }
        .register("sendSms") { v -> io { JsonPrimitive(SmsProvider.sendSms(v.str("address"), v.str("body"), v.optInt("subId") ?: -1)) } }
        .register("sims") { io { json.encodeToJsonElement(SimProvider.sims()) } }
        // Calls
        .register("calls") { v -> io { json.encodeToJsonElement(CallsProvider.calls(MediaQuery.clampOffset(v.optInt("offset")), MediaQuery.clampLimit(v.optInt("limit")))) } }
        .register("callCount") { io { JsonPrimitive(CallsProvider.count()) } }
        .register("callState") { io { json.encodeToJsonElement(CallsProvider.state()) } }
        .register("call") { v -> io { JsonPrimitive(CallsProvider.call(v.str("number"))) } }
        .register("deleteCalls") { v -> io { JsonPrimitive(CallsProvider.deleteCalls(v.strList("ids"))) } }
        .register("answerCall") { io { JsonPrimitive(CallsProvider.answerCall()) } }
        .register("endCall") { io { JsonPrimitive(CallsProvider.endCall()) } }
        .register("setCallSpeaker") { v -> io { JsonPrimitive(CallsProvider.setSpeaker(v.optBool("on"))) } }
        // Apps
        .register("packages") { v -> io { json.encodeToJsonElement(AppsProvider.packages(MediaQuery.clampOffset(v.optInt("offset")), MediaQuery.clampLimit(v.optInt("limit")))) } }
        .register("packageCount") { io { JsonPrimitive(AppsProvider.count()) } }
        .register("app") { v -> io { json.encodeToJsonElement(AppsProvider.app(v.str("packageName"))) } }
        .register("uninstallPackages") { v -> io { JsonPrimitive(v.strList("packageNames").count { AppsProvider.uninstall(it) }) } }
        .register("relaunchApp") { v -> io { JsonPrimitive(AppsProvider.launch(v.str("packageName"))) } }
        .register("openAccessibilitySettings") { io { JsonPrimitive(AppsProvider.openAccessibilitySettings()) } }
        // Notifications
        .register("notifications") { io { json.encodeToJsonElement(MwiNotificationListenerService.list()) } }
        .register("cancelNotifications") { v -> io { JsonPrimitive(MwiNotificationListenerService.cancel(v.strList("keys"))) } }
        .register("replyNotification") { v -> io { JsonPrimitive(MwiNotificationListenerService.replyTo(v.str("key"), v.str("text"))) } }
        // Device/App mutations (also demonstrate the WS event fan-out)
        .register("updateDeviceName") { v ->
            val name = v.str("name")
            AppPreferences.setDeviceName(name)
            AndroidWebServer.wsHub.broadcast(WebEventType.DEVICE_NAME_UPDATED, name.encodeToByteArray())
            JsonPrimitive(name)
        }
        .register("setClip") { v ->
            val text = v.str("text")
            withContext(Dispatchers.Main) {
                val cm = AndroidApp.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("MWI", text))
            }
            JsonPrimitive(true)
        }

    private fun mediaQuery(type: MediaType, v: JsonObject?) = MediaProvider.query(
        type = type,
        offset = MediaQuery.clampOffset(v.optInt("offset")),
        limit = MediaQuery.clampLimit(v.optInt("limit")),
        bucketId = v.optStr("bucketId"),
    )

    private fun mediaType(v: JsonObject?): MediaType =
        when (v.optStr("type")?.uppercase()) {
            "VIDEO" -> MediaType.VIDEO
            "AUDIO" -> MediaType.AUDIO
            else -> MediaType.IMAGE
        }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun JsonObject?.str(key: String): String =
        this?.get(key)?.jsonPrimitive?.content ?: throw IllegalArgumentException("$key required")

    private fun JsonObject?.strList(key: String): List<String> =
        (this?.get(key) as? JsonArray)?.map { it.jsonPrimitive.content }
            ?: throw IllegalArgumentException("$key required")

    private fun JsonObject?.optStr(key: String): String? =
        this?.get(key)?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

    private fun JsonObject?.optInt(key: String): Int? =
        this?.get(key)?.jsonPrimitive?.content?.toIntOrNull()

    private fun JsonObject?.optBool(key: String): Boolean =
        this?.get(key)?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
}
