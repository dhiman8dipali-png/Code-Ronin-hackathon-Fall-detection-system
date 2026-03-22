package com.falldetection.ml

import com.falldetection.model.MLModelScore
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Quantum-Inspired Fusion Model for Fall Detection
 * 
 * Uses probabilistic superposition:
 * - Smooth estimation combines accelerometer, gyroscope, tilt, and jerk
 * - Multiple hypotheses (Safe/Fall) exist in superposition
 * - Confidence threshold triggers state collapse to final classification
 * - Weights adjusted based on sensor fusion feedback
 */
class QuantumInspiredFusionModel {

    // Model weights for sensor inputs
    private var w_acceleration = 0.4f      // Importance of acceleration magnitude
    private var w_jerk = 0.25f             // Importance of acceleration change
    private var w_gyro = 0.2f              // Importance of angular velocity
    private var w_tilt = 0.15f             // Importance of tilt angle

    // Thresholds
    companion object {
        const val FALL_DECISION_THRESHOLD = 0.7f   // Confidence threshold for collapse
        const val SAFE_DECISION_THRESHOLD = 0.3f   // Confidence threshold for safety
        const val ACCELERATION_FALL_THRESHOLD = 25f
        const val JERK_FALL_THRESHOLD = 15f
        const val GYRO_FALL_THRESHOLD = 5f
        const val TILT_FALL_THRESHOLD = 45f  // degrees
    }

    // Superposition state representation
    private var safeProbability = 0.95f  // P(Safe) - probability of safe state
    private var fallProbability = 0.05f  // P(Fall) - probability of fall state
    
    private val stateHistory = mutableListOf<Float>()

    /**
     * Compute quantum-inspired fall detection score
     * 
     * Uses superposition model:
     * - Represents system as superposition of {Safe, Fall}
     * - Updates probabilities based on sensor evidence
     * - Normalizes and collapses when confidence is high
     */
    fun computeScore(
        accelerationMagnitude: Float,
        jerk: Float,
        gyroscopeMagnitude: Float,
        tiltAngle: Float
    ): MLModelScore {

        // Normalize sensor inputs to 0-1 range
        val accelNorm = normalizeAcceleration(accelerationMagnitude)
        val jerkNorm = normalizeJerk(jerk)
        val gyroNorm = normalizeGyro(gyroscopeMagnitude)
        val tiltNorm = normalizeTilt(tiltAngle)

        // Compute individual evidence scores for fall
        val accelScore = computeAccelerationScore(accelNorm)
        val jerkScore = computeJerkScore(jerkNorm)
        val gyroScore = computeGyroScore(gyroNorm)
        val tiltScore = computeTiltScore(tiltNorm)

        // Apply weighted sum for fall evidence
        var fallEvidence = (w_acceleration * accelScore + 
                          w_jerk * jerkScore + 
                          w_gyro * gyroScore + 
                          w_tilt * tiltScore)

        // Update quantum superposition using logistic regression
        fallEvidence = logisticFunction(fallEvidence)

        // Update probability states using Bayesian-like update
        updateSuperposition(fallEvidence)

        // Calculate total score (compatibility with both models)
        val totalScore = fallProbability

        // Determine if collapse occurs (state becomes definite)
        val isFall = fallProbability > FALL_DECISION_THRESHOLD

        // Add to history for trend analysis
        stateHistory.add(fallProbability)
        if (stateHistory.size > 50) {
            stateHistory.removeAt(0)
        }

        return MLModelScore(
            accelerationScore = accelScore,
            jerkScore = jerkScore,
            gyroScore = gyroScore,
            tiltScore = tiltScore,
            totalScore = totalScore,
            isFall = isFall,
            superpositionProbability = fallProbability
        )
    }

    /**
     * Update quantum superposition states based on evidence
     * Uses exponential moving average for smooth transitions
     */
    private fun updateSuperposition(fallEvidence: Float) {
        val alpha = 0.3f  // Smoothing factor
        
        fallProbability = alpha * fallEvidence + (1 - alpha) * fallProbability
        safeProbability = 1.0f - fallProbability

        // Ensure probabilities remain valid
        fallProbability = fallProbability.coerceIn(0f, 1f)
        safeProbability = safeProbability.coerceIn(0f, 1f)
    }

    /**
     * Determine if superposition collapses to definite state
     */
    private fun shouldCollapse(confidence: Float): Boolean {
        return confidence > FALL_DECISION_THRESHOLD || confidence < SAFE_DECISION_THRESHOLD
    }

    private fun normalizeAcceleration(accel: Float): Float {
        return (accel / 35f).coerceIn(0f, 1f)
    }

    private fun normalizeJerk(jerk: Float): Float {
        return (jerk / 30f).coerceIn(0f, 1f)
    }

    private fun normalizeGyro(gyro: Float): Float {
        return (gyro / 10f).coerceIn(0f, 1f)
    }

    private fun normalizeTilt(tilt: Float): Float {
        return (tilt / 90f).coerceIn(0f, 1f)
    }

    private fun computeAccelerationScore(normalized: Float): Float {
        // S-curve for acceleration scoring
        return if (normalized > 0.3f) (normalized * normalized) else 0f
    }

    private fun computeJerkScore(normalized: Float): Float {
        // S-curve for jerk scoring
        return if (normalized > 0.3f) (normalized * normalized) else 0f
    }

    private fun computeGyroScore(normalized: Float): Float {
        // Angular velocity indicates rotation during fall
        return if (normalized > 0.2f) (normalized * 0.8f) else 0f
    }

    private fun computeTiltScore(normalized: Float): Float {
        // Tilt below 30 degrees (person is more horizontal) = higher fall probability
        return if (normalized < 0.33f) (0.5f - normalized) else 0f
    }

    /**
     * Logistic function for smooth probability mapping
     * f(x) = 1 / (1 + e^(-k*x))
     */
    private fun logisticFunction(x: Float, k: Float = 5f): Float {
        return (1f / (1f + exp(-k * (x - 0.5f)))).coerceIn(0f, 1f)
    }

    /**
     * Get trend of fall probability over last N samples
     */
    fun getFallProbabilityTrend(): Float {
        if (stateHistory.size < 5) return 0f
        
        val recent = stateHistory.takeLast(10).average()
        val older = stateHistory.take(10).average()
        
        return (recent - older).toFloat()
    }

    /**
     * Reset model state
     */
    fun reset() {
        safeProbability = 0.95f
        fallProbability = 0.05f
        stateHistory.clear()
    }

    /**
     * Adapt model weights based on feedback (for future learning)
     */
    fun updateWeights(accelWeight: Float, jerkWeight: Float, gyroWeight: Float, tiltWeight: Float) {
        w_acceleration = accelWeight
        w_jerk = jerkWeight
        w_gyro = gyroWeight
        w_tilt = tiltWeight
        
        // Normalize weights
        val total = w_acceleration + w_jerk + w_gyro + w_tilt
        w_acceleration /= total
        w_jerk /= total
        w_gyro /= total
        w_tilt /= total
    }

    fun getCurrentProbabilities(): Pair<Float, Float> = Pair(safeProbability, fallProbability)
}
