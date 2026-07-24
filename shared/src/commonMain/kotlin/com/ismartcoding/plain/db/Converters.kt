package com.ismartcoding.plain.db

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/**
 * Room type converters (spec §10). Timestamps are stored as epoch-millis `Long`s (no converter
 * needed); the non-primitive columns — chat content, channel members, and string lists — round-trip
 * through kotlinx.serialization JSON.
 */
class Converters {
    @TypeConverter
    fun fromChatContent(value: ChatContent): String = json.encodeToString(value)

    @TypeConverter
    fun toChatContent(value: String): ChatContent = json.decodeFromString(value)

    @TypeConverter
    fun fromMembers(value: List<DChannelMember>): String = json.encodeToString(value)

    @TypeConverter
    fun toMembers(value: String): List<DChannelMember> = json.decodeFromString(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
