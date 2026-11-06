package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalContext
import com.example.util.TelegramHelper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.MainUiState

@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onToggleFloating: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings & Guide",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        // 1. FLOATING OVERLAY PERMISSION & STATUS
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222F46)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberCyan.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Floating Window Permission",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (uiState.hasOverlayPermission) "Granted • Ready to float" else "Not Granted • Required",
                                fontSize = 12.sp,
                                color = if (uiState.hasOverlayPermission) EmeraldConnected else Color(0xFFFFB703)
                            )
                        }
                    }

                    if (!uiState.hasOverlayPermission) {
                        Button(
                            onClick = onRequestOverlayPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB703), contentColor = Color(0xFF090D16)),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("settings_grant_permission_btn")
                        ) {
                            Text("Grant", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = EmeraldConnected,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Enable Floating Controller",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Show floating '+' bubble over other apps",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Switch(
                        checked = uiState.proxyState.isFloatingEnabled,
                        onCheckedChange = { onToggleFloating() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberCyan,
                            checkedTrackColor = Color(0xFF004D40)
                        ),
                        modifier = Modifier.testTag("settings_floating_switch")
                    )
                }
            }
        }

        // 2. DETAILED BENGALI USER GUIDE (ব্যবহার নির্দেশিকা)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
            border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "অ্যাপটি কিভাবে ব্যবহার করবেন? (User Guide)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GuideStepItem(
                    step = "১",
                    title = "Proxy Details সেট করুন",
                    desc = "Profiles ট্যাবে গিয়ে আপনার Proxy Host, Port, Protocol (SOCKS5/HTTP) এবং ইউজারনেম-পাসওয়ার্ড দিয়ে সেভ করুন।"
                )

                GuideStepItem(
                    step = "২",
                    title = "নির্দিষ্ট কোনো App এ কানেক্ট করতে চান?",
                    desc = "Per-App ট্যাবে গিয়ে 'Only Selected Apps' নির্বাচন করুন এবং আপনার পছন্দের অ্যাপগুলোতে টিক দিন।"
                )

                GuideStepItem(
                    step = "৩",
                    title = "Floating Start দিন",
                    desc = "হোম পেজ বা সেটিংস থেকে 'Floating Controller Widget' সুইচটি অন করুন। ডিসপ্লে পারমিশন চাইলে একবারে Grant দিন।"
                )

                GuideStepItem(
                    step = "৪",
                    title = "যেকোনো অ্যাপের উপর থেকে '+' এ চাপুন",
                    desc = "স্ক্রিনে ভেসে থাকা '+' বাটনে ট্যাপ করলেই সাথে সাথে প্রক্সি কানেক্ট হবে এবং লাইভ রিয়েল-টাইম টাইমার (00:00:00) দেখা যাবে!"
                )

                GuideStepItem(
                    step = "৫",
                    title = "Floating বন্ধ করবেন কিভাবে?",
                    desc = "ফ্লোটিং কার্ড ওপেন করে নিচে থাকা 'Floating Off ✕' অপশন চাপলেই ফ্লোটিং সাথে সাথে বন্ধ হয়ে যাবে।"
                )
            }
        }

        // TELEGRAM COMMUNITY & SUPPORT CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_telegram_community_card"),
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
                            .clip(RoundedCornerShape(10.dp))
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
                            text = "Join channel for new proxy methods & 24/7 direct support",
                            fontSize = 11.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Join Our Channel
                    Button(
                        onClick = { TelegramHelper.openChannel(context) },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("settings_btn_join_telegram_channel"),
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

                    // Support Button
                    Button(
                        onClick = { TelegramHelper.openSupport(context) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_btn_telegram_support"),
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

        // 3. ABOUT & PROTOCOLS SUPPORT
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF101726)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222F46)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Supported Proxy Protocols",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "• SOCKS5: High speed UDP/TCP with user/pass auth\n" +
                            "• SOCKS4: Lightweight socket tunnel\n" +
                            "• HTTP / HTTPS: Standard web proxy with basic auth\n" +
                            "• Android VpnService: Kernel-level per-app traffic routing",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "DH PROXY (DH PREMIUM EDITION) • Version 1.0.0",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD166)
                )
                Text(
                    text = "Developing by DHRUBO",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberCyan
                )
            }
        }
    }
}

@Composable
private fun GuideStepItem(
    step: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CyberCyan),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF090D16)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}
