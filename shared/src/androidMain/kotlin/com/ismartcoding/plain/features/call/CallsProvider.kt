package com.ismartcoding.plain.features.call

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.TelecomManager
import com.ismartcoding.plain.platform.AndroidApp

/** Reads the call log and drives basic call control (spec §6 Calls). */
object CallsProvider {

    private val resolver: ContentResolver get() = AndroidApp.context.contentResolver

    fun calls(offset: Int, limit: Int): List<DCall> {
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
        )
        val out = ArrayList<DCall>(limit.coerceAtMost(256))
        pagedQuery(projection, CallLog.Calls.DATE, limit, offset)?.use { c ->
            val id = c.getColumnIndex(CallLog.Calls._ID)
            val number = c.getColumnIndex(CallLog.Calls.NUMBER)
            val name = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val date = c.getColumnIndex(CallLog.Calls.DATE)
            val duration = c.getColumnIndex(CallLog.Calls.DURATION)
            val type = c.getColumnIndex(CallLog.Calls.TYPE)
            while (c.moveToNext()) {
                out.add(
                    DCall(
                        id = if (id >= 0) c.getLong(id).toString() else "",
                        number = if (number >= 0) c.getString(number) ?: "" else "",
                        name = if (name >= 0) c.getString(name) ?: "" else "",
                        date = if (date >= 0) c.getLong(date) else 0,
                        duration = if (duration >= 0) c.getLong(duration) else 0,
                        type = CallFormat.typeName(if (type >= 0) c.getInt(type) else 0),
                    ),
                )
            }
        }
        return out
    }

    fun count(): Int = resolver.query(
        CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID), null, null, null,
    )?.use { it.count } ?: 0

    fun deleteCalls(ids: List<String>): Int {
        if (ids.isEmpty()) return 0
        val placeholders = ids.joinToString(",") { "?" }
        return resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN ($placeholders)", ids.toTypedArray())
    }

    /** Place a call via the dialer (ACTION_CALL requires CALL_PHONE). */
    fun call(number: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidApp.context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun answerCall(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            telecom().acceptRingingCall()
            true
        } else {
            false
        }
    }.getOrDefault(false)

    fun endCall(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            telecom().endCall()
        } else {
            false
        }
    }.getOrDefault(false)

    fun setSpeaker(on: Boolean): Boolean = runCatching {
        @Suppress("DEPRECATION")
        audio().isSpeakerphoneOn = on
        true
    }.getOrDefault(false)

    fun state(): CallState {
        val am = audio()
        val state = when (am.mode) {
            AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION -> "offhook"
            AudioManager.MODE_RINGTONE -> "ringing"
            else -> "idle"
        }
        @Suppress("DEPRECATION")
        return CallState(state = state, speakerOn = am.isSpeakerphoneOn)
    }

    // ---- internals ----

    private fun telecom() =
        AndroidApp.context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    private fun audio() =
        AndroidApp.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun pagedQuery(projection: Array<String>, sortColumn: String, limit: Int, offset: Int): Cursor? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bundle = Bundle().apply {
                putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sortColumn))
                putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            }
            resolver.query(CallLog.Calls.CONTENT_URI, projection, bundle, null)
        } else {
            @Suppress("DEPRECATION")
            resolver.query(CallLog.Calls.CONTENT_URI, projection, null, null, "$sortColumn DESC LIMIT $limit OFFSET $offset")
        }
}
