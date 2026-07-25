package com.ismartcoding.plain.features.dlna

import android.content.Context
import android.net.wifi.WifiManager
import com.ismartcoding.plain.platform.AndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled DLNA/UPnP MediaRenderer discovery via SSDP (spec §7). Sends an M-SEARCH multicast,
 * collects responders, fetches each device description, and extracts the AVTransport control URL.
 */
object DlnaDiscovery {
    private const val SSDP_ADDR = "239.255.255.250"
    private const val SSDP_PORT = 1900
    private const val TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"

    val renderers = MutableStateFlow<List<DlnaRenderer>>(emptyList())

    // LanOnlyDns confines the LOCATION fetch to LAN IPs — an SSDP responder controls that URL.
    private val http = OkHttpClient.Builder()
        .dns(LanOnlyDns)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var running = false
    private var scope: CoroutineScope? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(): Boolean {
        if (running) return true
        running = true
        renderers.value = emptyList()
        acquireMulticastLock()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch { discover() }
        return true
    }

    fun stop() {
        running = false
        scope?.cancel()
        scope = null
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun discover() {
        runCatching {
            DatagramSocket().use { socket ->
                socket.reuseAddress = true
                socket.soTimeout = 2000
                val query = buildString {
                    append("M-SEARCH * HTTP/1.1\r\n")
                    append("HOST: $SSDP_ADDR:$SSDP_PORT\r\n")
                    append("MAN: \"ssdp:discover\"\r\n")
                    append("MX: 2\r\n")
                    append("ST: $TARGET\r\n\r\n")
                }.toByteArray()
                val group = InetAddress.getByName(SSDP_ADDR)
                repeat(2) { runCatching { socket.send(DatagramPacket(query, query.size, group, SSDP_PORT)) } }

                val buf = ByteArray(4096)
                val end = System.currentTimeMillis() + 4000
                while (running && System.currentTimeMillis() < end) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    header(text, "LOCATION")?.let { fetchDevice(it) }
                }
            }
        }
    }

    private fun fetchDevice(location: String) {
        runCatching {
            val xml = http.newCall(Request.Builder().url(location).build()).execute().use { it.body?.string() }
                ?: return
            val name = tag(xml, "friendlyName") ?: "Renderer"
            val udn = tag(xml, "UDN") ?: location
            val control = avTransportControlUrl(xml) ?: return
            upsert(DlnaRenderer(udn = udn, name = name, location = location, controlUrl = resolve(location, control)))
        }
    }

    // ---- parsing helpers ----

    private fun header(response: String, name: String): String? = response.lineSequence()
        .firstOrNull { it.startsWith("$name:", ignoreCase = true) }
        ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }

    private fun tag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)?.trim()

    /** Find the controlURL inside the service block whose serviceType contains AVTransport. */
    private fun avTransportControlUrl(xml: String): String? {
        val services = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        for (m in services) {
            val block = m.groupValues[1]
            if (block.contains("AVTransport")) {
                return tag(block, "controlURL")
            }
        }
        return null
    }

    private fun resolve(base: String, path: String): String =
        if (path.startsWith("http", ignoreCase = true)) path else runCatching { URI(base).resolve(path).toString() }.getOrDefault(path)

    private fun upsert(renderer: DlnaRenderer) {
        renderers.value = (renderers.value.filterNot { it.udn == renderer.udn } + renderer).sortedBy { it.name }
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifi = AndroidApp.context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("mwi-ssdp").apply {
                setReferenceCounted(true)
                acquire()
            }
        }
    }
}
