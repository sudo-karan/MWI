package com.ismartcoding.plain.features.dlna

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Sends AVTransport SOAP actions to a DLNA renderer's control URL (spec §7 TV Cast). */
object DlnaCaster {
    private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"
    private val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    /** Set the media URI on the renderer and start playback. Returns true on success. */
    fun cast(controlUrl: String, mediaUrl: String): Boolean {
        val set = soap(
            controlUrl, "SetAVTransportURI",
            "<InstanceID>0</InstanceID><CurrentURI>${escape(mediaUrl)}</CurrentURI><CurrentURIMetaData></CurrentURIMetaData>",
        )
        if (!set) return false
        return soap(controlUrl, "Play", "<InstanceID>0</InstanceID><Speed>1</Speed>")
    }

    fun stop(controlUrl: String): Boolean = soap(controlUrl, "Stop", "<InstanceID>0</InstanceID>")

    private fun soap(controlUrl: String, action: String, inner: String): Boolean {
        val envelope = """<?xml version="1.0" encoding="utf-8"?>""" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$SERVICE\">$inner</u:$action></s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(controlUrl)
            .header("SOAPACTION", "\"$SERVICE#$action\"")
            .post(envelope.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .build()
        return runCatching { http.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
