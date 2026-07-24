package com.ismartcoding.plain.db

import kotlinx.serialization.Serializable

/** A file/media attachment reference inside a chat message. */
@Serializable
data class DFileItem(
    val uri: String,
    val size: Long = 0,
    val fileName: String = "",
    val duration: Long = 0,
)

/**
 * Polymorphic chat-message payload. Persisted through a Room [Converters] TypeConverter
 * (sealed classes are serialized by kotlinx.serialization with a `type` discriminator).
 */
@Serializable
sealed class ChatContent {
    @Serializable
    data class Text(val text: String) : ChatContent()

    @Serializable
    data class Images(val items: List<DFileItem>) : ChatContent()

    @Serializable
    data class Files(val items: List<DFileItem>) : ChatContent()
}

/** A member of a P2P chat channel. */
@Serializable
data class DChannelMember(
    val id: String,
    val name: String,
    val publicKey: String = "",
)
