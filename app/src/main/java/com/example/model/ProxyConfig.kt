package com.example.model

enum class ProxyProtocol(val displayName: String, val defaultPort: Int) {
    SOCKS5("SOCKS5", 1080),
    HTTP("HTTP", 8080),
    HTTPS("HTTPS", 8443),
    SOCKS4("SOCKS4", 1080)
}

enum class AuthMethod(val displayName: String) {
    NONE("None"),
    USER_PASSWORD("Username/Password")
}

enum class DnsResolution(val displayName: String) {
    PROXY("Proxy"),
    LOCAL("Local"),
    CUSTOM("Custom DNS")
}

data class ProxyConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "New Profile",
    val host: String = "",
    val port: Int = 1080,
    val protocol: ProxyProtocol = ProxyProtocol.SOCKS5,
    val requiresAuth: Boolean = false,
    val username: String = "",
    val password: String = "",
    val dnsResolution: DnsResolution = DnsResolution.PROXY,
    val bypassList: String = "127.0.0.1, localhost, *.local",
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
