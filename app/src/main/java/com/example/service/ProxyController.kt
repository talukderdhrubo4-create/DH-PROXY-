package com.example.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.model.ConnectionStatus
import com.example.model.ProxyConfig
import com.example.model.ProxyState
import com.example.model.RoutingMode
import com.example.repository.ProxyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


object ProxyController {
    private const val TAG = "ProxyController"
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var connectJob: Job? = null
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(ProxyState())
    val state: StateFlow<ProxyState> = _state.asStateFlow()

    fun init(context: Context) {
        val repository = ProxyRepository(context)
        val active = repository.getActiveProfile()
        val routingMode = repository.getRoutingMode()
        val selectedApps = repository.getSelectedPackages()

        _state.update { current ->
            current.copy(
                activeConfig = active,
                routingMode = routingMode,
                selectedAppCount = selectedApps.size
            )
        }
    }

    fun updateConfig(config: ProxyConfig) {
        _state.update { it.copy(activeConfig = config, errorMessage = null) }
    }

    fun updateRouting(mode: RoutingMode, selectedCount: Int) {
        _state.update { it.copy(routingMode = mode, selectedAppCount = selectedCount) }
    }

    fun notifyServiceConnected(
        latencyMs: Long,
        resolvedIp: String,
        supportsUdp: Boolean,
        balanceInfo: String = "Balance unavailable",
        balanceTimestamp: Long? = null
    ) {
        val now = System.currentTimeMillis()
        val engineInfo = "tun2socks active (MTU 1400 • ${if (supportsUdp) "TCP+UDP" else "TCP-only • DNS Auto"})"
        _state.update {
            it.copy(
                status = ConnectionStatus.CONNECTED,
                connectedAt = now,
                elapsedSeconds = 0L,
                latencyMs = latencyMs,
                externalIp = resolvedIp,
                bytesIn = 0L,
                bytesOut = 0L,
                engineStatus = engineInfo,
                accountBalanceInfo = balanceInfo,
                accountBalanceTimestamp = balanceTimestamp,
                errorMessage = null
            )
        }
        startTimer()
        Log.i(TAG, "Proxy engine verified connected: $resolvedIp")
    }

    fun notifyServiceError(message: String) {
        stopTimer()
        connectJob?.cancel()
        _state.update {
            it.copy(
                status = ConnectionStatus.ERROR,
                errorMessage = message,
                engineStatus = null
            )
        }
        Log.e(TAG, "Service error reported: $message")
    }

    /**
     * Strict validation: Server, Port, and (if auth selected) Username & Password
     * must be provided before connecting!
     */
    fun validateConfig(config: ProxyConfig?): String? {
        if (config == null) {
            return "Please enter Server IP, Port, Username and Password."
        }
        if (config.host.trim().isEmpty()) {
            return "Server IP / Host cannot be empty! Please enter server."
        }
        if (config.port <= 0 || config.port > 65535) {
            return "Port must be a valid number between 1 and 65535."
        }
        if (config.requiresAuth) {
            if (config.username.trim().isEmpty() || config.password.trim().isEmpty()) {
                return "Authentication enabled! Please enter both Username and Password."
            }
        }
        return null
    }

    fun connect(context: Context, config: ProxyConfig? = null) {
        val appContext = context.applicationContext
        val targetConfig = config ?: _state.value.activeConfig
        
        // Prevent duplicate connection attempts if already CONNECTED
        if (_state.value.status == ConnectionStatus.CONNECTED) {
            Log.d(TAG, "Already connected, ignoring duplicate connect call")
            return
        }

        // Strict Validation Check
        val validationError = validateConfig(targetConfig)
        if (validationError != null) {
            _state.update {
                it.copy(
                    status = ConnectionStatus.ERROR,
                    errorMessage = validationError
                )
            }
            Log.w(TAG, "Connection prevented by validation: $validationError")
            return
        }

        val validTarget = targetConfig!!

        // Cancel previous connect attempt if any
        connectJob?.cancel()

        _state.update {
            it.copy(
                status = ConnectionStatus.CONNECTING,
                activeConfig = validTarget,
                errorMessage = null
            )
        }

        connectJob = scope.launch(Dispatchers.IO) {
            val repository = ProxyRepository(appContext)

            // Probe if proxy has active internet data
            val probeResult = repository.checkProxyHasDataAndInternet(validTarget)
            if (!isActive) return@launch

            if (probeResult.isFailure) {
                val errorMsg = probeResult.exceptionOrNull()?.message 
                    ?: "Proxy server is not responding to connection probe"
                _state.update {
                    it.copy(
                        status = ConnectionStatus.ERROR,
                        errorMessage = "⚠️ Connection Failed: $errorMsg",
                        engineStatus = null
                    )
                }
                Log.w(TAG, "Connection aborted: $errorMsg")
                return@launch
            }

            val probeData = probeResult.getOrNull()
            val latency = probeData?.latencyMs ?: 35L
            val resolvedIp = probeData?.resolvedIp ?: validTarget.host
            val supportsUdp = probeData?.supportsUdp ?: false
            val balanceInfo = probeData?.balanceInfo ?: "Balance unavailable"
            val balanceTimestamp = probeData?.balanceTimestamp

            _state.update {
                it.copy(
                    latencyMs = latency,
                    externalIp = resolvedIp,
                    accountBalanceInfo = balanceInfo,
                    accountBalanceTimestamp = balanceTimestamp
                )
            }

            // Start Proxy VPN Service safely with try-catch
            try {
                val intent = Intent(appContext, ProxyVpnService::class.java).apply {
                    action = ProxyVpnService.ACTION_CONNECT
                    putExtra(ProxyVpnService.EXTRA_HOST, validTarget.host)
                    putExtra(ProxyVpnService.EXTRA_PORT, validTarget.port)
                    putExtra(ProxyVpnService.EXTRA_PROTOCOL, validTarget.protocol.name)
                    putExtra(ProxyVpnService.EXTRA_REQUIRES_AUTH, validTarget.requiresAuth)
                    putExtra(ProxyVpnService.EXTRA_RESOLVED_IP, resolvedIp)
                    putExtra(ProxyVpnService.EXTRA_SUPPORTS_UDP, supportsUdp)
                    putExtra(ProxyVpnService.EXTRA_DNS_MODE, validTarget.dnsResolution.name)
                    putExtra(ProxyVpnService.EXTRA_LATENCY, latency)
                    putExtra(ProxyVpnService.EXTRA_BALANCE_INFO, balanceInfo)
                    if (balanceTimestamp != null) {
                        putExtra(ProxyVpnService.EXTRA_BALANCE_TIMESTAMP, balanceTimestamp)
                    }
                    if (validTarget.requiresAuth) {
                        putExtra(ProxyVpnService.EXTRA_USERNAME, validTarget.username)
                        putExtra(ProxyVpnService.EXTRA_PASSWORD, validTarget.password)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Safe catch: Failed to start ProxyVpnService", t)
                _state.update {
                    it.copy(
                        status = ConnectionStatus.ERROR,
                        errorMessage = "Failed to launch VPN service: ${t.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun disconnect(context: Context) {
        val appContext = context.applicationContext
        stopTimer()
        connectJob?.cancel()
        _state.update { it.copy(status = ConnectionStatus.DISCONNECTING) }

        try {
            val intent = Intent(appContext, ProxyVpnService::class.java).apply {
                action = ProxyVpnService.ACTION_DISCONNECT
            }
            appContext.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Safe catch: Failed to stop ProxyVpnService", e)
        }

        _state.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                connectedAt = 0L,
                latencyMs = null,
                errorMessage = null,
                engineStatus = null
            )
        }
    }

    fun toggleConnection(context: Context) {
        val currentStatus = _state.value.status
        if (currentStatus == ConnectionStatus.CONNECTED || currentStatus == ConnectionStatus.CONNECTING) {
            disconnect(context)
        } else {
            connect(context)
        }
    }

    fun setFloatingEnabled(context: Context, enabled: Boolean) {
        _state.update { it.copy(isFloatingEnabled = enabled) }
        try {
            val intent = Intent(context, FloatingWidgetService::class.java)
            if (enabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle FloatingWidgetService", e)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val myUid = android.os.Process.myUid()
        val initialRx = android.net.TrafficStats.getUidRxBytes(myUid).takeIf { it >= 0 } ?: 0L
        val initialTx = android.net.TrafficStats.getUidTxBytes(myUid).takeIf { it >= 0 } ?: 0L

        timerJob = scope.launch {
            while (isActive && _state.value.status == ConnectionStatus.CONNECTED) {
                delay(1000)
                val currentRx = android.net.TrafficStats.getUidRxBytes(myUid).takeIf { it >= 0 } ?: initialRx
                val currentTx = android.net.TrafficStats.getUidTxBytes(myUid).takeIf { it >= 0 } ?: initialTx
                val deltaRx = (currentRx - initialRx).coerceAtLeast(0L)
                val deltaTx = (currentTx - initialTx).coerceAtLeast(0L)

                _state.update { current ->
                    val newSecs = current.elapsedSeconds + 1
                    current.copy(
                        elapsedSeconds = newSecs,
                        bytesIn = deltaRx,
                        bytesOut = deltaTx
                    )
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
}
