package com.example.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR
}

data class ProxyState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val activeConfig: ProxyConfig? = null,
    val connectedAt: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val latencyMs: Long? = null,
    val externalIp: String? = null,
    val errorMessage: String? = null,
    val isFloatingEnabled: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.ALL_APPS,
    val selectedAppCount: Int = 0,
    val engineStatus: String? = null,
    val accountBalanceInfo: String = "Balance unavailable",
    val accountBalanceTimestamp: Long? = null
) {
    fun formattedBalance(): String {
        if (accountBalanceTimestamp != null && accountBalanceTimestamp > 0) {
            val dateStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(accountBalanceTimestamp))
            return "$accountBalanceInfo (updated $dateStr)"
        }
        return accountBalanceInfo
    }
    fun formattedTime(): String {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formattedDataIn(): String = formatBytes(bytesIn)
    fun formattedDataOut(): String = formatBytes(bytesOut)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024f * 1024f))
            else -> String.format("%.2f GB", bytes / (1024f * 1024f * 1024f))
        }
    }
}
