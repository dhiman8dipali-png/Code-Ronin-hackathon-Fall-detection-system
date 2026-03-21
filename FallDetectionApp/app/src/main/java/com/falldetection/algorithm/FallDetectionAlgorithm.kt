package com.falldetection.algorithm

import com.falldetection.model.IMUSensorData
import com.falldetection.ml.QuantumInspiredFusionModel
import kotlin.math.sqrt
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced Fall Detection Algorithm with Adaptive Thresholds
 *
 * Features:
 * - Distinguishes between phone falls and human falls
 * - Adaptive thresholds based on context and historical data
 * - Multi-stage fall detection with confidence scoring
 * - False positive reduction through pattern analysis
 * - Activity context awareness
 */
class FallDetectionAlgorithm {

    // Enhanced thresholds with adaptive adjustment
    companion object {
        // Base thresholds (will be adjusted adaptively)
        const val BASE_FREE_FALL_THRESHOLD = 0.5f  // m/s²
        const val BASE_IMPACT_THRESHOLD = 50f       // m/s²
        const val BASE_IMMOBILITY_THRESHOLD = 2f    // m/s²
        const val BASE_HIGH_IMPACT_THRESHOLD = 80f  // m/s² (phone drop)

        // Time windows
        const val FREE_FALL_DURATION_MS = 500       // 0.5 seconds
        const val IMMOBILITY_WINDOW_MS = 2000       // 2 seconds
        const val POST_FALL_WINDOW_MS = 3000        // 3 seconds after potential fall
        const val ACTIVITY_WINDOW_MS = 10000        // 10 seconds for activity assessment

        // Confidence thresholds
        const val HUMAN_FALL_CONFIDENCE = 0.75f
        const val PHONE_FALL_CONFIDENCE = 0.85f
        const val FALSE_POSITIVE_THRESHOLD = 0.3f
    }

    // Sensor data buffers
    private val sensorBuffer = mutableListOf<IMUSensorData>()
    private val activityBuffer = mutableListOf<IMUSensorData>()
    private val bufferSize = 200  // Increased for better analysis
    private val activityBufferSize = 100

    // State tracking
    private var freeFallStartTime: Long = 0
    private var isInFreeFall = false
    private var lastImpactTime: Long = 0
    private var potentialFallTime: Long = 0
    private var activityLevel = ActivityLevel.UNKNOWN

    // Adaptive thresholds (adjusted based on context)
    private var adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD
    private var adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD
    private var adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD

    // ML Model for enhanced detection
    private val fusionModel = QuantumInspiredFusionModel()

    // Historical data for adaptation
    private val fallHistory = mutableListOf<FallEvent>()
    private val falsePositiveHistory = mutableListOf<Long>()

    // Activity detection
    enum class ActivityLevel {
        STATIONARY, WALKING, RUNNING, UNKNOWN
    }

    enum class FallType {
        HUMAN_FALL, PHONE_FALL, NO_FALL
    }

    /**
     * Process new sensor data with enhanced fall detection
     */
    fun processSensorData(data: IMUSensorData): Unit {
        // Update buffers
        updateBuffers(data)

        // Assess current activity level
        activityLevel = assessActivityLevel()

        // Adapt thresholds based on activity and context
        adaptThresholds()

        // Perform multi-stage fall analysis
        performEnhancedFallAnalysis()
    }

    private fun updateBuffers(data: IMUSensorData) {
        sensorBuffer.add(data)
        if (sensorBuffer.size > bufferSize) {
            sensorBuffer.removeAt(0)
        }

        activityBuffer.add(data)
        if (activityBuffer.size > activityBufferSize) {
            activityBuffer.removeAt(0)
        }
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
        // Adjust thresholds based on activity level
        when (activityLevel) {
            ActivityLevel.STATIONARY -> {
                // More sensitive when stationary (higher chance of actual fall)
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 0.8f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 0.9f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 0.8f
            }
            ActivityLevel.WALKING -> {
                // Standard sensitivity
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD
            }
            ActivityLevel.RUNNING -> {
                // Less sensitive to avoid false positives during activity
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 1.3f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 1.2f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 1.2f
            }
            ActivityLevel.UNKNOWN -> {
                // Conservative defaults
                adaptiveFreeFallThreshold = BASE_FREE_FALL_THRESHOLD * 1.1f
                adaptiveImpactThreshold = BASE_IMPACT_THRESHOLD * 1.1f
                adaptiveImmobilityThreshold = BASE_IMMOBILITY_THRESHOLD * 1.1f
            }
        }

        // Further adjust based on recent false positives
        val recentFalsePositives = falsePositiveHistory.count {
            System.currentTimeMillis() - it < 300000 // Last 5 minutes
        }
        if (recentFalsePositives > 2) {
            // Increase thresholds to reduce false positives
            adaptiveFreeFallThreshold *= 1.2f
            adaptiveImpactThreshold *= 1.1f
        }
    }

    private fun performEnhancedFallAnalysis(): Unit {
        if (sensorBuffer.size < 10) {
            return
        }

        val currentTime = System.currentTimeMillis()
        val lastData = sensorBuffer.last()

        // Stage 1: Free Fall Detection with adaptive threshold
        val freeFallDetected = detectFreeFall(currentTime, lastData.magnitude)

        // Stage 2: Impact Detection with pattern analysis
        val impactDetected = detectImpact(currentTime, lastData)

        // Stage 3: Post-fall Analysis (immobility, orientation change)
        val postFallAnalysis = analyzePostFallBehavior(currentTime)

        // Stage 4: ML-based Classification using Quantum Fusion Model
        val mlScore = computeMLScore()

        // Stage 5: Fall Type Classification
        val fallType = classifyFallType(freeFallDetected, impactDetected, postFallAnalysis, mlScore)

        // Calculate confidence based on multiple factors
        val confidence = calculateConfidence(fallType, mlScore, postFallAnalysis)

        // Update state based on detection
        if (fallType != FallType.NO_FALL && confidence > HUMAN_FALL_CONFIDENCE) {
            potentialFallTime = currentTime
            recordFallEvent(fallType, confidence)
        }

        return
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
        val isHighImpact = magnitude > BASE_HIGH_IMPACT_THRESHOLD
        val isNormalImpact = magnitude > adaptiveImpactThreshold

        if (isHighImpact || isNormalImpact) {
            lastImpactTime = currentTime

            // Analyze impact pattern to distinguish phone vs human fall
            val impactPattern = analyzeImpactPattern(data)
            return impactPattern.isValidImpact
        }

        return false
    }

    private fun analyzeImpactPattern(data: IMUSensorData): ImpactPattern {
        if (sensorBuffer.size < 5) return ImpactPattern(false, 0f)

        // Look for sudden spike followed by oscillation (typical of human fall)
        // vs single sharp spike (typical of phone drop)
        val recentMagnitudes = sensorBuffer.takeLast(5).map { it.magnitude }
        val maxMagnitude = recentMagnitudes.maxOrNull() ?: 0f
        val minMagnitude = recentMagnitudes.minOrNull() ?: 0f
        val magnitudeRange = maxMagnitude - minMagnitude

        // Calculate jerk (rate of change of acceleration)
        val jerkValues = sensorBuffer.takeLast(5).map { it.jerk }
        val avgJerk = jerkValues.average().toFloat()

        // Human falls typically show:
        // - Higher magnitude range (body movement after impact)
        // - Multiple acceleration changes
        // - Higher jerk values
        val isValidImpact = magnitudeRange > 20f || abs(avgJerk) > 15f

        return ImpactPattern(isValidImpact, magnitudeRange)
    }

    private fun analyzePostFallBehavior(currentTime: Long): PostFallAnalysis {
        if (potentialFallTime == 0L || currentTime - potentialFallTime > POST_FALL_WINDOW_MS) {
            return PostFallAnalysis(false, 0f, 0f)
        }

        val postFallData = sensorBuffer.filter { it.timestamp > potentialFallTime }
        if (postFallData.size < 10) return PostFallAnalysis(false, 0f, 0f)

        // Check for immobility (human fall characteristic)
        val immobilityVariance = calculateVariance(postFallData.map { it.magnitude })
        val isImmobile = immobilityVariance < adaptiveImmobilityThreshold

        // Check for orientation change (person lying down)
        val tiltAngles = postFallData.map { calculateTiltAngle(it) }
        val avgTilt = tiltAngles.average().toFloat()
        val tiltChange = abs(avgTilt - calculateTiltAngle(sensorBuffer.first()))

        // Human falls show immobility AND significant tilt change
        val isHumanFallPattern = isImmobile && tiltChange > 30f

        return PostFallAnalysis(isHumanFallPattern, immobilityVariance, tiltChange)
    }

    private fun computeMLScore(): Float {
        if (sensorBuffer.size < 5) return 0f

        val avgAccel = sensorBuffer.takeLast(10).map { it.magnitude }.average().toFloat()
        val avgJerk = sensorBuffer.takeLast(10).map { it.jerk }.average().toFloat()
        val avgGyro = sensorBuffer.takeLast(10).map {
            sqrt(it.gyroscopeX * it.gyroscopeX + it.gyroscopeY * it.gyroscopeY + it.gyroscopeZ * it.gyroscopeZ)
        }.average().toFloat()
        val avgTilt = sensorBuffer.takeLast(10).map { calculateTiltAngle(it) }.average().toFloat()

        val mlResult = fusionModel.computeScore(avgAccel, avgJerk, avgGyro, avgTilt)
        return mlResult.totalScore
    }

    private fun classifyFallType(
        freeFall: Boolean,
        impact: Boolean,
        postFall: PostFallAnalysis,
        mlScore: Float
    ): FallType {
        // High-confidence phone fall: very high impact without free fall pattern
        if (impact && !freeFall && sensorBuffer.last().magnitude > BASE_HIGH_IMPACT_THRESHOLD) {
            return FallType.PHONE_FALL
        }

        // Human fall: free fall + impact + post-fall immobility + ML confidence
        if ((freeFall && impact) || postFall.isHumanFallPattern || mlScore > HUMAN_FALL_CONFIDENCE) {
            return FallType.HUMAN_FALL
        }

        // Check for false positive patterns
        if (isLikelyFalsePositive()) {
            return FallType.NO_FALL
        }

        return FallType.NO_FALL
    }

    private fun calculateConfidence(fallType: FallType, mlScore: Float, postFall: PostFallAnalysis): Float {
        return when (fallType) {
            FallType.HUMAN_FALL -> {
                // Combine ML score, post-fall analysis, and activity context
                val baseConfidence = mlScore
                val postFallBonus = if (postFall.isHumanFallPattern) 0.2f else 0f
                val activityBonus = if (activityLevel == ActivityLevel.STATIONARY) 0.1f else 0f
                min(1f, baseConfidence + postFallBonus + activityBonus)
            }
            FallType.PHONE_FALL -> {
                // Phone falls need very high confidence to avoid false alerts
                if (mlScore > PHONE_FALL_CONFIDENCE) 0.9f else 0.5f
            }
            FallType.NO_FALL -> 0f
        }
    }

    private fun isLikelyFalsePositive(): Boolean {
        if (sensorBuffer.size < 10) return false

        // Check for patterns that indicate false positives
        val recentData = sensorBuffer.takeLast(10)

        // Sudden phone movement (user picking up phone)
        val magnitudeChanges = recentData.zipWithNext { a, b -> abs(b.magnitude - a.magnitude) }
        val avgChange = magnitudeChanges.average().toFloat()

        // High frequency oscillations (not typical of falls)
        val gyroMagnitudes = recentData.map {
            sqrt(it.gyroscopeX * it.gyroscopeX + it.gyroscopeY * it.gyroscopeY + it.gyroscopeZ * it.gyroscopeZ)
        }
        val gyroVariance = calculateVariance(gyroMagnitudes)

        return avgChange > 30f || gyroVariance > 10f
    }

    private fun recordFallEvent(fallType: FallType, confidence: Float) {
        val event = FallEvent(System.currentTimeMillis(), fallType, confidence, activityLevel)
        fallHistory.add(event)

        // Keep only recent history
        if (fallHistory.size > 50) {
            fallHistory.removeAt(0)
        }
    }

    fun reportFalsePositive() {
        falsePositiveHistory.add(System.currentTimeMillis())

        // Keep only recent false positives
        falsePositiveHistory.removeAll { System.currentTimeMillis() - it > 3600000 } // 1 hour
    }

    // Helper functions
    private fun calculateVariance(values: List<Float>): Float {
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance)
    }

    private fun calculateTiltAngle(data: IMUSensorData): Float {
        val accelMagnitude = sqrt(
            data.accelerometerX * data.accelerometerX +
            data.accelerometerY * data.accelerometerY +
            data.accelerometerZ * data.accelerometerZ
        )
        return if (accelMagnitude > 0) {
            (Math.acos((data.accelerometerZ / accelMagnitude).toDouble()) * 180 / Math.PI).toFloat()
        } else 0f
    }

    // Public getters for UI/debugging
    fun getCurrentActivityLevel(): ActivityLevel = activityLevel
    fun getAdaptiveThresholds(): Triple<Float, Float, Float> =
        Triple(adaptiveFreeFallThreshold, adaptiveImpactThreshold, adaptiveImmobilityThreshold)
    fun getBufferSize(): Int = sensorBuffer.size
    fun getRecentFallHistory(): List<FallEvent> = fallHistory.takeLast(5)

    fun reset() {
        sensorBuffer.clear()
        activityBuffer.clear()
        isInFreeFall = false
        freeFallStartTime = 0
        lastImpactTime = 0
        potentialFallTime = 0
        fusionModel.reset()
    }
}

// Data classes for enhanced results
/*
data class EnhancedFallResult(
    val fallType: FallDetectionAlgorithm.FallType,
    val confidence: Float,
    val activityLevel: FallDetectionAlgorithm.ActivityLevel
)

data class ImpactPattern(
    val isValidImpact: Boolean,
    val magnitudeRange: Float
)

data class PostFallAnalysis(
    val isHumanFallPattern: Boolean,
    val immobilityVariance: Float,
    val tiltChange: Float
)

data class FallEvent(
    val timestamp: Long,
    val fallType: FallDetectionAlgorithm.FallType,
    val confidence: Float,
    val activityLevel: FallDetectionAlgorithm.ActivityLevel
)
*/
