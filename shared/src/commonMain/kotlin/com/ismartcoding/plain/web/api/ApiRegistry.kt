package com.ismartcoding.plain.web.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement

/**
 * Maps operation names (spec §6) to domain resolvers. A resolver receives the request's typed
 * [variables] and returns the response `data` as a [JsonElement]; it may throw, and [ApiPipeline]
 * turns that into a generic error.
 */
class ApiRegistry {
    private val handlers = LinkedHashMap<String, suspend (JsonObject?) -> JsonElement>()

    fun register(operation: String, handler: suspend (JsonObject?) -> JsonElement): ApiRegistry {
        handlers[operation] = handler
        return this
    }

    val operations: Set<String> get() = handlers.keys

    suspend fun dispatch(request: ApiRequest): ApiResponse {
        val handler = handlers[request.operation]
            ?: return ApiResponse(error = "unknown_operation")
        return ApiResponse(data = handler(request.variables))
    }
}
