package com.falldetection.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.falldetection.model.FallDetectionEvent
import com.falldetection.viewmodel.AlertViewModel
import kotlinx.coroutines.delay

@Composable
fun AlertScreen(viewModel: AlertViewModel, onDismiss: () -> Unit) {
    val alertState = viewModel.alertState.collectAsState()
    val countdownSeconds = viewModel.countdownSeconds.collectAsState()
    val isSoSTriggered = viewModel.isSoSTriggered.collectAsState()

    val event = alertState.value.event

    if (!alertState.value.isFallDetected) {
        return
    }

    val context = LocalContext.current

    DisposableEffect(Unit) {
        val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM)
        val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
        ringtone?.play()

        val vibrator = context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val pattern = longArrayOf(0, 500, 500)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
        } else {
            vibrator.vibrate(pattern, 0)
        }

        onDispose {
            ringtone?.stop()
            vibrator.cancel()
        }
    }

    LaunchedEffect(Unit) {
        var seconds = 5
        while (seconds > 0 && !isSoSTriggered.value) {
            viewModel.updateCountdown(seconds)
            delay(1000)
            seconds--
        }
        if (seconds == 0) {
            viewModel.triggerSoS()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF5722)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "🚨 FALL DETECTED!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    "Are you OK?",
                    fontSize = 20.sp,
                    color = Color.White
                )

                // Countdown Circle
                Box(
                    modifier = Modifier
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${countdownSeconds.value}s",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = countdownSeconds.value / 5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                // Event Details
                if (event != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Confidence: ${(alertState.value.confidence * 100).toInt()}%",
                                color = Color.White
                            )
                            if (alertState.value.quantumConfidence > 0) {
                                LinearProgressIndicator(
                                    progress = alertState.value.quantumConfidence,
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = Color.Cyan
                                )
                                Text(
                                    "Quantum Brain Sync: ${(alertState.value.quantumConfidence * 100).toInt()}%",
                                    color = Color.Cyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "Location: ${event.latitude.toFloat().toString().take(5)}, ${event.longitude.toFloat().toString().take(5)}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            viewModel.dismissAlert()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("I'm OK", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            viewModel.triggerSoS()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("SOS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                if (isSoSTriggered.value) {
                    Text(
                        "📱 Sending alerts to emergency contacts...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun SOSStatusScreen(event: FallDetectionEvent?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF44336)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "🚨 SOS INITIATED",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                "Emergency alerts sent to all contacts",
                fontSize = 16.sp,
                color = Color.White
            )

            if (event != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Location shared: ${event.mapsLink}", color = Color.White, fontSize = 12.sp)
                        Text("Confidence: ${(event.confidence * 100).toInt()}%", color = Color.White)
                    }
                }
            }

            Text(
                "Help is on the way!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
