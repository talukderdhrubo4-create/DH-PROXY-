package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ConnectionStatus
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldConnected

@Composable
fun DigitalTimer(
    status: ConnectionStatus,
    formattedTime: String,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.CONNECTED
    val isConnecting = status == ConnectionStatus.CONNECTING

    val timerColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected
            isConnecting -> Color(0xFFFFB703)
            else -> Color(0xFF64748B)
        },
        label = "timerColor"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isConnected -> EmeraldConnected.copy(alpha = 0.5f)
            isConnecting -> Color(0xFFFFB703).copy(alpha = 0.4f)
            else -> Color(0xFF222F46)
        },
        label = "timerBorderColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070B12))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("digital_timer_box"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CONNECTION UPTIME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = Color(0xFF94A3B8)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = if (isConnected) formattedTime else "00:00:00",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = timerColor,
                    modifier = Modifier.testTag("timer_text_display")
                )
            }
        }
    }
}
