package com.falldetection.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.falldetection.model.IMUSensorData
import kotlinx.coroutines.channels.Channel
import kotlin.math.sqrt

/**
 * Sensor data collector with real-time streaming
 */
class SensorDataCollector(private val context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    
    private val dataChannel = Channel<IMUSensorData>(capacity = 100)
    
    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var lastGyroX = 0f
    private var lastGyroY = 0f
    private var lastGyroZ = 0f
    
    private var currentAccelX = 0f
    private var currentAccelY = 0f
    private var currentAccelZ = 0f
    private var currentGyroX = 0f
    private var currentGyroY = 0f
    private var currentGyroZ = 0f
    
    private var isCollecting = false

    fun startCollecting() {
        if (isCollecting) return
        
        sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME)
        isCollecting = true
    }

    fun stopCollecting() {
        if (!isCollecting) return
        
        sensorManager.unregisterListener(this)
        isCollecting = false
    }

    suspend fun getSensorDataFlow() = dataChannel

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccelX = currentAccelX
                lastAccelY = currentAccelY
                lastAccelZ = currentAccelZ
                
                currentAccelX = event.values[0]
                currentAccelY = event.values[1]
                currentAccelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = currentGyroX
                lastGyroY = currentGyroY
                lastGyroZ = currentGyroZ
                
                currentGyroX = event.values[0]
                currentGyroY = event.values[1]
                currentGyroZ = event.values[2]
            }
        }

        // When we have data from both sensors, emit
        if (currentAccelX != 0f && currentGyroX != 0f) {
            val sensorData = IMUSensorData(
                timestamp = System.currentTimeMillis(),
                accelerometerX = currentAccelX,
                accelerometerY = currentAccelY,
                accelerometerZ = currentAccelZ,
                gyroscopeX = currentGyroX,
                gyroscopeY = currentGyroY,
                gyroscopeZ = currentGyroZ,
                magnitude = calculateAccelerationMagnitude(),
                jerk = calculateJerk()
            )
            
            dataChannel.trySend(sensorData)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun calculateAccelerationMagnitude(): Float {
        return sqrt(
            currentAccelX * currentAccelX +
            currentAccelY * currentAccelY +
            currentAccelZ * currentAccelZ
        )
    }

    private fun calculateJerk(): Float {
        val deltaX = currentAccelX - lastAccelX
        val deltaY = currentAccelY - lastAccelY
        val deltaZ = currentAccelZ - lastAccelZ
        
        return sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)
    }

    fun getGyroscopeMagnitude(): Float {
        return sqrt(
            currentGyroX * currentGyroX +
            currentGyroY * currentGyroY +
            currentGyroZ * currentGyroZ
        )
    }

    fun getCurrentAcceleration(): Float = calculateAccelerationMagnitude()
    fun getCurrentJerk(): Float = calculateJerk()
    
    fun isMonitoring(): Boolean = isCollecting
}
