package com.ismartcoding.plain.web.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A single API operation the browser invokes (spec §6). The dashboard's queries/mutations are
 * addressed by [operation] name with typed [variables]; the server dispatches to a domain resolver.
 */
@Serializable
data class ApiRequest(
    val operation: String,
    val variables: JsonObject? = null,
)

/** The result of an operation — either [data] or a generic [error] string (never leaks internals). */
@Serializable
data class ApiResponse(
    val data: JsonElement? = null,
    val error: String? = null,
)
