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

package com.environmentalimaging.app.data

import android.content.Context
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for tracking scanning session data and real-time sensor status
 * Provides centralized access to performance metrics and sensor availability
 */
object ScanningSessionManager {
    
    private val _sessionData = MutableStateFlow(SessionData())
    val sessionData: StateFlow<SessionData> = _sessionData.asStateFlow()
    
    private val _sensorStatus = MutableStateFlow(RealTimeSensorStatus())
    val sensorStatus: StateFlow<RealTimeSensorStatus> = _sensorStatus.asStateFlow()
    
    private var sessionStartTime: Long = 0
    private var isScanning = false
    
    // Performance trend tracking
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val maxHistorySize = 100
    
    /**
     * Start a new scanning session
     */
    fun startSession() {
        sessionStartTime = System.currentTimeMillis()
        isScanning = true
        _sessionData.value = SessionData(
            isActive = true,
            startTime = sessionStartTime
        )
    }
    
    /**
     * Stop the current scanning session
     */
    fun stopSession() {
        isScanning = false
        _sessionData.value = _sessionData.value.copy(isActive = false)
    }
    
    /**
     * Update landmark count
     */
    fun updateLandmarks(count: Int) {
        _sessionData.value = _sessionData.value.copy(landmarkCount = count)
    }
    
    /**
     * Add a new measurement
     */
    fun addMeasurement(accuracy: Float) {
        val currentData = _sessionData.value
        val newCount = currentData.measurementCount + 1
        val newAvg = ((currentData.averageAccuracy * currentData.measurementCount) + accuracy) / newCount
        
        _sessionData.value = currentData.copy(
            measurementCount = newCount,
            averageAccuracy = newAvg
        )
    }
    
    /**
     * Update sensor status with real hardware checks
     */
    fun updateSensorStatus(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        val hasCamera = try {
            cameraManager?.cameraIdList?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
        
        _sensorStatus.value = _sensorStatus.value.copy(
            cameraAvailable = hasCamera,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Update sensor availability from DataAcquisitionModule
     */
    fun updateFromAcquisitionModule(
        wifiRtt: Boolean,
        bluetooth: Boolean,
        acoustic: Boolean,
        imu: Boolean,
        camera: Boolean = _sensorStatus.value.cameraAvailable
    ) {
        _sensorStatus.value = _sensorStatus.value.copy(
            wifiRttAvailable = wifiRtt,
            bluetoothAvailable = bluetooth,
            acousticAvailable = acoustic,
            imuAvailable = imu,
            cameraAvailable = camera,
            lastUpdateTime = System.currentTimeMillis()
        )
    }
    
    /**
     * Update sensor active state (whether currently being used)
     */
    fun setSensorActive(sensor: String, active: Boolean) {
        _sensorStatus.value = when (sensor.lowercase()) {
            "wifi", "wifirtt" -> _sensorStatus.value.copy(wifiRttActive = active)
            "bluetooth", "bt" -> _sensorStatus.value.copy(bluetoothActive = active)
            "acoustic", "audio" -> _sensorStatus.value.copy(acousticActive = active)
            "imu", "motion" -> _sensorStatus.value.copy(imuActive = active)
            "camera" -> _sensorStatus.value.copy(cameraActive = active)
            else -> _sensorStatus.value
        }
    }
    
    /**
     * Add performance snapshot for trend tracking
     */
    fun addPerformanceSnapshot(
        cpuUsage: Float,
        memoryUsage: Float,
        batteryLevel: Float,
        measurementRate: Float
    ) {
        val snapshot = PerformanceSnapshot(
            timestamp = System.currentTimeMillis(),
            cpuUsage = cpuUsage,
            memoryUsage = memoryUsage,
            batteryLevel = batteryLevel,
            measurementRate = measurementRate
        )
        
        performanceHistory.add(snapshot)
        if (performanceHistory.size > maxHistorySize) {
            performanceHistory.removeAt(0)
        }
    }
    
    /**
     * Get performance trend data
     */
    fun getPerformanceTrend(): List<PerformanceSnapshot> {
        return performanceHistory.toList()
    }
    
    /**
     * Get current session duration in seconds
     */
    fun getSessionDuration(): Long {
        return if (isScanning) {
            (System.currentTimeMillis() - sessionStartTime) / 1000
        } else {
            _sessionData.value.let {
                if (it.startTime > 0) {
                    (System.currentTimeMillis() - it.startTime) / 1000
                } else 0
            }
        }
    }
    
    /**
     * Reset all session data
     */
    fun reset() {
        _sessionData.value = SessionData()
        performanceHistory.clear()
    }
}

/**
 * Data class for session statistics
 */
data class SessionData(
    val isActive: Boolean = false,
    val startTime: Long = 0,
    val landmarkCount: Int = 0,
    val measurementCount: Int = 0,
    val averageAccuracy: Float = 0.0f
)

/**
 * Data class for real-time sensor status
 */
data class RealTimeSensorStatus(
    val wifiRttAvailable: Boolean = false,
    val bluetoothAvailable: Boolean = false,
    val acousticAvailable: Boolean = false,
    val imuAvailable: Boolean = false,
    val cameraAvailable: Boolean = false,
    
    val wifiRttActive: Boolean = false,
    val bluetoothActive: Boolean = false,
    val acousticActive: Boolean = false,
    val imuActive: Boolean = false,
    val cameraActive: Boolean = false,
    
    val lastUpdateTime: Long = 0
)

/**
 * Data class for performance trend tracking
 */
data class PerformanceSnapshot(
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val batteryLevel: Float,
    val measurementRate: Float
)
