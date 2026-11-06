package com.example.ui.screens

import android.widget.ImageView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.AppItem
import com.example.model.RoutingMode
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.MainUiState

@Composable
fun AppsScreen(
    uiState: MainUiState,
    onSetRoutingMode: (RoutingMode) -> Unit,
    onToggleAppSelection: (String) -> Unit,
    onSelectAllVisible: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleHideSystemApps: (Boolean) -> Unit,
    onRefreshApps: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .padding(16.dp)
    ) {
        Text(
            text = "Per-App Proxy Tunneling",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Select which specific installed apps should route traffic through proxy",
            fontSize = 12.sp,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 1. ROUTING MODE SELECTOR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141C2E))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RoutingMode.values().forEach { mode ->
                val isSelected = uiState.routingMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CyberCyan else Color.Transparent)
                        .clickable { onSetRoutingMode(mode) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color(0xFF090D16) else Color(0xFFCBD5E1)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Info Banner based on selected mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF101726))
                .border(1.dp, Color(0xFF222F46), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = when (uiState.routingMode) {
                    RoutingMode.ALL_APPS -> "🌐 Global Mode: All application network requests will route through proxy."
                    RoutingMode.SELECTED_ONLY -> "🎯 Specific Mode: ONLY the ${uiState.selectedPackages.size} selected apps below will use proxy."
                    RoutingMode.BYPASS_SELECTED -> "🛡️ Bypass Mode: All apps use proxy EXCEPT the ${uiState.selectedPackages.size} selected apps."
                },
                fontSize = 11.sp,
                color = CyberCyan
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.appSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search installed apps...", color = Color(0xFF64748B)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = CyberCyan)
            },
            trailingIcon = {
                if (uiState.appSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyberCyan,
                unfocusedBorderColor = Color(0xFF222F46),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = CyberCyan
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter / Action shortcuts row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = uiState.hideSystemApps,
                    onCheckedChange = onToggleHideSystemApps,
                    colors = CheckboxDefaults.colors(
                        checkedColor = CyberCyan,
                        checkmarkColor = Color(0xFF090D16)
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hide System Apps",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.clickable { onToggleHideSystemApps(!uiState.hideSystemApps) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onSelectAllVisible(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Select All", fontSize = 10.sp, color = CyberCyan)
                }

                OutlinedButton(
                    onClick = { onSelectAllVisible(false) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Clear All", fontSize = 10.sp, color = Color(0xFFEF476F))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2. INSTALLED APPS LIST
        if (uiState.isLoadingApps) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = CyberCyan, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Loading installed applications...", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else if (uiState.filteredApps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching apps found", color = Color(0xFF94A3B8), fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.filteredApps, key = { it.packageName }) { app ->
                    val isSelected = uiState.selectedPackages.contains(app.packageName)
                    AppItemRow(
                        app = app,
                        isSelected = isSelected,
                        onToggle = { onToggleAppSelection(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppItemRow(
    app: AppItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .testTag("app_row_${app.packageName}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) CyberCyan.copy(alpha = 0.5f) else Color(0xFF1F293D)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // App Icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A2438)),
                    contentAlignment = Alignment.Center
                ) {
                    if (app.icon != null) {
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    setImageDrawable(app.icon)
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.appName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        if (app.isSystemApp) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "System",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Text(
                        text = app.packageName,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1
                    )
                }
            }

            Switch(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberCyan,
                    checkedTrackColor = Color(0xFF004D40)
                ),
                modifier = Modifier.testTag("app_switch_${app.packageName}")
            )
        }
    }
}
