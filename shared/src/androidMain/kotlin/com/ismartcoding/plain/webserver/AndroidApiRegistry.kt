package com.ismartcoding.plain.webserver

import com.ismartcoding.plain.features.device.DeviceInfoProvider
import com.ismartcoding.plain.features.file.FileService
import com.ismartcoding.plain.web.api.ApiRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Builds the operation → resolver [ApiRegistry] with the Android domain providers. Resolvers run on
 * the IO dispatcher (platform/filesystem calls). This is the current Phase-3 surface (Device +
 * Files); more domains register here as they land.
 */
object AndroidApiRegistry {
    private val json = Json { encodeDefaults = true }

    fun build(): ApiRegistry = ApiRegistry()
        .register("deviceInfo") { io { json.encodeToJsonElement(DeviceInfoProvider.info()) } }
        .register("battery") { io { json.encodeToJsonElement(DeviceInfoProvider.battery()) } }
        .register("mounts") { io { json.encodeToJsonElement(FileService.mounts()) } }
        .register("files") { vars -> io { json.encodeToJsonElement(FileService.files(vars.requirePath())) } }
        .register("fileInfo") { vars -> io { json.encodeToJsonElement(FileService.fileInfo(vars.requirePath())) } }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun JsonObject?.requirePath(): String =
        this?.get("path")?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("path variable required")
}
