package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ProxyConfig
import com.example.ui.components.ProxyDialog
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.MainUiState

@Composable
fun ProfilesScreen(
    uiState: MainUiState,
    onSelectProfile: (ProxyConfig) -> Unit,
    onSaveProfile: (ProxyConfig) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onTestPing: (host: String, port: Int) -> Unit
) {
    var editingProfile by remember { mutableStateOf<ProxyConfig?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isCreatingNew = true },
                containerColor = CyberCyan,
                contentColor = Color(0xFF090D16),
                modifier = Modifier.testTag("add_proxy_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Proxy")
            }
        },
        containerColor = Color(0xFF090D16)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Proxy Configurations",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Add and switch between HTTP and SOCKS5 proxy servers",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            if (uiState.profiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No proxy profiles yet", color = Color(0xFF94A3B8), fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { isCreatingNew = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color(0xFF090D16))
                        ) {
                            Text("Create First Profile")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(uiState.profiles, key = { it.id }) { config ->
                        val isActive = uiState.activeProfile?.id == config.id
                        ProfileCard(
                            config = config,
                            isActive = isActive,
                            onSelect = { onSelectProfile(config) },
                            onEdit = { editingProfile = config },
                            onDelete = { onDeleteProfile(config.id) },
                            onTestPing = { onTestPing(config.host, config.port) },
                            isTestingPing = uiState.isTestingPing,
                            pingResultMs = if (uiState.activeProfile?.id == config.id) uiState.pingResultMs else null
                        )
                    }
                }
            }
        }
    }

    if (isCreatingNew) {
        ProxyDialog(
            initialConfig = null,
            isTestingPing = uiState.isTestingPing,
            pingResultMs = uiState.pingResultMs,
            pingError = uiState.pingError,
            onTestPing = onTestPing,
            onDismiss = { isCreatingNew = false },
            onSave = {
                onSaveProfile(it)
                onSelectProfile(it)
                isCreatingNew = false
            }
        )
    }

    editingProfile?.let { config ->
        ProxyDialog(
            initialConfig = config,
            isTestingPing = uiState.isTestingPing,
            pingResultMs = uiState.pingResultMs,
            pingError = uiState.pingError,
            onTestPing = onTestPing,
            onDismiss = { editingProfile = null },
            onSave = {
                onSaveProfile(it)
                editingProfile = null
            }
        )
    }
}

@Composable
private fun ProfileCard(
    config: ProxyConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTestPing: () -> Unit,
    isTestingPing: Boolean,
    pingResultMs: Long?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onSelect() }
            .testTag("profile_card_${config.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isActive) CyberCyan else Color(0xFF222F46)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Select Profile",
                        tint = if (isActive) CyberCyan else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = config.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Protocol Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ElectricBlue.copy(alpha = 0.2f))
                        .border(1.dp, ElectricBlue.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = config.protocol.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Host & Port
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF090D16))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${config.host}:${config.port}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFCBD5E1)
                )

                if (config.requiresAuth) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Auth",
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auth: ${config.username}", fontSize = 11.sp, color = Color(0xFFFFB703))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: Ping test, Edit, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = onTestPing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("card_ping_button")
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (pingResultMs != null) "$pingResultMs ms" else "Test Ping",
                        fontSize = 11.sp,
                        color = CyberCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("edit_profile_button")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("delete_profile_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Profile",
                            tint = Color(0xFFEF476F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
