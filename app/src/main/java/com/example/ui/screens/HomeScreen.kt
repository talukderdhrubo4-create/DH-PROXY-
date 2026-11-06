package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import com.example.util.TelegramHelper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthMethod
import com.example.model.ConnectionStatus
import com.example.model.DnsResolution
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.ui.components.DigitalTimer
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.MainUiState

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onSaveProfile: (ProxyConfig) -> Unit,
    onToggleConnection: () -> Unit,
    onToggleFloating: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val proxyState = uiState.proxyState
    val active = uiState.activeProfile ?: ProxyConfig(name = "New Profile", host = "", port = 1080)

    // Form states mapped directly to active profile (Super Proxy layout)
    var profileName by remember(active.id) { mutableStateOf(active.name) }
    var protocol by remember(active.id) { mutableStateOf(active.protocol) }
    var serverHost by remember(active.id) { mutableStateOf(active.host) }
    var serverPort by remember(active.id) { mutableStateOf(active.port.toString()) }
    var authMethod by remember(active.id) {
        mutableStateOf(if (active.requiresAuth) AuthMethod.USER_PASSWORD else AuthMethod.NONE)
    }
    var username by remember(active.id) { mutableStateOf(active.username) }
    var password by remember(active.id) { mutableStateOf(active.password) }
    var dnsResolution by remember(active.id) { mutableStateOf(active.dnsResolution) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Dropdown expansion states
    var protocolMenuExpanded by remember { mutableStateOf(false) }
    var authMenuExpanded by remember { mutableStateOf(false) }
    var dnsMenuExpanded by remember { mutableStateOf(false) }

    // Local validation feedback
    var validationError by remember { mutableStateOf<String?>(null) }
    var saveSuccessMessage by remember { mutableStateOf(false) }

    val isConnected = proxyState.status == ConnectionStatus.CONNECTED
    val isConnecting = proxyState.status == ConnectionStatus.CONNECTING

    // Helper to build updated config
    fun buildCurrentConfig(): ProxyConfig {
        val portInt = serverPort.toIntOrNull() ?: protocol.defaultPort
        return active.copy(
            name = profileName.ifBlank { "New Profile" },
            protocol = protocol,
            host = serverHost.trim(),
            port = portInt,
            requiresAuth = authMethod == AuthMethod.USER_PASSWORD,
            username = username.trim(),
            password = password.trim(),
            dnsResolution = dnsResolution
        )
    }

    // Auto save on field changes
    fun performSave(): Boolean {
        val config = buildCurrentConfig()
        onSaveProfile(config)
        saveSuccessMessage = true
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. BRANDING HERO BANNER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dh_branding_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C1322)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00FF66).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = com.example.R.drawable.dh_proxy_logo),
                    contentDescription = "DH PROXY Logo",
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF00FF66), CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DH PROXY",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2E2207))
                                .border(1.dp, Color(0xFFFFD166), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "★ PREMIUM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD166)
                            )
                        }
                    }
                    Text(
                        text = "Developing by DHRUBO • 100% WORKING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FF66)
                    )
                }

                // Floppy Disk Save Button (like Super Proxy screenshot)
                IconButton(
                    onClick = { performSave() },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00FF66).copy(alpha = 0.2f))
                        .testTag("save_profile_icon_btn")
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = "Save Profile",
                        tint = Color(0xFF00FF66),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // TELEGRAM COMMUNITY & 24/7 SUPPORT CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("telegram_community_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101C2E)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF24A1DE).copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF24A1DE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Telegram",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Official Telegram Community",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Updates, Method tricks & Direct Support",
                            fontSize = 11.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Join Our Channel Button (https://t.me/dh_method)
                    Button(
                        onClick = { TelegramHelper.openChannel(context) },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("btn_join_telegram_channel"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF24A1DE),
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Join Our Channel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. Support Button (https://t.me/argentina1918)
                    Button(
                        onClick = { TelegramHelper.openSupport(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_telegram_support"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF38BDF8)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.HeadsetMic,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Support",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Overlay Permission Banner if not granted
        AnimatedVisibility(visible = !uiState.hasOverlayPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261D10)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB703)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB703),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Overlay Permission Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Enable 'Display Over Other Apps' to use the floating '+' widget.",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                    Button(
                        onClick = onRequestOverlayPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB703)),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Grant", fontSize = 11.sp, color = Color(0xFF090D16), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // VALIDATION ERROR ALERT (Strict check: Server, Port, User, Pass required!)
        val activeError = validationError ?: proxyState.errorMessage
        AnimatedVisibility(visible = activeError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1219)),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF476F)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color(0xFFEF476F),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = activeError ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF85A1),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. SUPER PROXY CONFIGURATION FORM (EXACT REPLICA OF USER SCREENSHOT)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("super_proxy_form_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF222F46))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Profile name field
                OutlinedTextField(
                    value = profileName,
                    onValueChange = {
                        profileName = it
                        validationError = null
                        performSave()
                    },
                    label = { Text("Profile name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_profile_name"),
                    shape = RoundedCornerShape(10.dp),
                    colors = proxyTextFieldColors()
                )

                // Protocol dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = protocol.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Protocol") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Protocol",
                                tint = CyberCyan,
                                modifier = Modifier.clickable { protocolMenuExpanded = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { protocolMenuExpanded = true }
                            .testTag("dropdown_protocol"),
                        shape = RoundedCornerShape(10.dp),
                        colors = proxyTextFieldColors()
                    )

                    DropdownMenu(
                        expanded = protocolMenuExpanded,
                        onDismissRequest = { protocolMenuExpanded = false }
                    ) {
                        ProxyProtocol.values().forEach { proto ->
                            DropdownMenuItem(
                                text = { Text(proto.displayName, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    protocol = proto
                                    if (serverPort.isBlank() || serverPort == "1080" || serverPort == "8080") {
                                        serverPort = proto.defaultPort.toString()
                                    }
                                    protocolMenuExpanded = false
                                    performSave()
                                }
                            )
                        }
                    }
                }

                // Server (Host / IP) field
                OutlinedTextField(
                    value = serverHost,
                    onValueChange = {
                        serverHost = it
                        validationError = null
                        performSave()
                    },
                    label = { Text("Server") },
                    placeholder = { Text("e.g. 192.168.1.1 or proxy.domain.com") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_server_host"),
                    shape = RoundedCornerShape(10.dp),
                    colors = proxyTextFieldColors()
                )

                // Port field
                OutlinedTextField(
                    value = serverPort,
                    onValueChange = {
                        serverPort = it.filter { ch -> ch.isDigit() }
                        validationError = null
                        performSave()
                    },
                    label = { Text("Port") },
                    placeholder = { Text("e.g. 1080") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_server_port"),
                    shape = RoundedCornerShape(10.dp),
                    colors = proxyTextFieldColors()
                )

                // Authentication method dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = authMethod.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Authentication method") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Auth",
                                tint = CyberCyan,
                                modifier = Modifier.clickable { authMenuExpanded = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { authMenuExpanded = true }
                            .testTag("dropdown_auth_method"),
                        shape = RoundedCornerShape(10.dp),
                        colors = proxyTextFieldColors()
                    )

                    DropdownMenu(
                        expanded = authMenuExpanded,
                        onDismissRequest = { authMenuExpanded = false }
                    ) {
                        AuthMethod.values().forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.displayName) },
                                onClick = {
                                    authMethod = method
                                    authMenuExpanded = false
                                    performSave()
                                }
                            )
                        }
                    }
                }

                // If Authentication method is Username/Password
                if (authMethod == AuthMethod.USER_PASSWORD) {
                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            validationError = null
                            performSave()
                        },
                        label = { Text("Username") },
                        placeholder = { Text("Enter username") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_username"),
                        shape = RoundedCornerShape(10.dp),
                        colors = proxyTextFieldColors()
                    )

                    // Password field with eye show/hide icon
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            validationError = null
                            performSave()
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password visibility",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_password"),
                        shape = RoundedCornerShape(10.dp),
                        colors = proxyTextFieldColors()
                    )
                }

                // DNS resolution dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dnsResolution.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("DNS resolution") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select DNS",
                                tint = CyberCyan,
                                modifier = Modifier.clickable { dnsMenuExpanded = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dnsMenuExpanded = true }
                            .testTag("dropdown_dns_resolution"),
                        shape = RoundedCornerShape(10.dp),
                        colors = proxyTextFieldColors()
                    )

                    DropdownMenu(
                        expanded = dnsMenuExpanded,
                        onDismissRequest = { dnsMenuExpanded = false }
                    ) {
                        DnsResolution.values().forEach { dns ->
                            DropdownMenuItem(
                                text = { Text(dns.displayName) },
                                onClick = {
                                    dnsResolution = dns
                                    dnsMenuExpanded = false
                                    performSave()
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. LIVE DIGITAL TIMER & CONNECTION STATUS
        DigitalTimer(
            status = proxyState.status,
            formattedTime = proxyState.formattedTime(),
            modifier = Modifier.fillMaxWidth()
        )

        if (isConnected && proxyState.engineStatus != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E19)),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldConnected.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldConnected)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = proxyState.engineStatus ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldConnected
                    )
                }
            }
        }

        // 4. PRIMARY CONNECT / DISCONNECT BUTTON WITH STRICT VALIDATION
        Button(
            onClick = {
                // Strict validation before connecting
                val currentConfig = buildCurrentConfig()
                onSaveProfile(currentConfig)

                if (serverHost.trim().isEmpty()) {
                    validationError = "Server IP / Host is required! Please enter Server."
                    return@Button
                }
                val portNum = serverPort.toIntOrNull()
                if (portNum == null || portNum <= 0 || portNum > 65535) {
                    validationError = "Please enter a valid Port number (1-65535)."
                    return@Button
                }
                if (authMethod == AuthMethod.USER_PASSWORD) {
                    if (username.trim().isEmpty() || password.trim().isEmpty()) {
                        validationError = "Authentication is enabled! Username and Password are required."
                        return@Button
                    }
                }

                validationError = null
                onToggleConnection()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("main_start_proxy_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) Color(0xFFEF476F) else CyberCyan,
                contentColor = Color(0xFF090D16)
            )
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color(0xFF090D16),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Connecting...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            } else if (isConnected) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("STOP PROXY", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF090D16))
                Spacer(modifier = Modifier.width(8.dp))
                Text("START PROXY", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF090D16))
            }
        }

        // 5. FLOATING CONTROLLER WIDGET CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141C2E)),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (proxyState.isFloatingEnabled) CyberCyan else Color(0xFF222F46)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (proxyState.isFloatingEnabled) CyberCyan.copy(alpha = 0.2f) else Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = if (proxyState.isFloatingEnabled) CyberCyan else Color(0xFF94A3B8)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Floating Controller Widget",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (proxyState.isFloatingEnabled) "Active on Screen • Tap '+' to toggle" else "Disabled • Toggle to float on screen",
                            fontSize = 11.sp,
                            color = if (proxyState.isFloatingEnabled) EmeraldConnected else Color(0xFF94A3B8)
                        )
                    }
                }

                Switch(
                    checked = proxyState.isFloatingEnabled,
                    onCheckedChange = { onToggleFloating() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberCyan,
                        checkedTrackColor = Color(0xFF004D40)
                    ),
                    modifier = Modifier.testTag("toggle_floating_widget_switch")
                )
            }
        }

        // 6. SPECIFIC APPS ROUTING SHORTCUT (PER-APP)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToApps() }
                .testTag("specific_apps_shortcut_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7B2CBF).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF7B2CBF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = "Apps",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Specific Apps Proxy (Per-App)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.selectedPackages.size} apps selected to route • Tap to edit",
                            fontSize = 12.sp,
                            color = Color(0xFFD0BCFF)
                        )
                    }
                }

                OutlinedButton(
                    onClick = onNavigateToApps,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Select Apps", fontSize = 11.sp, color = Color(0xFFD0BCFF))
                }
            }
        }
    }
}

@Composable
private fun proxyTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberCyan,
    unfocusedBorderColor = Color(0xFF2A374E),
    focusedLabelColor = CyberCyan,
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = CyberCyan
)
