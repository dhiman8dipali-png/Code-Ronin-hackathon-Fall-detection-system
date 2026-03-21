package com.falldetection.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.falldetection.MainActivity
import com.falldetection.integration.TwilioIntegration
import com.falldetection.model.FallDetectionEvent
import com.falldetection.repository.FallDetectionDatabase
import com.falldetection.sensor.SensorDataCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background service for continuous sensor monitoring and fall detection
 */
class SensorMonitoringService : Service() {

    private val binder = LocalBinder()
    private lateinit var sensorCollector: SensorDataCollector
    private lateinit var fallDetectionAlgorithm: FallDetectionAlgorithm
    private lateinit var locationManager: LocationManager
    private lateinit var twilioIntegration: TwilioIntegration
    private lateinit var repository: com.falldetection.repository.FallDetectionRepository

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var isMonitoring = false

    companion object {
        private const val TAG = "SensorMonitoringService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "fall_detection_channel"
    }

    inner class LocalBinder : Binder() {
        fun getService(): SensorMonitoringService = this@SensorMonitoringService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        sensorCollector = SensorDataCollector(this)
        fallDetectionAlgorithm = FallDetectionAlgorithm()
        locationManager = LocationManager(this)
        twilioIntegration = TwilioIntegration(this)

        val database = FallDetectionDatabase.getDatabase(this)
        repository = com.falldetection.repository.FallDetectionRepository(
            database.fallDetectionEventDao(),
            database.emergencyContactDao()
        )

        createNotificationChannel()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        startMonitoring()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        isMonitoring = true
        sensorCollector.startCollecting()

        serviceScope.launch {
            val dataFlow = sensorCollector.getSensorDataFlow()

            dataFlow.collect { sensorData ->
                // TEMPORARILY DISABLED: Process sensor data through enhanced fall detection algorithm
                // val detectionResult = fallDetectionAlgorithm.processSensorData(sensorData)
                // Handle different types of fall detection with appropriate confidence thresholds
                // ... rest of the code commented out for testing
            }
        }
    }

    private fun handleFallDetection(detectionResult: EnhancedFallResult, fallType: FallDetectionAlgorithm.FallType) {
        serviceScope.launch {
            val location = locationManager.getCurrentLocation()
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0
            val mapsLink = locationManager.generateMapsLink(latitude, longitude)

            // Create fall event with enhanced data
            val event = FallDetectionEvent(
                timestamp = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                confidence = detectionResult.confidence,
                accelerationMagnitude = fallDetectionAlgorithm.getAverageAcceleration(),
                gyroscopeMagnitude = fallDetectionAlgorithm.getAverageGyro(),
                tiltAngle = fallDetectionAlgorithm.getTiltAngle(),
                mapsLink = mapsLink
            )

            // Save to database
            repository.insertEvent(event)

            // Notify main activity with fall type information
            sendBroadcast(Intent(ACTION_FALL_DETECTED).apply {
                putExtra("event", event)
                putExtra("confidence", detectionResult.confidence)
                putExtra("fallType", fallType.name)
                putExtra("activityLevel", detectionResult.activityLevel.name)
            })

            val fallTypeDescription = when (fallType) {
                FallDetectionAlgorithm.FallType.HUMAN_FALL -> "HUMAN FALL"
                FallDetectionAlgorithm.FallType.PHONE_FALL -> "PHONE FALL"
                else -> "UNKNOWN FALL"
            }

            Log.w(TAG, "$fallTypeDescription DETECTED! Confidence: ${detectionResult.confidence}, Activity: ${detectionResult.activityLevel}")
        }
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Fall Detection System")
            .setContentText("Monitoring for falls...")
            .setSmallIcon(android.R.drawable.ic_notification_overlay)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Fall Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Continuous monitoring for falls"
                enableVibration(true)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        sensorCollector.stopCollecting()
        fallDetectionAlgorithm.reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopMonitoring()
    }

    companion object {
        const val ACTION_FALL_DETECTED = "com.falldetection.FALL_DETECTED"
    }
}
