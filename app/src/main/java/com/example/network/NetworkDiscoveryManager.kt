package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * v1.8.0 (#6): safe LAN auto-discovery for network storage.
 *
 * - Framework NsdManager only (no new deps, minSdk 24 compatible).
 * - Discovers `_smb._tcp.` and `_http._tcp.` (WebDAV hints) via mDNS.
 * - Single-shot scan with 25s auto-stop, MulticastLock acquired/released
 *   in try/finally, in-memory results only (never writes DataStore).
 * - All NSD callbacks run on the main looper: zero I/O there; resolve
 *   results are deduped by host:port and exposed as immutable StateFlow.
 * - Every framework call is runCatching-guarded (missing permission,
 *   already-active, max-limit) so discovery can never crash the app.
 */
data class DiscoveredDevice(
    val host: String,
    val port: Int,
    val serviceName: String,
    val hint: NetworkProtocol?,
)

class NetworkDiscoveryManager(private val appContext: Context) {

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var smbListener: NsdManager.DiscoveryListener? = null
    private var httpListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    fun start() {
        if (_scanning.value) return
        _devices.update { emptyList() }
        val nsd = runCatching {
            appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }.getOrNull() ?: return
        nsdManager = nsd
        acquireLock()
        _scanning.update { true }
        smbListener = makeListener(nsd, "_smb._tcp.", NetworkProtocol.SMB)
        httpListener = makeListener(nsd, "_http._tcp.", NetworkProtocol.WEBDAV)
        smbListener?.let { runCatching { nsd.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, it) } }
        httpListener?.let { runCatching { nsd.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, it) } }
    }

    fun stop() {
        if (!_scanning.value && smbListener == null && httpListener == null) {
            releaseLock()
            return
        }
        val nsd = nsdManager
        smbListener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        httpListener?.let { runCatching { nsd?.stopServiceDiscovery(it) } }
        smbListener = null
        httpListener = null
        nsdManager = null
        _scanning.update { false }
        releaseLock()
    }

    private fun makeListener(
        nsd: NsdManager,
        serviceType: String,
        hint: NetworkProtocol,
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                runCatching { nsd.resolveService(service, makeResolveListener(hint)) }
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val name = service.serviceName ?: return
                _devices.update { list -> list.filterNot { it.serviceName == name } }
            }
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (serviceType == serviceType) _scanning.update { false }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    private fun makeResolveListener(hint: NetworkProtocol): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = runCatching { serviceInfo.host?.hostAddress }.getOrNull()
                    ?: return
                if (host.isBlank()) return
                val port = serviceInfo.port.takeIf { it > 0 } ?: return
                val name = serviceInfo.serviceName ?: host
                _devices.update { list ->
                    if (list.any { it.host == host && it.port == port }) list
                    else list + DiscoveredDevice(host, port, name, hint)
                }
            }
        }
    }

    private fun acquireLock() {
        releaseLock()
        val wifi = runCatching {
            appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        }.getOrNull() ?: return
        multicastLock = runCatching {
            wifi.createMulticastLock("mediamaster-discovery").apply {
                setReferenceCounted(true)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseLock() {
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
        multicastLock = null
    }
}
