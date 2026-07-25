package com.ismartcoding.plain.features.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.ismartcoding.plain.platform.AndroidApp
import com.ismartcoding.plain.webserver.AndroidWebServer
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * LAN peer discovery via mDNS/DNS-SD (`NsdManager`), spec §7. Advertises this device's MWI service
 * (`_mwi._tcp`) when the web server is up, and discovers other MWI instances on the network.
 */
object NearbyDiscovery {
    private const val SERVICE_TYPE = "_mwi._tcp."

    val devices = MutableStateFlow<List<NearbyDevice>>(emptyList())

    @Volatile
    private var running = false
    private var manager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun start(): Boolean {
        if (running) return true
        val nsd = AndroidApp.context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return false
        manager = nsd
        running = true
        devices.value = emptyList()
        registerSelf(nsd)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { running = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                upsert(NearbyDevice(name = info.serviceName))
                resolve(nsd, info)
            }
            override fun onServiceLost(info: NsdServiceInfo) = removeByName(info.serviceName)
        }
        discoveryListener = listener
        return runCatching {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        }.getOrDefault(false)
    }

    fun stop() {
        val nsd = manager ?: return
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registrationListener?.let { runCatching { nsd.unregisterService(it) } }
        discoveryListener = null
        registrationListener = null
        running = false
        devices.value = emptyList()
    }

    private fun resolve(nsd: NsdManager, info: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = resolved.host?.hostAddress ?: return
                upsert(NearbyDevice(name = resolved.serviceName, host = host, port = resolved.port, resolved = true))
            }
        }
        runCatching { nsd.resolveService(info, resolveListener) }
    }

    private fun registerSelf(nsd: NsdManager) {
        val port = AndroidWebServer.sslPort.value.takeIf { it > 0 } ?: return
        val info = NsdServiceInfo().apply {
            serviceName = "MWI-${Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val reg = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        }
        registrationListener = reg
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, reg) }
    }

    private fun upsert(device: NearbyDevice) {
        val existing = devices.value.firstOrNull { it.name == device.name }
        // Keep the richer (resolved) record.
        if (existing != null && existing.resolved && !device.resolved) return
        devices.value = (devices.value.filterNot { it.name == device.name } + device).sortedBy { it.name }
    }

    private fun removeByName(name: String) {
        devices.value = devices.value.filterNot { it.name == name }
    }
}
