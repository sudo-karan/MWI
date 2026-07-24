package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.features.device.DeviceInfoProvider
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.web.api.ApiRegistry
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

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun JsonObject?.str(key: String): String =
        this?.get(key)?.jsonPrimitive?.content ?: throw IllegalArgumentException("$key required")

    private fun JsonObject?.strList(key: String): List<String> =
        (this?.get(key) as? JsonArray)?.map { it.jsonPrimitive.content }
            ?: throw IllegalArgumentException("$key required")
}
