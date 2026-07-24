package com.ismartcoding.plain.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.ismartcoding.plain.platform.newId
import kotlinx.serialization.Serializable

/** A P2P chat message. `content` round-trips via [Converters]. */
@Serializable
@Entity(tableName = "chats")
@TypeConverters(Converters::class)
data class DChat(
    @PrimaryKey val id: String = newId(),
    val channelId: String = "",
    val isMe: Boolean = true,
    val content: ChatContent = ChatContent.Text(""),
    val status: Int = 0,          // 0 = ok, 1 = sending, 2 = failed
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** A P2P chat channel. Denylisted from the DB browser (spec §5). */
@Serializable
@Entity(tableName = "chat_channels")
@TypeConverters(Converters::class)
data class DChatChannel(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val members: List<DChannelMember> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

/** A discovered/paired peer device. Denylisted from the DB browser (spec §5). */
@Serializable
@Entity(tableName = "peers")
data class DPeer(
    @PrimaryKey val id: String = newId(),
    val name: String = "",
    val ip: String = "",
    val port: Int = 0,
    val publicKey: String = "",
    val paired: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
