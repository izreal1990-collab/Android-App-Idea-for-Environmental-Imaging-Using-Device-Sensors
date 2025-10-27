/*
 * Environmental Imaging App
 * 
 * Developer: Jovan Blango
 * Copyright (c) 2025 Jovan Blango
 * 
 * An advanced Android application for environmental imaging and spatial mapping
 * using device sensors including camera, WiFi RTT, Bluetooth, acoustics, and IMU.
 * 
 * GitHub: https://github.com/izreal1990-collab/Android-App-Idea-for-Environmental-Imaging-Using-Device-Sensors
 */


package com.environmentalimaging.app.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.environmentalimaging.app.data.IMUMeasurement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.math.*

/**
 * IMU (Inertial Measurement Unit) sensor for device motion tracking
 * Combines accelerometer, gyroscope, and magnetometer data for SLAM dead-reckoning
 * Essential for tracking device movement between ranging measurements
 */
class IMUSensor(private val context: Context) : SensorEventListener {
    
    private val sensorManager: SensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    
    private val accelerometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }
    
    private val gyroscope: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    private val magnetometer: Sensor? by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }
    
    // Data channels for sensor measurements
    private val measurementChannel = Channel<IMUMeasurement>(Channel.UNLIMITED)
    val measurementFlow: Flow<IMUMeasurement> = measurementChannel.receiveAsFlow()
    
    // Latest sensor readings
    private var latestAcceleration = floatArrayOf(0f, 0f, 0f)
    private var latestAngularVelocity = floatArrayOf(0f, 0f, 0f)
    private var latestMagneticField: FloatArray? = null
    
    // Sensor timing
    private var lastAccelerometerTime = 0L
    private var lastGyroscopeTime = 0L
    private var lastMagnetometerTime = 0L
    
    // Calibration and filtering
    private val accelerometerBias = floatArrayOf(0f, 0f, 0f)
    private val gyroscopeBias = floatArrayOf(0f, 0f, 0f)
    private var isCalibrated = false
    
    companion object {
        private const val TAG = "IMUSensor"
        private const val SENSOR_DELAY_US = 20000 // 50 Hz sampling rate
        private const val GRAVITY_THRESHOLD = 0.1f // For gravity calibration
        private const val GYRO_BIAS_SAMPLES = 100 // Samples for gyroscope bias calculation
        
        // Low-pass filter coefficients
        private const val ACCEL_ALPHA = 0.8f
        private const val GYRO_ALPHA = 0.9f
    }
    
    /**
     * Check if IMU sensors are available
     */
    fun isAvailable(): Boolean {
        val hasAccelerometer = accelerometer != null
        val hasGyroscope = gyroscope != null
        
        Log.d(TAG, "IMU availability - Accelerometer: $hasAccelerometer, Gyroscope: $hasGyroscope, Magnetometer: ${magnetometer != null}")
        return hasAccelerometer && hasGyroscope
    }
    
    /**
     * Start IMU sensor monitoring
     */
    fun startMonitoring(): Boolean {
        try {
            if (!isAvailable()) {
                Log.e(TAG, "IMU sensors not available")
                return false
            }
            
            // Register sensor listeners
            accelerometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, SENSOR_DELAY_US)
                Log.d(TAG, "Accelerometer registered")
            }
            
            gyroscope?.let { sensor ->
                sensorManager.registerListener(this, sensor, SENSOR_DELAY_US)
                Log.d(TAG, "Gyroscope registered")
            }
            
            magnetometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, SENSOR_DELAY_US)
                Log.d(TAG, "Magnetometer registered")
            }
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting IMU monitoring", e)
            return false
        }
    }
    
    /**
     * Stop IMU sensor monitoring
     */
    fun stopMonitoring() {
        try {
            sensorManager.unregisterListener(this)
            measurementChannel.close()
            Log.d(TAG, "IMU monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping IMU monitoring", e)
        }
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        val currentTime = System.currentTimeMillis()
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                handleAccelerometerData(event.values.clone(), currentTime)
                lastAccelerometerTime = currentTime
            }
            
            Sensor.TYPE_GYROSCOPE -> {
                handleGyroscopeData(event.values.clone(), currentTime)
                lastGyroscopeTime = currentTime
            }
            
            Sensor.TYPE_MAGNETIC_FIELD -> {
                handleMagnetometerData(event.values.clone(), currentTime)
                lastMagnetometerTime = currentTime
            }
        }
        
        // Create combined measurement when we have recent data from both sensors
        if (abs(lastAccelerometerTime - lastGyroscopeTime) < 100) { // Within 100ms
            val measurement = IMUMeasurement(
                acceleration = latestAcceleration.clone(),
                angularVelocity = latestAngularVelocity.clone(),
                magneticField = latestMagneticField?.clone(),
                timestamp = currentTime
            )
            
            // Send measurement through channel
            measurementChannel.trySend(measurement)
        }
    }
    
    /**
     * Handle accelerometer data with filtering and calibration
     */
    private fun handleAccelerometerData(values: FloatArray, timestamp: Long) {
        if (!isCalibrated) {
            // Simple gravity-based calibration
            val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
            if (abs(magnitude - SensorManager.GRAVITY_EARTH) < GRAVITY_THRESHOLD) {
                // Device is roughly stationary, calculate bias
                for (i in values.indices) {
                    accelerometerBias[i] = values[i] - if (i == 2) SensorManager.GRAVITY_EARTH else 0f
                }
            }
        }
        
        // Apply bias correction and low-pass filtering
        for (i in values.indices) {
            val correctedValue = values[i] - accelerometerBias[i]
            latestAcceleration[i] = ACCEL_ALPHA * latestAcceleration[i] + (1 - ACCEL_ALPHA) * correctedValue
        }
    }
    
    /**
     * Handle gyroscope data with bias correction and filtering
     */
    private fun handleGyroscopeData(values: FloatArray, timestamp: Long) {
        // Apply bias correction and low-pass filtering
        for (i in values.indices) {
            val correctedValue = values[i] - gyroscopeBias[i]
            latestAngularVelocity[i] = GYRO_ALPHA * latestAngularVelocity[i] + (1 - GYRO_ALPHA) * correctedValue
        }
    }
    
    /**
     * Handle magnetometer data
     */
    private fun handleMagnetometerData(values: FloatArray, timestamp: Long) {
        latestMagneticField = values
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        when (sensor?.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                Log.d(TAG, "Accelerometer accuracy changed: $accuracy")
            }
            Sensor.TYPE_GYROSCOPE -> {
                Log.d(TAG, "Gyroscope accuracy changed: $accuracy")
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                Log.d(TAG, "Magnetometer accuracy changed: $accuracy")
            }
        }
    }
    
    /**
     * Calibrate gyroscope bias by collecting samples while stationary
     */
    suspend fun calibrateGyroscope() {
        Log.d(TAG, "Starting gyroscope calibration - keep device stationary")
        
        val samples = mutableListOf<FloatArray>()
        val startTime = System.currentTimeMillis()
        
        // Collect samples for bias calculation
        while (samples.size < GYRO_BIAS_SAMPLES && System.currentTimeMillis() - startTime < 10000) {
            samples.add(latestAngularVelocity.clone())
            kotlinx.coroutines.delay(100)
        }
        
        if (samples.size >= GYRO_BIAS_SAMPLES) {
            // Calculate average bias
            for (i in 0..2) {
                gyroscopeBias[i] = samples.map { it[i] }.average().toFloat()
            }
            
            isCalibrated = true
            Log.d(TAG, "Gyroscope calibration completed. Bias: [${gyroscopeBias.joinToString(", ")}]")
        } else {
            Log.w(TAG, "Gyroscope calibration failed - insufficient samples")
        }
    }
    
    /**
     * Get current device orientation using sensor fusion
     */
    fun getCurrentOrientation(): FloatArray {
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        
        if (latestMagneticField != null) {
            // Use accelerometer and magnetometer for absolute orientation
            val inclinationMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, latestAcceleration, latestMagneticField)) {
                SensorManager.getOrientation(rotationMatrix, orientation)
            }
        } else {
            // Fallback to accelerometer-only orientation
            val gravity = latestAcceleration.clone()
            val norm = sqrt(gravity[0] * gravity[0] + gravity[1] * gravity[1] + gravity[2] * gravity[2])
            
            if (norm > 0) {
                // Normalize gravity vector
                for (i in gravity.indices) {
                    gravity[i] /= norm
                }
                
                // Calculate pitch and roll from gravity
                orientation[1] = asin(-gravity[0]) // Pitch
                orientation[2] = atan2(gravity[1], gravity[2]) // Roll
                orientation[0] = 0f // Yaw (cannot be determined without magnetometer)
            }
        }
        
        return orientation
    }
    
    /**
     * Reset calibration
     */
    fun resetCalibration() {
        isCalibrated = false
        for (i in accelerometerBias.indices) {
            accelerometerBias[i] = 0f
            gyroscopeBias[i] = 0f
        }
        Log.d(TAG, "Calibration reset")
    }
}