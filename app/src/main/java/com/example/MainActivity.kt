package com.example

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.ui.screens.AppsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Overlay permission launcher
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshOverlayPermission()
        }

    // VPN permission launcher
    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.toggleConnection()
            }
        }

    // Notifications permission launcher (Android 13+)
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Permission result handled
        }

    private var targetTab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tabExtra = intent?.getIntExtra("NAV_TAB", -1) ?: -1
        if (tabExtra >= 0) targetTab = tabExtra

        if (intent?.getBooleanExtra("REQUEST_VPN_CONSENT", false) == true) {
            prepareVpnAndConnect()
        }

        requestInitialPermissions()

        setContent {
            MyApplicationTheme {
                MainAppContent(
                    viewModel = viewModel,
                    initialTab = targetTab,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onRequestVpnPermission = { prepareVpnAndConnect() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tabExtra = intent.getIntExtra("NAV_TAB", -1)
        if (tabExtra >= 0) targetTab = tabExtra

        if (intent.getBooleanExtra("REQUEST_VPN_CONSENT", false)) {
            prepareVpnAndConnect()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverlayPermission()
    }

    private fun requestInitialPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }
    }

    private fun prepareVpnAndConnect() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Already authorized
            viewModel.toggleConnection()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    initialTab: Int = 0,
    onRequestOverlayPermission: () -> Unit,
    onRequestVpnPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(initialTab) {
        if (initialTab in 0..3) {
            selectedTab = initialTab
        }
    }

    val isConnected = uiState.proxyState.status == ConnectionStatus.CONNECTED
    val isConnecting = uiState.proxyState.status == ConnectionStatus.CONNECTING

    LaunchedEffect(Unit) {
        snackbarHostState.showSnackbar("Developing by DHRUBO • Welcome to DH PROXY (DH Premium)")
    }

    LaunchedEffect(uiState.userNoticeMessage) {
        uiState.userNoticeMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.dh_proxy_logo),
                            contentDescription = "DH PROXY Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF00FF66), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "DH PROXY",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                // DH Premium Golden Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E2207))
                                        .border(1.dp, Color(0xFFFFB703), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "★ DH PREMIUM",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFFFFD166)
                                    )
                                }
                                if (uiState.proxyState.isFloatingEnabled) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CyberCyan.copy(alpha = 0.2f))
                                            .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Floating On",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberCyan
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Developing by DHRUBO",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CyberCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF090D16),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0F172A),
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard", fontSize = 11.sp) },
                    colors = navigationItemColors(),
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Profiles") },
                    label = { Text("Profiles", fontSize = 11.sp) },
                    colors = navigationItemColors(),
                    modifier = Modifier.testTag("nav_tab_profiles")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Apps, contentDescription = "Specific Apps") },
                    label = { Text("App Proxy", fontSize = 11.sp) },
                    colors = navigationItemColors(),
                    modifier = Modifier.testTag("nav_tab_apps")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = navigationItemColors(),
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF090D16)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    uiState = uiState,
                    onSaveProfile = { viewModel.saveProfile(it) },
                    onToggleConnection = { onRequestVpnPermission() },
                    onToggleFloating = {
                        if (!uiState.hasOverlayPermission && !uiState.proxyState.isFloatingEnabled) {
                            onRequestOverlayPermission()
                        } else {
                            viewModel.toggleFloatingWidget()
                        }
                    },
                    onRequestOverlayPermission = onRequestOverlayPermission,
                    onNavigateToApps = { selectedTab = 2 }
                )
                1 -> ProfilesScreen(
                    uiState = uiState,
                    onSelectProfile = { viewModel.selectActiveProfile(it) },
                    onSaveProfile = { viewModel.saveProfile(it) },
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onTestPing = { host, port -> viewModel.testPing(host, port) }
                )
                2 -> AppsScreen(
                    uiState = uiState,
                    onSetRoutingMode = { viewModel.setRoutingMode(it) },
                    onToggleAppSelection = { viewModel.toggleAppSelection(it) },
                    onSelectAllVisible = { viewModel.selectAllVisibleApps(it) },
                    onSearchQueryChange = { viewModel.setAppSearchQuery(it) },
                    onToggleHideSystemApps = { viewModel.setHideSystemApps(it) },
                    onRefreshApps = { viewModel.refreshInstalledApps() }
                )
                3 -> SettingsScreen(
                    uiState = uiState,
                    onToggleFloating = {
                        if (!uiState.hasOverlayPermission && !uiState.proxyState.isFloatingEnabled) {
                            onRequestOverlayPermission()
                        } else {
                            viewModel.toggleFloatingWidget()
                        }
                    },
                    onRequestOverlayPermission = onRequestOverlayPermission
                )
            }
        }
    }
}

@Composable
private fun navigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = CyberCyan,
    selectedTextColor = CyberCyan,
    unselectedIconColor = Color(0xFF64748B),
    unselectedTextColor = Color(0xFF64748B),
    indicatorColor = Color(0xFF1E293B)
)
