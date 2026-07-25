package com.ismartcoding.plain.webserver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ismartcoding.plain.db.AppDb
import com.ismartcoding.plain.db.ChatContent
import com.ismartcoding.plain.db.DBookmark
import com.ismartcoding.plain.db.DChat
import com.ismartcoding.plain.db.DChatChannel
import com.ismartcoding.plain.db.DBookmarkGroup
import com.ismartcoding.plain.db.DFeed
import com.ismartcoding.plain.db.DNote
import com.ismartcoding.plain.db.DPomodoroItem
import com.ismartcoding.plain.db.DTag
import com.ismartcoding.plain.db.DTagRelation
import com.ismartcoding.plain.features.app.AppsProvider
import com.ismartcoding.plain.platform.epochMillis
import com.ismartcoding.plain.platform.newId
import com.ismartcoding.plain.features.call.CallsProvider
import com.ismartcoding.plain.features.contact.ContactsProvider
import com.ismartcoding.plain.features.device.DeviceInfoProvider
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.features.notification.MwiNotificationListenerService
import com.ismartcoding.plain.features.screenmirror.ScreenMirror
import com.ismartcoding.plain.features.screenmirror.ScreenMirrorControl
import com.ismartcoding.plain.features.media.MediaProvider
import com.ismartcoding.plain.features.media.MediaType
import com.ismartcoding.plain.features.nearby.NearbyDiscovery
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
    private val db get() = AppDb.instance

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
        // ---- Standalone tools (Room-backed) ----
        // Notes
        .register("notes") { v -> json.encodeToJsonElement(db.noteDao().page(MediaQuery.clampLimit(v.optInt("limit")), MediaQuery.clampOffset(v.optInt("offset")))) }
        .register("noteCount") { JsonPrimitive(db.noteDao().count()) }
        .register("note") { v -> json.encodeToJsonElement(db.noteDao().getById(v.str("id"))) }
        .register("createNote") { v ->
            val now = epochMillis()
            val note = DNote(id = newId(), title = v.optStr("title") ?: "", content = v.optStr("content") ?: "", createdAt = now, updatedAt = now)
            db.noteDao().upsert(note)
            json.encodeToJsonElement(note)
        }
        .register("updateNote") { v ->
            val existing = db.noteDao().getById(v.str("id")) ?: throw IllegalArgumentException("not found")
            val updated = existing.copy(title = v.optStr("title") ?: existing.title, content = v.optStr("content") ?: existing.content, updatedAt = epochMillis())
            db.noteDao().upsert(updated)
            json.encodeToJsonElement(updated)
        }
        .register("deleteNote") { v -> db.noteDao().deleteById(v.str("id")); JsonPrimitive(true) }
        // Bookmarks
        .register("bookmarkGroups") { json.encodeToJsonElement(db.bookmarkGroupDao().getAll()) }
        .register("createBookmarkGroup") { v ->
            val now = epochMillis()
            val g = DBookmarkGroup(id = newId(), name = v.str("name"), createdAt = now, updatedAt = now)
            db.bookmarkGroupDao().upsert(g); json.encodeToJsonElement(g)
        }
        .register("deleteBookmarkGroup") { v -> db.bookmarkGroupDao().deleteById(v.str("id")); JsonPrimitive(true) }
        .register("bookmarks") { v ->
            val groupId = v.optStr("groupId")
            val list = if (groupId != null) db.bookmarkDao().getByGroup(groupId) else db.bookmarkDao().getAll()
            json.encodeToJsonElement(list)
        }
        .register("createBookmark") { v ->
            val now = epochMillis()
            val b = DBookmark(id = newId(), groupId = v.optStr("groupId") ?: "", title = v.str("title"), url = v.str("url"), icon = v.optStr("icon") ?: "", createdAt = now, updatedAt = now)
            db.bookmarkDao().upsert(b); json.encodeToJsonElement(b)
        }
        .register("deleteBookmark") { v -> db.bookmarkDao().deleteById(v.str("id")); JsonPrimitive(true) }
        // Tags
        .register("tags") { v -> json.encodeToJsonElement(db.tagDao().getByType(v.optInt("type") ?: 0)) }
        .register("createTag") { v ->
            val now = epochMillis()
            val t = DTag(id = newId(), name = v.str("name"), type = v.optInt("type") ?: 0, createdAt = now, updatedAt = now)
            db.tagDao().upsert(t); json.encodeToJsonElement(t)
        }
        .register("deleteTag") { v -> db.tagDao().deleteById(v.str("id")); JsonPrimitive(true) }
        .register("tagRelations") { v -> json.encodeToJsonElement(db.tagRelationDao().getByTag(v.str("tagId"))) }
        .register("addTagRelation") { v ->
            val r = DTagRelation(id = newId(), tagId = v.str("tagId"), key = v.str("key"), type = v.optInt("type") ?: 0)
            db.tagRelationDao().insert(r); json.encodeToJsonElement(r)
        }
        .register("deleteTagRelation") { v -> db.tagRelationDao().delete(v.str("tagId"), v.str("key")); JsonPrimitive(true) }
        // Pomodoro
        .register("pomodoros") { v -> json.encodeToJsonElement(db.pomodoroDao().recent(MediaQuery.clampLimit(v.optInt("limit")))) }
        .register("createPomodoro") { v ->
            val item = DPomodoroItem(id = newId(), durationSeconds = v.optInt("durationSeconds") ?: 0, kind = v.optInt("kind") ?: 0, startedAt = v.optInt("startedAt")?.toLong() ?: epochMillis(), completedAt = v.optInt("completedAt")?.toLong(), createdAt = epochMillis())
            db.pomodoroDao().upsert(item); json.encodeToJsonElement(item)
        }
        // Feeds
        .register("feeds") { json.encodeToJsonElement(db.feedDao().getAll()) }
        .register("feedsCount") { JsonPrimitive(db.feedDao().count()) }
        .register("feed") { v -> json.encodeToJsonElement(db.feedDao().getById(v.str("id"))) }
        .register("createFeed") { v ->
            val now = epochMillis()
            val f = DFeed(id = newId(), url = v.str("url"), name = v.optStr("name") ?: "", fetchContent = v.optBool("fetchContent"), createdAt = now, updatedAt = now)
            db.feedDao().upsert(f); json.encodeToJsonElement(f)
        }
        .register("updateFeed") { v ->
            val existing = db.feedDao().getById(v.str("id")) ?: throw IllegalArgumentException("not found")
            val updated = existing.copy(name = v.optStr("name") ?: existing.name, fetchContent = v.optBool("fetchContent"), updatedAt = epochMillis())
            db.feedDao().upsert(updated); json.encodeToJsonElement(updated)
        }
        .register("deleteFeed") { v -> db.feedDao().deleteById(v.str("id")); JsonPrimitive(true) }
        .register("feedEntries") { v -> json.encodeToJsonElement(db.feedEntryDao().getByFeed(v.str("feedId"), MediaQuery.clampLimit(v.optInt("limit")), MediaQuery.clampOffset(v.optInt("offset")))) }
        .register("feedEntryCount") { v -> JsonPrimitive(db.feedEntryDao().countByFeed(v.str("feedId"))) }
        .register("feedEntry") { v -> json.encodeToJsonElement(db.feedEntryDao().getById(v.str("id"))) }
        // Screen mirror
        .register("screenMirrorState") { json.encodeToJsonElement(ScreenMirror.info()) }
        .register("screenMirrorQuality") { JsonPrimitive(ScreenMirror.quality.value) }
        .register("screenMirrorVideoCodec") { JsonPrimitive(ScreenMirror.codec.value) }
        .register("screenMirrorControlEnabled") { JsonPrimitive(ScreenMirror.controlEnabled.value) }
        .register("startScreenMirror") { ScreenMirror.requestStart(); JsonPrimitive(true) }
        .register("stopScreenMirror") { ScreenMirror.stop(); JsonPrimitive(true) }
        .register("updateScreenMirrorQuality") { v -> ScreenMirror.updateQuality(v.str("quality")); JsonPrimitive(ScreenMirror.quality.value) }
        .register("sendScreenMirrorControl") { v ->
            val control = ScreenMirrorControl(
                type = v.str("type"),
                x = v.optFloat("x"),
                y = v.optFloat("y"),
                x2 = v.optFloat("x2"),
                y2 = v.optFloat("y2"),
                durationMs = v.optInt("durationMs")?.toLong() ?: 0,
                key = v.optStr("key") ?: "",
                text = v.optStr("text") ?: "",
            )
            JsonPrimitive(ScreenMirror.control(control))
        }
        // Chat (P2P / local)
        .register("chatChannels") { json.encodeToJsonElement(db.chatChannelDao().getAll()) }
        .register("createChatChannel") { v ->
            val now = epochMillis()
            val c = DChatChannel(id = newId(), name = v.str("name"), createdAt = now, updatedAt = now)
            db.chatChannelDao().upsert(c); json.encodeToJsonElement(c)
        }
        .register("deleteChatChannel") { v -> db.chatChannelDao().deleteById(v.str("id")); JsonPrimitive(true) }
        .register("chatItems") { v -> json.encodeToJsonElement(db.chatDao().getByChannel(v.str("channelId"), MediaQuery.clampLimit(v.optInt("limit")), MediaQuery.clampOffset(v.optInt("offset")))) }
        .register("sendChat") { v ->
            val now = epochMillis()
            val chat = DChat(id = newId(), channelId = v.str("channelId"), isMe = true, content = ChatContent.Text(v.str("text")), createdAt = now, updatedAt = now)
            db.chatDao().upsert(chat)
            db.chatChannelDao().getById(v.str("channelId"))?.let { db.chatChannelDao().upsert(it.copy(updatedAt = now)) }
            AndroidWebServer.wsHub.broadcast(com.ismartcoding.plain.web.WebEventType.MESSAGE_CREATED, ByteArray(0))
            json.encodeToJsonElement(chat)
        }
        .register("deleteChat") { v -> db.chatDao().deleteById(v.str("id")); JsonPrimitive(true) }
        // Nearby devices (mDNS)
        .register("nearbyDevices") { json.encodeToJsonElement(NearbyDiscovery.devices.value) }
        .register("startNearbyDiscovery") { JsonPrimitive(NearbyDiscovery.start()) }
        .register("stopNearbyDiscovery") { NearbyDiscovery.stop(); JsonPrimitive(true) }
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

    private fun JsonObject?.optFloat(key: String): Float =
        this?.get(key)?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
}
