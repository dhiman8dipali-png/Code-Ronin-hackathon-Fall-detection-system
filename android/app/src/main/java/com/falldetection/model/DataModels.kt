package com.falldetection.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import java.time.LocalDateTime

/**
 * IMU Sensor Data Point
 */
data class IMUSensorData(
    val timestamp: Long,
    val accelerometerX: Float,
    val accelerometerY: Float,
    val accelerometerZ: Float,
    val gyroscopeX: Float,
    val gyroscopeY: Float,
    val gyroscopeZ: Float,
    val magnitude: Float = 0f,
    val jerk: Float = 0f
)

/**
 * Fall Detection Event - stored in Room Database
 */
@Parcelize
@Entity(tableName = "fall_events")
data class FallDetectionEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val confidence: Float,
    val accelerationMagnitude: Float,
    val gyroscopeMagnitude: Float,
    val tiltAngle: Float,
    val alertCancelled: Boolean = false,
    val sosTriggered: Boolean = false,
    val mapsLink: String = ""
) : Parcelable

/**
 * Emergency Contact
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String,
    val email: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val isPrimary: Boolean = false
)

/**
 * System Status
 */
enum class SystemStatus {
    SAFE,
    MONITORING,
    FALL_DETECTED,
    ALERT_ACTIVE,
    SOS_TRIGGERED
}

/**
 * Alert State for UI
 */
data class AlertState(
    val isFallDetected: Boolean = false,
    val confidence: Float = 0f,
    val quantumConfidence: Float = 0f,
    val countdownSeconds: Int = 5,
    val event: FallDetectionEvent? = null
)

/**
 * ML Model Score
 */
data class MLModelScore(
    val accelerationScore: Float,
    val jerkScore: Float,
    val gyroScore: Float,
    val tiltScore: Float,
    val totalScore: Float,
    val isFall: Boolean,
    val superpositionProbability: Float = 0.5f
)
