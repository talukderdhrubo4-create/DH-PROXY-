package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.window.Dialog
import com.example.model.ProxyConfig
import com.example.model.ProxyProtocol
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected

@Composable
fun ProxyDialog(
    initialConfig: ProxyConfig? = null,
    isTestingPing: Boolean = false,
    pingResultMs: Long? = null,
    pingError: String? = null,
    onTestPing: (host: String, port: Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: (ProxyConfig) -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "My SOCKS5 Proxy") }
    var host by remember { mutableStateOf(initialConfig?.host ?: "127.0.0.1") }
    var portText by remember { mutableStateOf(initialConfig?.port?.toString() ?: "1080") }
    var protocol by remember { mutableStateOf(initialConfig?.protocol ?: ProxyProtocol.SOCKS5) }
    var requiresAuth by remember { mutableStateOf(initialConfig?.requiresAuth ?: false) }
    var username by remember { mutableStateOf(initialConfig?.username ?: "") }
    var password by remember { mutableStateOf(initialConfig?.password ?: "") }
    var bypassList by remember { mutableStateOf(initialConfig?.bypassList ?: "127.0.0.1, localhost") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, Color(0xFF222F46), RoundedCornerShape(20.dp))
                .testTag("proxy_dialog_surface"),
            color = Color(0xFF101726)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialConfig == null) "Add Proxy Profile" else "Edit Proxy Profile",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan
                )

                Text(
                    text = "Configure host, port, protocol and credentials",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Profile Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_name_input"),
                    colors = outlinedColors()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Protocol Selector Row
                Text(
                    text = "Proxy Protocol",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProxyProtocol.values().forEach { proto ->
                        val isSelected = proto == protocol
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) ElectricBlue else Color(0xFF1E293B))
                                .border(
                                    1.dp,
                                    if (isSelected) CyberCyan else Color(0xFF334155),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    protocol = proto
                                    if (portText.isBlank() || portText == "1080" || portText == "8080") {
                                        portText = proto.defaultPort.toString()
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = proto.displayName,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Host & Port Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Server Host / IP") },
                        placeholder = { Text("e.g. 192.168.1.1") },
                        modifier = Modifier
                            .weight(2f)
                            .testTag("proxy_host_input"),
                        colors = outlinedColors()
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("proxy_port_input"),
                        colors = outlinedColors()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Ping test button & status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF182234))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Socket Reachability Test",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        when {
                            isTestingPing -> {
                                Text(
                                    text = "Testing connection...",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFB703)
                                )
                            }
                            pingResultMs != null -> {
                                Text(
                                    text = "Reachable: ${pingResultMs} ms latency",
                                    fontSize = 11.sp,
                                    color = EmeraldConnected,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            pingError != null -> {
                                Text(
                                    text = "Offline / Unreachable: $pingError",
                                    fontSize = 11.sp,
                                    color = Color(0xFFEF476F)
                                )
                            }
                            else -> {
                                Text(
                                    text = "Check if host:port responds",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 1080
                            onTestPing(host, port)
                        },
                        enabled = !isTestingPing && host.isNotBlank(),
                        modifier = Modifier.testTag("test_ping_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        if (isTestingPing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = CyberCyan
                            )
                        } else {
                            Icon(
                                Icons.Default.Speed,
                                contentDescription = "Test Ping",
                                modifier = Modifier.size(16.dp),
                                tint = CyberCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Ping", fontSize = 11.sp, color = CyberCyan)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Auth Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Authentication Required",
                        fontSize = 13.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = requiresAuth,
                        onCheckedChange = { requiresAuth = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = Color(0xFF004D40)
                        ),
                        modifier = Modifier.testTag("requires_auth_switch")
                    )
                }

                if (requiresAuth) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_username_input"),
                        colors = outlinedColors()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("proxy_password_input"),
                        colors = outlinedColors()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bypass List
                OutlinedTextField(
                    value = bypassList,
                    onValueChange = { bypassList = it },
                    label = { Text("Bypass Domains (comma separated)") },
                    placeholder = { Text("127.0.0.1, localhost") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("proxy_bypass_input"),
                    colors = outlinedColors()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }

                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 1080
                            val updated = (initialConfig ?: ProxyConfig(
                                name = name,
                                host = host,
                                port = port,
                                protocol = protocol
                            )).copy(
                                name = name.ifBlank { "Proxy $port" },
                                host = host.ifBlank { "127.0.0.1" },
                                port = port,
                                protocol = protocol,
                                requiresAuth = requiresAuth,
                                username = username,
                                password = password,
                                bypassList = bypassList
                            )
                            onSave(updated)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_proxy_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color(0xFF090D16)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CyberCyan,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = CyberCyan,
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = CyberCyan
)
