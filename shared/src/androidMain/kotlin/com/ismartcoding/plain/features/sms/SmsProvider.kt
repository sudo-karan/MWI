package com.ismartcoding.plain.features.sms

import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.SmsManager
import com.ismartcoding.plain.platform.AndroidApp

/** Reads SMS threads/messages and sends SMS via Telephony/SmsManager (spec §6). */
object SmsProvider {

    private val resolver: ContentResolver get() = AndroidApp.context.contentResolver

    fun messages(threadId: String?, offset: Int, limit: Int): List<DSms> {
        val selection = threadId?.let { "${Telephony.Sms.THREAD_ID} = ?" }
        val args = threadId?.let { arrayOf(it) }
        val out = ArrayList<DSms>(limit.coerceAtMost(256))
        pagedQuery(PROJECTION, selection, args, Telephony.Sms.DATE, limit, offset)?.use { c ->
            val i = SmsIndex(c)
            while (c.moveToNext()) out.add(c.toSms(i))
        }
        return out
    }

    fun count(): Int = resolver.query(
        Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID), null, null, null,
    )?.use { it.count } ?: 0

    /** Conversation summaries, grouped from messages (latest-per-thread), then paginated. */
    fun conversations(offset: Int, limit: Int): List<SmsConversation> {
        val convs = LinkedHashMap<String, SmsConversation>()
        resolver.query(Telephony.Sms.CONTENT_URI, PROJECTION, null, null, "${Telephony.Sms.DATE} DESC")?.use { c ->
            val i = SmsIndex(c)
            while (c.moveToNext()) {
                val threadId = c.getStringSafe(i.thread)
                val existing = convs[threadId]
                val read = c.getIntSafe(i.read) == 1
                if (existing == null) {
                    convs[threadId] = SmsConversation(
                        threadId = threadId,
                        address = c.getStringSafe(i.address),
                        snippet = c.getStringSafe(i.body),
                        date = c.getLongSafe(i.date),
                        messageCount = 1,
                        unreadCount = if (read) 0 else 1,
                    )
                } else {
                    convs[threadId] = existing.copy(
                        messageCount = existing.messageCount + 1,
                        unreadCount = existing.unreadCount + if (read) 0 else 1,
                    )
                }
            }
        }
        return convs.values.drop(offset).take(limit)
    }

    fun conversationCount(): Int {
        val threads = HashSet<String>()
        resolver.query(
            Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.THREAD_ID), null, null, null,
        )?.use { c ->
            val idx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
            while (c.moveToNext()) threads.add(c.getString(idx) ?: "")
        }
        return threads.size
    }

    fun sendSms(address: String, body: String, subId: Int): Boolean = runCatching {
        val manager = smsManager(subId)
        val parts = manager.divideMessage(body)
        if (parts.size > 1) {
            manager.sendMultipartTextMessage(address, null, parts, null, null)
        } else {
            manager.sendTextMessage(address, null, body, null, null)
        }
        true
    }.getOrDefault(false)

    // ---- internals ----

    private val PROJECTION = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.THREAD_ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.TYPE,
        Telephony.Sms.READ,
        Telephony.Sms.SUBSCRIPTION_ID,
    )

    private class SmsIndex(c: Cursor) {
        val id = c.getColumnIndex(Telephony.Sms._ID)
        val thread = c.getColumnIndex(Telephony.Sms.THREAD_ID)
        val address = c.getColumnIndex(Telephony.Sms.ADDRESS)
        val body = c.getColumnIndex(Telephony.Sms.BODY)
        val date = c.getColumnIndex(Telephony.Sms.DATE)
        val type = c.getColumnIndex(Telephony.Sms.TYPE)
        val read = c.getColumnIndex(Telephony.Sms.READ)
        val sub = c.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
    }

    private fun Cursor.toSms(i: SmsIndex) = DSms(
        id = getStringSafe(i.id),
        threadId = getStringSafe(i.thread),
        address = getStringSafe(i.address),
        body = getStringSafe(i.body),
        date = getLongSafe(i.date),
        type = SmsFormat.typeName(getIntSafe(i.type)),
        read = getIntSafe(i.read) == 1,
        subId = if (i.sub >= 0) getIntSafe(i.sub) else -1,
    )

    private fun Cursor.getStringSafe(idx: Int) = if (idx >= 0) getString(idx) ?: "" else ""
    private fun Cursor.getLongSafe(idx: Int) = if (idx >= 0) getLong(idx) else 0L
    private fun Cursor.getIntSafe(idx: Int) = if (idx >= 0) getInt(idx) else 0

    @Suppress("DEPRECATION")
    private fun smsManager(subId: Int): SmsManager {
        val ctx = AndroidApp.context
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            SmsManager.getDefault()
        }
        if (subId < 0) return base
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            base.createForSubscriptionId(subId)
        } else {
            SmsManager.getSmsManagerForSubscriptionId(subId)
        }
    }

    private fun pagedQuery(
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
        sortColumn: String,
        limit: Int,
        offset: Int,
    ): Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bundle = Bundle().apply {
            selection?.let { putString(ContentResolver.QUERY_ARG_SQL_SELECTION, it) }
            args?.let { putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, it) }
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortColumn))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
        }
        resolver.query(Telephony.Sms.CONTENT_URI, projection, bundle, null)
    } else {
        @Suppress("DEPRECATION")
        resolver.query(Telephony.Sms.CONTENT_URI, projection, selection, args, "$sortColumn DESC LIMIT $limit OFFSET $offset")
    }
}
