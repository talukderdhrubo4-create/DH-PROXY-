package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppItem
import com.example.model.ConnectionStatus
import com.example.model.ProxyConfig
import com.example.model.ProxyState
import com.example.model.RoutingMode
import com.example.repository.ProxyRepository
import com.example.service.ProxyController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val proxyState: ProxyState = ProxyState(),
    val profiles: List<ProxyConfig> = emptyList(),
    val activeProfile: ProxyConfig? = null,
    val installedApps: List<AppItem> = emptyList(),
    val filteredApps: List<AppItem> = emptyList(),
    val appSearchQuery: String = "",
    val hideSystemApps: Boolean = false,
    val routingMode: RoutingMode = RoutingMode.ALL_APPS,
    val selectedPackages: Set<String> = emptySet(),
    val hasOverlayPermission: Boolean = false,
    val isTestingPing: Boolean = false,
    val pingResultMs: Long? = null,
    val pingError: String? = null,
    val isLoadingApps: Boolean = false,
    val userNoticeMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ProxyRepository(application)
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        ProxyController.init(application)
        loadData()
        observeProxyController()
    }

    private fun loadData() {
        val profiles = repository.getProfiles()
        val active = repository.getActiveProfile()
        val mode = repository.getRoutingMode()
        val selected = repository.getSelectedPackages()
        val overlayPerm = checkOverlayPermission(getApplication())

        _uiState.update {
            it.copy(
                profiles = profiles,
                activeProfile = active,
                routingMode = mode,
                selectedPackages = selected,
                hasOverlayPermission = overlayPerm
            )
        }

        refreshInstalledApps()
    }

    private fun observeProxyController() {
        viewModelScope.launch {
            ProxyController.state.collectLatest { state ->
                _uiState.update { it.copy(proxyState = state) }
            }
        }
    }

    fun refreshOverlayPermission() {
        val granted = checkOverlayPermission(getApplication())
        _uiState.update { it.copy(hasOverlayPermission = granted) }
    }

    private fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun toggleConnection() {
        ProxyController.toggleConnection(getApplication())
    }

    fun toggleFloatingWidget() {
        val current = _uiState.value.proxyState.isFloatingEnabled
        if (!current && !_uiState.value.hasOverlayPermission) {
            _uiState.update {
                it.copy(userNoticeMessage = "Please grant Display Over Other Apps permission first")
            }
            return
        }
        ProxyController.setFloatingEnabled(getApplication(), !current)
    }

    fun selectActiveProfile(config: ProxyConfig) {
        repository.setActiveProfileId(config.id)
        _uiState.update { it.copy(activeProfile = config) }
        ProxyController.updateConfig(config)
    }

    fun saveProfile(config: ProxyConfig) {
        repository.addOrUpdateProfile(config)
        val list = repository.getProfiles()
        _uiState.update {
            it.copy(
                profiles = list,
                activeProfile = if (it.activeProfile?.id == config.id) config else it.activeProfile
            )
        }
        if (_uiState.value.activeProfile?.id == config.id) {
            ProxyController.updateConfig(config)
        }
    }

    fun deleteProfile(id: String) {
        repository.deleteProfile(id)
        val list = repository.getProfiles()
        val active = repository.getActiveProfile()
        _uiState.update {
            it.copy(
                profiles = list,
                activeProfile = active
            )
        }
        active?.let { ProxyController.updateConfig(it) }
    }

    fun testPing(host: String, port: Int) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isTestingPing = true, pingResultMs = null, pingError = null)
            }
            val result = repository.testProxyPing(host, port)
            result.onSuccess { ms ->
                _uiState.update {
                    it.copy(isTestingPing = false, pingResultMs = ms, pingError = null)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTestingPing = false,
                        pingResultMs = null,
                        pingError = err.localizedMessage ?: "Failed to connect"
                    )
                }
            }
        }
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            val apps = repository.loadInstalledApps()
            _uiState.update { current ->
                val filtered = applyAppFilters(apps, current.appSearchQuery, current.hideSystemApps)
                current.copy(
                    installedApps = apps,
                    filteredApps = filtered,
                    isLoadingApps = false
                )
            }
        }
    }

    fun setAppSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = applyAppFilters(current.installedApps, query, current.hideSystemApps)
            current.copy(appSearchQuery = query, filteredApps = filtered)
        }
    }

    fun setHideSystemApps(hide: Boolean) {
        _uiState.update { current ->
            val filtered = applyAppFilters(current.installedApps, current.appSearchQuery, hide)
            current.copy(hideSystemApps = hide, filteredApps = filtered)
        }
    }

    fun setRoutingMode(mode: RoutingMode) {
        repository.setRoutingMode(mode)
        _uiState.update { it.copy(routingMode = mode) }
        ProxyController.updateRouting(mode, _uiState.value.selectedPackages.size)
    }

    fun toggleAppSelection(packageName: String) {
        val currentSelected = _uiState.value.selectedPackages.toMutableSet()
        if (currentSelected.contains(packageName)) {
            currentSelected.remove(packageName)
        } else {
            currentSelected.add(packageName)
        }
        repository.setSelectedPackages(currentSelected)

        // Update installedApps list selection state
        val updatedApps = _uiState.value.installedApps.map {
            if (it.packageName == packageName) it.copy(isSelected = currentSelected.contains(packageName))
            else it
        }

        _uiState.update { current ->
            val filtered = applyAppFilters(updatedApps, current.appSearchQuery, current.hideSystemApps)
            current.copy(
                selectedPackages = currentSelected,
                installedApps = updatedApps,
                filteredApps = filtered
            )
        }
        ProxyController.updateRouting(_uiState.value.routingMode, currentSelected.size)
    }

    fun selectAllVisibleApps(select: Boolean) {
        val currentSelected = _uiState.value.selectedPackages.toMutableSet()
        val visiblePackages = _uiState.value.filteredApps.map { it.packageName }

        if (select) {
            currentSelected.addAll(visiblePackages)
        } else {
            currentSelected.removeAll(visiblePackages.toSet())
        }

        repository.setSelectedPackages(currentSelected)

        val updatedApps = _uiState.value.installedApps.map {
            it.copy(isSelected = currentSelected.contains(it.packageName))
        }

        _uiState.update { current ->
            val filtered = applyAppFilters(updatedApps, current.appSearchQuery, current.hideSystemApps)
            current.copy(
                selectedPackages = currentSelected,
                installedApps = updatedApps,
                filteredApps = filtered
            )
        }
        ProxyController.updateRouting(_uiState.value.routingMode, currentSelected.size)
    }

    fun clearUserNotice() {
        _uiState.update { it.copy(userNoticeMessage = null) }
    }

    private fun applyAppFilters(
        apps: List<AppItem>,
        query: String,
        hideSystem: Boolean
    ): List<AppItem> {
        return apps.filter { app ->
            val matchesQuery = query.isBlank() ||
                    app.appName.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            val matchesSystem = !hideSystem || !app.isSystemApp
            matchesQuery && matchesSystem
        }
    }
}
