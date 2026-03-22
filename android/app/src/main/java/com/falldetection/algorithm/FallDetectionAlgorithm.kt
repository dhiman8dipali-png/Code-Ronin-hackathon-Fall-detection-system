package com.falldetection.algorithm

import com.falldetection.model.IMUSensorData
import com.falldetection.ml.QuantumInspiredFusionModel
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced Fall Detection Algorithm with Adaptive Thresholds
 */
class FallDetectionAlgorithm {

    // Enhanced thresholds with adaptive adjustment
    companion object {
        const val BASE_FREE_FALL_THRESHOLD = 3.5f  // Realistic free fall drop
        const val BASE_IMPACT_THRESHOLD = 18f       // m/s²
        const val BASE_IMMOBILITY_THRESHOLD = 1f    // m/s²
        const val BASE_HIGH_IMPACT_THRESHOLD = 30f  // m/s² (phone drop)

        const val FREE_FALL_DURATION_MS = 250      // For ~30cm fall
        const val IMMOBILITY_WINDOW_MS = 1000
        const val POST_FALL_WINDOW_MS = 2000
        const val ACTIVITY_WINDOW_MS = 5000

        const val HUMAN_FALL_CONFIDENCE = 0.5f     // Higher threshold for alert
        const val PHONE_FALL_CONFIDENCE = 0.6f
    }

    private val sensorBuffer = mutableListOf<IMUSensorData>()
    private val activityBuffer = mutableListOf<IMUSensorData>()
    private val bufferSize = 200
    private val activityBufferSize = 100

    private var freeFallStartTime: Long = 0
    private var isInFreeFall = false
    private var lastImpactTime: Long = 0
    private var potentialFallTime: Long = 0
    private var activityLevel = ActivityLevel.UNKNOWN

    private var adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD
    private var adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD
    private var adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD

    private var lastDetectionTime: Long = 0
    private val detectionCoolDownMs = 5000L

    private val fusionModel = QuantumInspiredFusionModel()
    private val fallHistory = mutableListOf<FallEvent>()
    private val falsePositiveHistory = mutableListOf<Long>()

    enum class ActivityLevel {
        STATIONARY, WALKING, RUNNING, UNKNOWN
    }

    enum class FallType {
        HUMAN_FALL, PHONE_FALL, NO_FALL
    }

    /**
     * Process new sensor data and return detection result if a fall is detected
     */
    fun processSensorData(data: IMUSensorData): EnhancedFallResult? {
        updateBuffers(data)
        activityLevel = assessActivityLevel()
        adaptThresholds()
        return performEnhancedFallAnalysis()
    }

    private fun updateBuffers(data: IMUSensorData) {
        sensorBuffer.add(data)
        if (sensorBuffer.size > bufferSize) sensorBuffer.removeAt(0)
        activityBuffer.add(data)
        if (activityBuffer.size > activityBufferSize) activityBuffer.removeAt(0)
    }

    private fun assessActivityLevel(): ActivityLevel {
        if (activityBuffer.size < 20) return ActivityLevel.UNKNOWN
        val recentData = activityBuffer.takeLast(20)
        val avgMagnitude = recentData.map { it.magnitude }.average().toFloat()
        val magnitudeVariance = calculateVariance(recentData.map { it.magnitude })
        val avgGyro = recentData.map {
            sqrt(it.gyroscopeX * it.gyroscopeX + it.gyroscopeY * it.gyroscopeY + it.gyroscopeZ * it.gyroscopeZ)
        }.average().toFloat()

        return when {
            avgMagnitude < 10f && magnitudeVariance < 5f && avgGyro < 0.5f -> ActivityLevel.STATIONARY
            avgMagnitude in 10f..25f && magnitudeVariance in 5f..50f -> ActivityLevel.WALKING
            avgMagnitude > 25f || magnitudeVariance > 50f -> ActivityLevel.RUNNING
            else -> ActivityLevel.UNKNOWN
        }
    }

    private fun adaptThresholds() {
        when (activityLevel) {
            ActivityLevel.STATIONARY -> {
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 0.8f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 0.9f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 0.8f
            }
            ActivityLevel.WALKING -> {
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD
            }
            ActivityLevel.RUNNING -> {
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 1.3f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 1.2f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 1.2f
            }
            ActivityLevel.UNKNOWN -> {
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 1.1f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 1.1f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 1.1f
            }
        }
    }

    private fun performEnhancedFallAnalysis(): EnhancedFallResult? {
        if (sensorBuffer.size < 10) return null

        val currentTime = System.currentTimeMillis()
        val lastData = sensorBuffer.last()

        val freeFallDetected = detectFreeFall(currentTime, lastData.magnitude)
        val impactDetected = detectImpact(currentTime, lastData)
        val postFallAnalysis = analyzePostFallBehavior(currentTime)
        val mlScore = computeMLScore()

        val fallType = classifyFallType(freeFallDetected, impactDetected, postFallAnalysis, mlScore)
        val confidence = calculateConfidence(fallType, mlScore, postFallAnalysis)

        if (fallType != FallType.NO_FALL && confidence > HUMAN_FALL_CONFIDENCE) {
            if (currentTime - lastDetectionTime < detectionCoolDownMs) return null

            lastDetectionTime = currentTime
            potentialFallTime = currentTime
            recordFallEvent(fallType, confidence)
            return EnhancedFallResult(fallType, confidence, activityLevel, mlScore?.superpositionProbability ?: 0f)
        }

        return null
    }

    private fun detectFreeFall(currentTime: Long, magnitude: Float): Boolean {
        if (magnitude < adaptiveFreeFallThreshold) {
            if (!isInFreeFall) {
                freeFallStartTime = currentTime
                isInFreeFall = true
            }
            return (currentTime - freeFallStartTime) > FREE_FALL_DURATION_MS
        } else {
            isInFreeFall = false
            return false
        }
    }

    private fun detectImpact(currentTime: Long, data: IMUSensorData): Boolean {
        val magnitude = data.magnitude
        if (magnitude > adaptiveImpactThreshold) {
            lastImpactTime = currentTime
            return analyzeImpactPattern(data).isValidImpact
        }
        return false
    }

    private fun analyzeImpactPattern(data: IMUSensorData): ImpactPattern {
        if (sensorBuffer.size < 5) return ImpactPattern(false, 0f)
        val recentMagnitudes = sensorBuffer.takeLast(5).map { it.magnitude }
        val magnitudeRange = (recentMagnitudes.maxOrNull() ?: 0f) - (recentMagnitudes.minOrNull() ?: 0f)
        val avgJerk = sensorBuffer.takeLast(5).map { it.jerk }.average().toFloat()
        return ImpactPattern(magnitudeRange > 20f || abs(avgJerk) > 15f, magnitudeRange)
    }

    private fun analyzePostFallBehavior(currentTime: Long): PostFallAnalysis {
        if (potentialFallTime == 0L || currentTime - potentialFallTime > POST_FALL_WINDOW_MS) {
            return PostFallAnalysis(false, 0f, 0f)
        }
        val postFallData = sensorBuffer.filter { it.timestamp > potentialFallTime }
        if (postFallData.size < 10) return PostFallAnalysis(false, 0f, 0f)
        val immobilityVariance = calculateVariance(postFallData.map { it.magnitude })
        val isImmobile = immobilityVariance < adaptiveImmobilityThreshold
        val tiltAngles = postFallData.map { calculateTiltAngle(it) }
        val tiltChange = abs(tiltAngles.average().toFloat() - calculateTiltAngle(sensorBuffer.first()))
        return PostFallAnalysis(isImmobile && tiltChange > 30f, immobilityVariance, tiltChange)
    }

    private fun computeMLScore(): com.falldetection.model.MLModelScore? {
        if (sensorBuffer.size < 5) return null
        val avgAccel = sensorBuffer.takeLast(10).map { it.magnitude }.average().toFloat()
        val avgJerk = sensorBuffer.takeLast(10).map { it.jerk }.average().toFloat()
        val avgGyro = sensorBuffer.takeLast(10).map {
            sqrt(it.gyroscopeX * it.gyroscopeX + it.gyroscopeY * it.gyroscopeY + it.gyroscopeZ * it.gyroscopeZ)
        }.average().toFloat()
        val avgTilt = sensorBuffer.takeLast(10).map { calculateTiltAngle(it) }.average().toFloat()
        return fusionModel.computeScore(avgAccel, avgJerk, avgGyro, avgTilt)
    }

    private fun classifyFallType(ff: Boolean, imp: Boolean, post: PostFallAnalysis, mlScore: com.falldetection.model.MLModelScore?): FallType {
        val ml = mlScore?.totalScore ?: 0f
        val isQuantumFall = mlScore?.isFall == true
        if (imp && !ff && sensorBuffer.last().magnitude > BASE_HIGH_IMPACT_THRESHOLD) return FallType.PHONE_FALL
        if ((ff && imp) || post.isHumanFallPattern || isQuantumFall || ml > HUMAN_FALL_CONFIDENCE) return FallType.HUMAN_FALL
        return FallType.NO_FALL
    }

    private fun calculateConfidence(type: FallType, mlScore: com.falldetection.model.MLModelScore?, post: PostFallAnalysis): Float {
        val ml = mlScore?.totalScore ?: 0f
        return when (type) {
            FallType.HUMAN_FALL -> min(1f, ml + (if (post.isHumanFallPattern) 0.2f else 0f))
            FallType.PHONE_FALL -> if (ml > PHONE_FALL_CONFIDENCE) 0.9f else 0.5f
            else -> 0f
        }
    }

    private fun recordFallEvent(type: FallType, confidence: Float) {
        fallHistory.add(FallEvent(System.currentTimeMillis(), type, confidence, activityLevel))
        if (fallHistory.size > 50) fallHistory.removeAt(0)
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        return sqrt(values.map { (it - mean) * (it - mean) }.average().toFloat())
    }

    private fun calculateTiltAngle(data: IMUSensorData): Float {
        val accelMagnitude = sqrt(data.accelerometerX * data.accelerometerX + data.accelerometerY * data.accelerometerY + data.accelerometerZ * data.accelerometerZ)
        return if (accelMagnitude > 0) (Math.acos((data.accelerometerZ / accelMagnitude).toDouble()) * 180 / Math.PI).toFloat() else 0f
    }

    fun getAverageAcceleration() = if (sensorBuffer.isNotEmpty()) sensorBuffer.takeLast(10).map { it.magnitude }.average().toFloat() else 0f
    fun getAverageGyro() = if (sensorBuffer.isNotEmpty()) sensorBuffer.takeLast(10).map { sqrt(it.gyroscopeX * it.gyroscopeX + it.gyroscopeY * it.gyroscopeY + it.gyroscopeZ * it.gyroscopeZ) }.average().toFloat() else 0f
    fun getTiltAngle() = if (sensorBuffer.isNotEmpty()) calculateTiltAngle(sensorBuffer.last()) else 0f
    fun reset() {
        sensorBuffer.clear(); activityBuffer.clear(); isInFreeFall = false; potentialFallTime = 0
    }
}

data class EnhancedFallResult(val fallType: FallDetectionAlgorithm.FallType, val confidence: Float, val activityLevel: FallDetectionAlgorithm.ActivityLevel, val quantumConfidence: Float = 0f)
data class ImpactPattern(val isValidImpact: Boolean, val magnitudeRange: Float)
data class PostFallAnalysis(val isHumanFallPattern: Boolean, val immobilityVariance: Float, val tiltChange: Float)
data class FallEvent(val timestamp: Long, val fallType: FallDetectionAlgorithm.FallType, val confidence: Float, val activityLevel: FallDetectionAlgorithm.ActivityLevel)
