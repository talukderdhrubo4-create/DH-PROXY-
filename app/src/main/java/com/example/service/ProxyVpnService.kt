package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.model.RoutingMode
import com.example.repository.ProxyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.URLEncoder

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isEngineRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        const val ACTION_CONNECT = "com.example.proxyfloat.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.proxyfloat.ACTION_DISCONNECT"
        const val EXTRA_HOST = "extra_host"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PROTOCOL = "extra_protocol"
        const val EXTRA_REQUIRES_AUTH = "extra_requires_auth"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_RESOLVED_IP = "extra_resolved_ip"
        const val EXTRA_SUPPORTS_UDP = "extra_supports_udp"
        const val EXTRA_DNS_MODE = "extra_dns_mode"
        const val EXTRA_LATENCY = "extra_latency"
        const val EXTRA_BALANCE_INFO = "extra_balance_info"
        const val EXTRA_BALANCE_TIMESTAMP = "extra_balance_timestamp"

        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "proxy_vpn_channel"
        private const val TAG = "ProxyVpnService"

        /**
         * Decomposes 0.0.0.0/0 into exactly 32 CIDR prefixes covering all IPv4 addresses except [ipToExclude].
         * Used to route DNS queries to the fallback DNS server directly over the physical network,
         * avoiding UDP blackholing when the upstream SOCKS5 proxy is TCP-only.
         */
        fun getRoutesExcludingIp(ipToExclude: String): List<Pair<String, Int>> {
            val routes = mutableListOf<Pair<String, Int>>()
            try {
                val bytes = java.net.InetAddress.getByName(ipToExclude).address
                if (bytes.size != 4) return listOf(Pair("0.0.0.0", 0))

                var targetInt = 0L
                for (b in bytes) {
                    targetInt = (targetInt shl 8) or (b.toLong() and 0xFFL)
                }

                var currentPrefix = 0L
                for (i in 0 until 32) {
                    val bit = (targetInt shr (31 - i)) and 1L
                    val siblingBit = 1L - bit
                    val siblingPrefix = currentPrefix or (siblingBit shl (31 - i))

                    val ipStr = String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        (siblingPrefix shr 24) and 0xFFL,
                        (siblingPrefix shr 16) and 0xFFL,
                        (siblingPrefix shr 8) and 0xFFL,
                        siblingPrefix and 0xFFL
                    )
                    val prefixLen = i + 1
                    routes.add(Pair(ipStr, prefixLen))

                    currentPrefix = currentPrefix or (bit shl (31 - i))
                }
            } catch (e: Exception) {
                return listOf(Pair("0.0.0.0", 0))
            }
            return routes
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
            val notification = createNotification("DH PROXY Engine Active")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onCreate startForeground", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val host = intent?.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
            val port = intent?.getIntExtra(EXTRA_PORT, 1080) ?: 1080
            val protocol = intent?.getStringExtra(EXTRA_PROTOCOL) ?: "SOCKS5"
            val requiresAuth = intent?.getBooleanExtra(EXTRA_REQUIRES_AUTH, false) ?: false
            val username = intent?.getStringExtra(EXTRA_USERNAME) ?: ""
            val password = intent?.getStringExtra(EXTRA_PASSWORD) ?: ""
            val resolvedIp = intent?.getStringExtra(EXTRA_RESOLVED_IP) ?: host
            val supportsUdp = intent?.getBooleanExtra(EXTRA_SUPPORTS_UDP, false) ?: false
            val dnsMode = intent?.getStringExtra(EXTRA_DNS_MODE) ?: "REMOTE"
            val latency = intent?.getLongExtra(EXTRA_LATENCY, 35L) ?: 35L
            val balanceInfo = intent?.getStringExtra(EXTRA_BALANCE_INFO) ?: "Balance unavailable"
            val balanceTimestamp = if (intent?.hasExtra(EXTRA_BALANCE_TIMESTAMP) == true) intent.getLongExtra(EXTRA_BALANCE_TIMESTAMP, 0L) else null

            when (intent?.action) {
                ACTION_CONNECT -> {
                    startVpn(host, port, protocol, requiresAuth, username, password, resolvedIp, supportsUdp, dnsMode, latency, balanceInfo, balanceTimestamp)
                }
                ACTION_DISCONNECT -> {
                    stopVpn()
                }
                else -> {
                    startVpn(host, port, protocol, requiresAuth, username, password, resolvedIp, supportsUdp, dnsMode, latency, balanceInfo, balanceTimestamp)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error handling start command", t)
        }
        return START_NOT_STICKY
    }

    private fun startVpn(
        host: String,
        port: Int,
        protocol: String,
        requiresAuth: Boolean,
        username: String,
        password: String,
        resolvedIp: String,
        supportsUdp: Boolean,
        dnsMode: String,
        latencyMs: Long,
        balanceInfo: String,
        balanceTimestamp: Long?
    ) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val notification = createNotification("DH PROXY Connected: $protocol://$host:$port")
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.notify(NOTIFICATION_ID, notification)

                stopEngineAndVpnInterface()

                // Check if VPN is prepared
                val prepareIntent = prepare(this@ProxyVpnService)
                if (prepareIntent != null) {
                    Log.w(TAG, "VPN service not prepared by user yet, opening MainActivity")
                    val activityIntent = Intent(this@ProxyVpnService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("REQUEST_VPN_CONSENT", true)
                    }
                    startActivity(activityIntent)
                    return@launch
                }

                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                val activeNetwork = cm?.activeNetwork
                val linkProps = activeNetwork?.let { cm.getLinkProperties(it) }

                val builder = Builder()
                    .setSession("DH PROXY - $protocol")
                    .setMtu(1400) // MTU 1400 prevents carrier packet fragmentation
                    .addAddress("10.0.0.2", 24)

                // DNS & Routing Strategy:
                // SOCKS5 proxies can either be UDP-capable or strictly TCP-only.
                // When proxy supports UDP Associate and mode isn't LOCAL:
                //   Route 0.0.0.0/0 into TUN so all traffic & DNS resolve remotely through SOCKS5.
                // When proxy is TCP-only (or mode is LOCAL):
                //   UDP packets into TUN cannot be forwarded by a TCP-only proxy and would blackhole.
                //   We route all IPv4 EXCEPT the fallback DNS server (1.1.1.1).
                //   The OS resolves hostnames directly via the physical interface in <20ms,
                //   while all resolved TCP traffic (YouTube, Web, Apps) matches the VPN routes and is proxied through SOCKS5!
                val fallbackDns = "1.1.1.1"
                if (supportsUdp && dnsMode != "LOCAL") {
                    builder.addRoute("0.0.0.0", 0)
                    builder.addDnsServer("1.1.1.1")
                    builder.addDnsServer("8.8.8.8")
                } else {
                    val routes = getRoutesExcludingIp(fallbackDns)
                    for ((routeIp, prefix) in routes) {
                        builder.addRoute(routeIp, prefix)
                    }
                    builder.addDnsServer(fallbackDns)

                    // Add underlying network DNS if available
                    linkProps?.dnsServers?.forEach { dns ->
                        if (dns is java.net.Inet4Address && dns.hostAddress != fallbackDns) {
                            try {
                                builder.addDnsServer(dns)
                            } catch (e: Throwable) {
                                Log.d(TAG, "Could not add underlying DNS $dns: ${e.message}")
                            }
                        }
                    }
                }

                // Bind underlying network to tell Android OS the VPN operates on top of it
                if (activeNetwork != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    try {
                        builder.setUnderlyingNetworks(arrayOf(activeNetwork))
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not set underlying networks", t)
                    }
                }

                val repository = ProxyRepository(this@ProxyVpnService)
                val mode = repository.getRoutingMode()
                val selectedPackages = repository.getSelectedPackages()

                when (mode) {
                    RoutingMode.SELECTED_ONLY -> {
                        if (selectedPackages.isNotEmpty()) {
                            for (pkg in selectedPackages) {
                                try {
                                    builder.addAllowedApplication(pkg)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Package $pkg not found for allowed application")
                                }
                            }
                        } else {
                            // Fallback: If no specific app is selected, route all apps except DH PROXY itself
                            try {
                                builder.addDisallowedApplication(packageName)
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not disallow own package: ${e.message}")
                            }
                        }
                    }
                    RoutingMode.BYPASS_SELECTED -> {
                        for (pkg in selectedPackages) {
                            try {
                                builder.addDisallowedApplication(pkg)
                            } catch (e: Exception) {
                                Log.w(TAG, "Package $pkg not found for disallowed application")
                            }
                        }
                        try {
                            builder.addDisallowedApplication(packageName)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not disallow own package: ${e.message}")
                        }
                    }
                    RoutingMode.ALL_APPS -> {
                        // Bypass the app's own traffic so tun2socks connects directly to the proxy server
                        try {
                            builder.addDisallowedApplication(packageName)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not disallow own package: ${e.message}")
                        }
                    }
                }

                val pfd = builder.establish()
                if (pfd == null) {
                    Log.e(TAG, "builder.establish() returned null")
                    ProxyController.notifyServiceError("Android VPN system could not establish TUN interface. Please retry.")
                    return@launch
                }
                vpnInterface = pfd
                val fd = pfd.fd
                if (fd <= 0) {
                    Log.e(TAG, "Invalid TUN file descriptor: $fd")
                    ProxyController.notifyServiceError("Invalid TUN interface file descriptor. Please retry.")
                    return@launch
                }
                Log.i(TAG, "VPN interface established with valid fd=$fd, MTU=1400")

                // Start tun2socks engine on IO dispatcher
                startTun2Socks(fd, host, port, protocol, requiresAuth, username, password, resolvedIp, latencyMs, supportsUdp, balanceInfo, balanceTimestamp)

            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException: VPN consent required", se)
                ProxyController.notifyServiceError("VPN permission required. Please allow VPN connection in the prompt.")
                val activityIntent = Intent(this@ProxyVpnService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("REQUEST_VPN_CONSENT", true)
                }
                try {
                    startActivity(activityIntent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to establish VPN interface", t)
                ProxyController.notifyServiceError("Failed to establish VPN interface: ${t.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    private fun startTun2Socks(
        fd: Int,
        host: String,
        port: Int,
        protocol: String,
        requiresAuth: Boolean,
        username: String,
        password: String,
        resolvedIp: String,
        latencyMs: Long,
        supportsUdp: Boolean,
        balanceInfo: String,
        balanceTimestamp: Long?
    ) {
        val dialHost = if (resolvedIp.isNotBlank()) resolvedIp else host
        val proxyUrl = buildProxyUrl(protocol, dialHost, port, requiresAuth, username, password)

        serviceScope.launch {
            try {
                val key = engine.Key().apply {
                    setDevice("fd://$fd")
                    setProxy(proxyUrl)
                    setMTU(1400)
                    // Log level 'warn' prevents flooding Android logcat and reduces CPU overhead
                    setLogLevel("warn")
                    // Moderate receive buffer dynamically auto-tunes TCP receive window
                    setTCPModerateReceiveBuffer(true)
                    // 2MB send & receive buffer handles high Bandwidth-Delay Product (BDP) for smooth streaming
                    setTCPSendBufferSize("2m")
                    setTCPReceiveBufferSize("2m")
                }
                engine.Engine.insert(key)
                engine.Engine.start()
                isEngineRunning = true
                Log.i(TAG, "tun2socks engine started successfully for: $protocol://$host:$port ($dialHost)")

                // Notify Controller that connection is active
                ProxyController.notifyServiceConnected(latencyMs, dialHost, supportsUdp, balanceInfo, balanceTimestamp)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start tun2socks engine", t)
                ProxyController.notifyServiceError("Failed to start proxy routing engine: ${t.localizedMessage ?: "Native engine error"}")
            }
        }
    }

    private fun buildProxyUrl(
        protocol: String,
        host: String,
        port: Int,
        requiresAuth: Boolean,
        username: String,
        password: String
    ): String {
        val proto = when (protocol.uppercase()) {
            "HTTP" -> "http"
            "HTTPS" -> "http"
            "SOCKS4" -> "socks4"
            else -> "socks5"
        }
        return if (requiresAuth && username.isNotEmpty()) {
            val encUser = URLEncoder.encode(username, "UTF-8")
            val encPass = URLEncoder.encode(password, "UTF-8")
            "$proto://$encUser:$encPass@$host:$port"
        } else {
            "$proto://$host:$port"
        }
    }

    private fun stopEngineAndVpnInterface() {
        if (isEngineRunning) {
            try {
                engine.Engine.stop()
            } catch (t: Throwable) {
                Log.e(TAG, "Error stopping tun2socks engine", t)
            }
            isEngineRunning = false
        }
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing previous vpn interface", e)
        }
    }

    private fun stopVpn() {
        stopEngineAndVpnInterface()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        stopSelf()
    }

    override fun onRevoke() {
        stopVpn()
        ProxyController.disconnect(this)
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DH PROXY Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active proxy status and duration"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DH PROXY Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
