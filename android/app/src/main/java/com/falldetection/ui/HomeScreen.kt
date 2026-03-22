package com.falldetection.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falldetection.model.SystemStatus
import com.falldetection.viewmodel.HomeScreenViewModel

@Composable
fun HomeScreen(viewModel: HomeScreenViewModel) {
    val systemStatus = viewModel.systemStatus.collectAsState()
    val isMonitoring = viewModel.isMonitoring.collectAsState()
    val fallCount = viewModel.fallCount.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Fall Detection",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
            }

            // Status Card
            StatusCard(systemStatus = systemStatus.value, isMonitoring = isMonitoring.value)

            // Control Buttons
            ControlButtonsSection(
                isMonitoring = isMonitoring.value,
                onStart = { viewModel.setMonitoring(true) },
                onStop = { viewModel.setMonitoring(false) }
            )

            // Statistics
            StatisticsCard(fallCount = fallCount.value)

            // Quick Actions
            QuickActionsSection()

            Spacer(modifier = Modifier.weight(1f))

            // Emergency Button at bottom
            SOSButton()
        }
    }
}

@Composable
fun StatusCard(systemStatus: SystemStatus, isMonitoring: Boolean) {
    val backgroundColor = when (systemStatus) {
        SystemStatus.SAFE -> Color(0xFF4CAF50)
        SystemStatus.MONITORING -> Color(0xFF2196F3)
        SystemStatus.FALL_DETECTED -> Color(0xFFFF5722)
        SystemStatus.ALERT_ACTIVE -> Color(0xFFFF9800)
        SystemStatus.SOS_TRIGGERED -> Color(0xFFF44336)
    }

    val statusText = when (systemStatus) {
        SystemStatus.SAFE -> "🟢 SAFE"
        SystemStatus.MONITORING -> "🔵 MONITORING"
        SystemStatus.FALL_DETECTED -> "🔴 FALL DETECTED"
        SystemStatus.ALERT_ACTIVE -> "⚠️ ALERT ACTIVE"
        SystemStatus.SOS_TRIGGERED -> "🚨 SOS TRIGGERED"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                statusText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isMonitoring) "System Active" else "System Inactive",
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ControlButtonsSection(isMonitoring: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onStart,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF4CAF50),
                disabledContainerColor = Color.Gray
            ),
            enabled = !isMonitoring
        ) {
            Text("Start", color = Color.White, fontSize = 12.sp)
        }

        Button(
            onClick = onStop,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336),
                disabledContainerColor = Color.Gray
            ),
            enabled = isMonitoring
        ) {
            Text("Stop", color = Color.White, fontSize = 12.sp)
        }

        Button(
            onClick = {
                val intent = android.content.Intent(context, com.falldetection.service.SensorMonitoringService::class.java).apply {
                    action = com.falldetection.service.SensorMonitoringService.ACTION_SIMULATE_FALL
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF9C27B0)
            )
        ) {
            Text("Simulate", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun StatisticsCard(fallCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Statistics (Last 24 Hours)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                StatisticItem("Falls Detected", "$fallCount")
                StatisticItem("Sent Alerts", "${fallCount * 2}")
                StatisticItem("Active Contacts", "3")
            }
        }
    }
}

@Composable
fun StatisticItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun QuickActionsSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionButton("📍 Location", Modifier.weight(1f))
        QuickActionButton("📧 Contacts", Modifier.weight(1f))
        QuickActionButton("📊 Logs", Modifier.weight(1f))
    }
}

@Composable
fun QuickActionButton(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SOSButton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(Color(0xFFF44336), shape = RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "🚨 SOS - TAP FOR IMMEDIATE ALERT",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
