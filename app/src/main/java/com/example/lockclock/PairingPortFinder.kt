package com.example.lockclock

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Watches for the "Pair device with pairing code" dialog and reports its (changing) port. */
class PairingPortFinder(ctx: Context, private val onPort: (Int?) -> Unit) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var started = false

    private val listener = object : NsdManager.DiscoveryListener {
        @Suppress("DEPRECATION")
        override fun onServiceFound(info: NsdServiceInfo) {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                override fun onServiceResolved(i: NsdServiceInfo) { onPort(i.port) }
            })
        }
        override fun onServiceLost(info: NsdServiceInfo) { onPort(null) }
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) {}
        override fun onStartDiscoveryFailed(type: String, code: Int) {}
        override fun onStopDiscoveryFailed(type: String, code: Int) {}
    }

    fun start() {
        if (started) return
        runCatching {
            nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
            started = true
        }
    }

    fun stop() {
        if (!started) return
        runCatching { nsd.stopServiceDiscovery(listener) }
        started = false
    }
}
