package com.example.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.model.AppItem
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.model.RoutingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class ProxyRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("proxy_float_prefs", Context.MODE_PRIVATE)

    private val dnsCache = ConcurrentHashMap<String, Pair<String, Long>>()

    companion object {
        private const val KEY_PROFILES = "saved_proxy_profiles"
        private const val KEY_ACTIVE_ID = "active_profile_id"
        private const val KEY_ROUTING_MODE = "routing_mode"
        private const val KEY_SELECTED_PACKAGES = "selected_packages"
        private const val KEY_FLOATING_AUTO_MINIMIZE = "floating_auto_minimize"
    }

    init {
        ensureDefaultProfiles()
    }

    private fun ensureDefaultProfiles() {
        if (!prefs.contains(KEY_PROFILES)) {
            val defaults = listOf(
                ProxyConfig(
                    id = "default_socks5",
                    name = "Fast SOCKS5 (Default)",
                    host = "127.0.0.1",
                    port = 1080,
                    protocol = ProxyProtocol.SOCKS5,
                    requiresAuth = false,
                    isDefault = true
                ),
                ProxyConfig(
                    id = "default_http",
                    name = "HTTP Web Proxy",
                    host = "192.168.1.1",
                    port = 8080,
                    protocol = ProxyProtocol.HTTP,
                    requiresAuth = true,
                    username = "user",
                    password = "password"
                ),
                ProxyConfig(
                    id = "default_socks4",
                    name = "Secure SOCKS4 Tunnel",
                    host = "10.0.0.1",
                    port = 1080,
                    protocol = ProxyProtocol.SOCKS4,
                    requiresAuth = false
                )
            )
            saveProfiles(defaults)
            prefs.edit().putString(KEY_ACTIVE_ID, "default_socks5").apply()
        }
    }

    fun getProfiles(): List<ProxyConfig> {
        val jsonString = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val list = mutableListOf<ProxyConfig>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ProxyConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        host = obj.getString("host"),
                        port = obj.getInt("port"),
                        protocol = ProxyProtocol.valueOf(obj.optString("protocol", "SOCKS5")),
                        requiresAuth = obj.optBoolean("requiresAuth", false),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        dnsResolution = try {
                            com.example.model.DnsResolution.valueOf(obj.optString("dnsResolution", "PROXY"))
                        } catch (e: Exception) {
                            com.example.model.DnsResolution.PROXY
                        },
                        bypassList = obj.optString("bypassList", "127.0.0.1, localhost"),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveProfiles(profiles: List<ProxyConfig>) {
        val array = JSONArray()
        for (item in profiles) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            obj.put("host", item.host)
            obj.put("port", item.port)
            obj.put("protocol", item.protocol.name)
            obj.put("requiresAuth", item.requiresAuth)
            obj.put("username", item.username)
            obj.put("password", item.password)
            obj.put("dnsResolution", item.dnsResolution.name)
            obj.put("bypassList", item.bypassList)
            obj.put("isDefault", item.isDefault)
            obj.put("createdAt", item.createdAt)
            array.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).apply()
    }

    fun addOrUpdateProfile(config: ProxyConfig) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            current[index] = config
        } else {
            current.add(0, config)
        }
        saveProfiles(current)
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().toMutableList()
        current.removeAll { it.id == id }
        saveProfiles(current)
        if (getActiveProfileId() == id) {
            val first = current.firstOrNull()?.id ?: ""
            setActiveProfileId(first)
        }
    }

    fun getActiveProfileId(): String {
        return prefs.getString(KEY_ACTIVE_ID, "") ?: ""
    }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActiveProfile(): ProxyConfig? {
        val id = getActiveProfileId()
        val list = getProfiles()
        return list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    fun getRoutingMode(): RoutingMode {
        val modeStr = prefs.getString(KEY_ROUTING_MODE, RoutingMode.ALL_APPS.name)
        return try {
            RoutingMode.valueOf(modeStr ?: RoutingMode.ALL_APPS.name)
        } catch (e: Exception) {
            RoutingMode.ALL_APPS
        }
    }

    fun setRoutingMode(mode: RoutingMode) {
        prefs.edit().putString(KEY_ROUTING_MODE, mode.name).apply()
    }

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet(KEY_SELECTED_PACKAGES, emptySet()) ?: emptySet()
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_SELECTED_PACKAGES, packages).apply()
    }

    fun isAutoMinimizeFloating(): Boolean {
        return prefs.getBoolean(KEY_FLOATING_AUTO_MINIMIZE, true)
    }

    fun setAutoMinimizeFloating(value: Boolean) {
        prefs.edit().putBoolean(KEY_FLOATING_AUTO_MINIMIZE, value).apply()
    }

    suspend fun loadInstalledApps(): List<AppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val selected = getSelectedPackages()
        val apps = mutableListOf<AppItem>()
        val seenPackages = mutableSetOf<String>()

        try {
            // 1. Query all launcher apps (guarantees Chrome, browsers, games, etc. are found)
            val launcherIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            }
            val launcherApps = pm.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in launcherApps) {
                val pkgName = resolveInfo.activityInfo.packageName
                if (pkgName == context.packageName || seenPackages.contains(pkgName)) continue
                seenPackages.add(pkgName)

                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                apps.add(
                    AppItem(
                        packageName = pkgName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = false, // User-facing app (like Chrome)
                        isSelected = selected.contains(pkgName)
                    )
                )
            }

            // 2. Query all other installed packages
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            for (pkg in packages) {
                val pkgName = pkg.packageName
                if (pkgName == context.packageName || seenPackages.contains(pkgName)) continue
                seenPackages.add(pkgName)

                val hasLaunchIntent = pm.getLaunchIntentForPackage(pkgName) != null
                val isSystem = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                val appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
                val icon = try {
                    pkg.applicationInfo?.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }

                apps.add(
                    AppItem(
                        packageName = pkgName,
                        appName = appName,
                        icon = icon,
                        isSystemApp = isSystem && !hasLaunchIntent,
                        isSelected = selected.contains(pkgName)
                    )
                )
            }

            // Sort: Selected first, then non-system user apps, then alphabetically
            apps.sortWith(
                compareByDescending<AppItem> { it.isSelected }
                    .thenBy { it.isSystemApp }
                    .thenBy { it.appName.lowercase() }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        apps
    }

    suspend fun testProxyPing(host: String, port: Int): Result<Long> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
                val latency = System.currentTimeMillis() - startTime
                Result.success(latency)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

data class ProxyProbeData(
    val latencyMs: Long,
    val resolvedIp: String,
    val supportsUdp: Boolean,
    val balanceInfo: String = "Balance unavailable",
    val balanceTimestamp: Long? = null
)

    fun getCachedBalance(profileId: String): Pair<String, Long?> {
        val prefs = context.getSharedPreferences("proxy_balance_cache", Context.MODE_PRIVATE)
        val info = prefs.getString("balance_$profileId", "Balance unavailable") ?: "Balance unavailable"
        val time = prefs.getLong("time_$profileId", 0L).takeIf { it > 0 }
        return Pair(info, time)
    }

    fun saveCachedBalance(profileId: String, balanceInfo: String) {
        val prefs = context.getSharedPreferences("proxy_balance_cache", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("balance_$profileId", balanceInfo)
            .putLong("time_$profileId", System.currentTimeMillis())
            .apply()
    }

    fun resolveHostWithCache(host: String): String {
        val cleanHost = host.trim()
        val now = System.currentTimeMillis()
        dnsCache[cleanHost]?.let { (cachedIp, timestamp) ->
            if (now - timestamp < 300_000L) { // 5-minute DNS cache TTL
                return cachedIp
            }
        }
        val resolved = java.net.InetAddress.getByName(cleanHost).hostAddress ?: cleanHost
        dnsCache[cleanHost] = Pair(resolved, now)
        return resolved
    }

    /**
     * Checks if the proxy has working data / MB and is responsive to live network requests.
     * Verifies hostname resolution, SOCKS5 greeting, username/password authentication, remote connectivity,
     * and probes whether UDP Associate is supported concurrently to avoid connection lag.
     */
    suspend fun checkProxyHasDataAndInternet(config: ProxyConfig): Result<ProxyProbeData> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val resolvedIp = try {
                resolveHostWithCache(config.host)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Cannot resolve proxy host '${config.host}': ${e.localizedMessage ?: "Unknown host"}"))
            }

            coroutineScope {
                // Concurrently probe if SOCKS5 proxy supports UDP Associate (CMD=0x03)
                val udpProbeDeferred = async(Dispatchers.IO) {
                    if (config.protocol != ProxyProtocol.SOCKS5) return@async false
                    try {
                        Socket().use { udpSocket ->
                            udpSocket.tcpNoDelay = true
                            udpSocket.soTimeout = 2000
                            udpSocket.connect(InetSocketAddress(resolvedIp, config.port), 2000)
                            val uOut = udpSocket.getOutputStream()
                            val uIn = udpSocket.getInputStream()
                            val m = if (config.requiresAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
                            uOut.write(byteArrayOf(0x05, m.size.toByte()) + m)
                            uOut.flush()
                            val g = ByteArray(2)
                            readFully(uIn, g)
                            if (g[0] == 0x05.toByte()) {
                                val sel = g[1].toInt() and 0xFF
                                if (sel == 0x02 && config.requiresAuth) {
                                    val uB = config.username.toByteArray(Charsets.UTF_8)
                                    val pB = config.password.toByteArray(Charsets.UTF_8)
                                    uOut.write(byteArrayOf(0x01, uB.size.toByte()) + uB + byteArrayOf(pB.size.toByte()) + pB)
                                    uOut.flush()
                                    val aR = ByteArray(2)
                                    readFully(uIn, aR)
                                    if (aR[0] != 0x01.toByte() || aR[1] != 0x00.toByte()) return@async false
                                }
                                // Send UDP Associate request (CMD=0x03)
                                uOut.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                                uOut.flush()
                                val uReply = ByteArray(4)
                                readFully(uIn, uReply)
                                return@async (uReply[0] == 0x05.toByte() && uReply[1] == 0x00.toByte())
                            }
                        }
                    } catch (e: Exception) {
                        // UDP Associate not supported by proxy
                    }
                    false
                }

                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = 4000
                    socket.connect(InetSocketAddress(resolvedIp, config.port), 4000)

                    val output = socket.getOutputStream()
                    val input = socket.getInputStream()

                    when (config.protocol) {
                        ProxyProtocol.SOCKS5 -> {
                            // RFC 1928: Send supported authentication methods.
                            // 0x00 = NO AUTHENTICATION REQUIRED
                            // 0x02 = USERNAME/PASSWORD (RFC 1929)
                            val methods = if (config.requiresAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
                            output.write(byteArrayOf(0x05, methods.size.toByte()) + methods)
                            output.flush()

                            val greeting = ByteArray(2)
                            readFully(input, greeting)
                            if (greeting[0] != 0x05.toByte()) {
                                return@coroutineScope Result.failure(Exception("Invalid SOCKS5 greeting from proxy server."))
                            }

                            val selectedMethod = greeting[1].toInt() and 0xFF
                            when (selectedMethod) {
                                0x00 -> {
                                    // No authentication required by proxy
                                }
                                0x02 -> {
                                    if (!config.requiresAuth) {
                                        return@coroutineScope Result.failure(Exception("Proxy requires username/password authentication. Please enable authentication in profile settings."))
                                    }
                                    val userBytes = config.username.toByteArray(Charsets.UTF_8)
                                    val passBytes = config.password.toByteArray(Charsets.UTF_8)
                                    if (userBytes.isEmpty() || passBytes.isEmpty() || userBytes.size > 255 || passBytes.size > 255) {
                                        return@coroutineScope Result.failure(Exception("Username and password must each be between 1 and 255 bytes."))
                                    }
                                    // RFC 1929: 0x01 + ULEN + UNAME + PLEN + PASSWD
                                    output.write(byteArrayOf(0x01, userBytes.size.toByte()) + userBytes + byteArrayOf(passBytes.size.toByte()) + passBytes)
                                    output.flush()

                                    val authReply = ByteArray(2)
                                    readFully(input, authReply)
                                    if (authReply[0] != 0x01.toByte() || authReply[1] != 0x00.toByte()) {
                                        return@coroutineScope Result.failure(Exception("SOCKS5 authentication failed! Invalid username or password."))
                                    }
                                }
                                0xFF -> {
                                    return@coroutineScope Result.failure(Exception("SOCKS5 proxy server rejected authentication methods (No acceptable methods)."))
                                }
                                else -> {
                                    return@coroutineScope Result.failure(Exception("SOCKS5 proxy requested unsupported authentication method: 0x${Integer.toHexString(selectedMethod)}"))
                                }
                            }

                            // Verify proxy can establish remote TCP connection:
                            // SOCKS5 CONNECT request: CMD=0x01 (CONNECT), RSV=0x00, ATYP=0x03 (Domain), Port=80
                            val targetDomain = "connectivitycheck.gstatic.com".toByteArray(Charsets.US_ASCII)
                            val connectPacket = byteArrayOf(0x05, 0x01, 0x00, 0x03, targetDomain.size.toByte()) + targetDomain + byteArrayOf(0x00, 0x50.toByte())
                            output.write(connectPacket)
                            output.flush()

                            val replyHeader = ByteArray(4)
                            readFully(input, replyHeader)
                            if (replyHeader[0] != 0x05.toByte()) {
                                return@coroutineScope Result.failure(Exception("Invalid SOCKS5 CONNECT response from proxy."))
                            }

                            val replyCode = replyHeader[1].toInt() and 0xFF
                            if (replyCode != 0x00) {
                                val errorReason = when (replyCode) {
                                    0x01 -> "General SOCKS server failure"
                                    0x02 -> "Connection not allowed by proxy ruleset"
                                    0x03 -> "Network unreachable via proxy"
                                    0x04 -> "Host unreachable"
                                    0x05 -> "Connection refused by target"
                                    0x06 -> "TTL expired"
                                    0x07 -> "Command not supported"
                                    0x08 -> "Address type not supported"
                                    else -> "Error code $replyCode"
                                }
                                return@coroutineScope Result.failure(Exception("SOCKS5 connected but remote routing failed: $errorReason."))
                            }

                            // Read bound address and port to clean up
                            val addrLength = when (replyHeader[3].toInt() and 0xFF) {
                                0x01 -> 4 // IPv4
                                0x04 -> 16 // IPv6
                                0x03 -> {
                                    val len = input.read()
                                    if (len < 0) return@coroutineScope Result.failure(Exception("Incomplete SOCKS5 response."))
                                    len
                                }
                                else -> return@coroutineScope Result.failure(Exception("Invalid SOCKS5 address type."))
                            }
                            readFully(input, ByteArray(addrLength + 2)) // Bound address + port
                        }
                        ProxyProtocol.HTTP, ProxyProtocol.HTTPS -> {
                            val authHeader = if (config.requiresAuth && config.username.isNotBlank()) {
                                val creds = "${config.username}:${config.password}"
                                val encoded = android.util.Base64.encodeToString(creds.toByteArray(), android.util.Base64.NO_WRAP)
                                "Proxy-Authorization: Basic $encoded\r\n"
                            } else ""

                            val probe = "GET http://connectivitycheck.gstatic.com/generate_204 HTTP/1.1\r\n" +
                                    "Host: connectivitycheck.gstatic.com\r\n" +
                                    authHeader +
                                    "Connection: close\r\n\r\n"
                            output.write(probe.toByteArray())
                            output.flush()

                            val buffer = ByteArray(512)
                            val read = input.read(buffer)
                            if (read <= 0) {
                                return@coroutineScope Result.failure(Exception("Proxy server did not return a response to the connectivity probe."))
                            }
                            val header = String(buffer, 0, read)
                            if (header.contains("407 Proxy Authentication Required")) {
                                return@coroutineScope Result.failure(Exception("HTTP Proxy Authentication Failed! Check username/password."))
                            }
                        }
                        ProxyProtocol.SOCKS4 -> {
                            // SOCKS4 CONNECT to 8.8.8.8:80
                            val userId = if (config.requiresAuth) config.username.toByteArray(Charsets.US_ASCII) else byteArrayOf()
                            val packet = byteArrayOf(0x04, 0x01, 0x00, 0x50, 0x08, 0x08, 0x08, 0x08) + userId + byteArrayOf(0x00)
                            output.write(packet)
                            output.flush()

                            val reply = ByteArray(8)
                            readFully(input, reply)
                            if (reply[1] != 0x5A.toByte()) {
                                return@coroutineScope Result.failure(Exception("SOCKS4 proxy request rejected or failed (code 0x${Integer.toHexString(reply[1].toInt() and 0xFF)})."))
                            }
                        }
                    }

                    val supportsUdp = udpProbeDeferred.await()
                    val cached = getCachedBalance(config.id)
                    val latency = System.currentTimeMillis() - startTime
                    Result.success(
                        ProxyProbeData(
                            latencyMs = latency,
                            resolvedIp = resolvedIp,
                            supportsUdp = supportsUdp,
                            balanceInfo = cached.first,
                            balanceTimestamp = cached.second
                        )
                    )
                }
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Connection timed out"
            Result.failure(Exception("Proxy connection failed: $msg"))
        }
    }

    private fun readFully(input: java.io.InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw java.io.EOFException("Proxy closed connection unexpectedly during handshake.")
            offset += count
        }
    }

    data class BenchmarkReport(
        val directLatencyMs: Long,
        val proxyLatencyMs: Long,
        val handshakeMs: Long,
        val directSuccess: Boolean,
        val proxySuccess: Boolean,
        val summary: String
    )

    suspend fun runSpeedAndLatencyBenchmark(config: ProxyConfig): BenchmarkReport = withContext(Dispatchers.IO) {
        var directLatency = -1L
        var directSuccess = false
        val dStart = System.currentTimeMillis()
        try {
            Socket().use { s ->
                s.tcpNoDelay = true
                s.soTimeout = 4000
                s.connect(InetSocketAddress("connectivitycheck.gstatic.com", 80), 4000)
                s.getOutputStream().write("GET /generate_204 HTTP/1.1\r\nHost: connectivitycheck.gstatic.com\r\nConnection: close\r\n\r\n".toByteArray())
                s.getOutputStream().flush()
                val buf = ByteArray(256)
                val r = s.getInputStream().read(buf)
                if (r > 0) {
                    directLatency = System.currentTimeMillis() - dStart
                    directSuccess = true
                }
            }
        } catch (e: Exception) {
            directLatency = -1L
        }

        var proxyLatency = -1L
        var handshakeLatency = -1L
        var proxySuccess = false
        val pStart = System.currentTimeMillis()
        try {
            val resolved = resolveHostWithCache(config.host)
            Socket().use { s ->
                s.tcpNoDelay = true
                s.soTimeout = 4000
                s.connect(InetSocketAddress(resolved, config.port), 4000)
                val out = s.getOutputStream()
                val inp = s.getInputStream()

                val methods = if (config.requiresAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
                out.write(byteArrayOf(0x05, methods.size.toByte()) + methods)
                out.flush()
                val g = ByteArray(2)
                readFully(inp, g)
                if (g[0] == 0x05.toByte()) {
                    val sel = g[1].toInt() and 0xFF
                    if (sel == 0x02 && config.requiresAuth) {
                        val uB = config.username.toByteArray(Charsets.UTF_8)
                        val pB = config.password.toByteArray(Charsets.UTF_8)
                        out.write(byteArrayOf(0x01, uB.size.toByte()) + uB + byteArrayOf(pB.size.toByte()) + pB)
                        out.flush()
                        val aR = ByteArray(2)
                        readFully(inp, aR)
                    }
                    val targetDomain = "connectivitycheck.gstatic.com".toByteArray(Charsets.US_ASCII)
                    val connectPacket = byteArrayOf(0x05, 0x01, 0x00, 0x03, targetDomain.size.toByte()) + targetDomain + byteArrayOf(0x00, 0x50.toByte())
                    out.write(connectPacket)
                    out.flush()
                    val rep = ByteArray(4)
                    readFully(inp, rep)
                    if (rep[1] == 0x00.toByte()) {
                        handshakeLatency = System.currentTimeMillis() - pStart
                        out.write("GET /generate_204 HTTP/1.1\r\nHost: connectivitycheck.gstatic.com\r\nConnection: close\r\n\r\n".toByteArray())
                        out.flush()
                        val buf = ByteArray(256)
                        val r = inp.read(buf)
                        if (r > 0) {
                            proxyLatency = System.currentTimeMillis() - pStart
                            proxySuccess = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            proxyLatency = -1L
        }

        val summary = if (proxySuccess && directSuccess) {
            "Direct: ${directLatency}ms | Proxy: ${proxyLatency}ms (Handshake: ${handshakeLatency}ms, Overhead: +${proxyLatency - directLatency}ms)"
        } else if (directSuccess) {
            "Direct: ${directLatency}ms | Proxy: unreachable or handshake error"
        } else {
            "Network unreachable"
        }

        BenchmarkReport(
            directLatencyMs = directLatency,
            proxyLatencyMs = proxyLatency,
            handshakeMs = handshakeLatency,
            directSuccess = directSuccess,
            proxySuccess = proxySuccess,
            summary = summary
        )
    }
}
