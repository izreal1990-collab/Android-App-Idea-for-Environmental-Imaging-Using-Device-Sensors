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
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Settings Manager
 * Manages all app settings with SharedPreferences and provides reactive updates
 */
object SettingsManager {
    
    private const val PREFS_NAME = "environmental_imaging_settings"
    
    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    // Settings State Flows
    private val _sensorSettings = MutableStateFlow(SensorSettings())
    val sensorSettings: StateFlow<SensorSettings> = _sensorSettings.asStateFlow()
    
    private val _visualizationSettings = MutableStateFlow(VisualizationSettings())
    val visualizationSettings: StateFlow<VisualizationSettings> = _visualizationSettings.asStateFlow()
    
    private val _slamSettings = MutableStateFlow(SLAMSettings())
    val slamSettings: StateFlow<SLAMSettings> = _slamSettings.asStateFlow()
    
    private val _storageSettings = MutableStateFlow(StorageSettings())
    val storageSettings: StateFlow<StorageSettings> = _storageSettings.asStateFlow()
    
    /**
     * Initialize settings manager
     */
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAllSettings()
    }
    
    /**
     * Load all settings from SharedPreferences
     */
    private fun loadAllSettings() {
        loadSensorSettings()
        loadVisualizationSettings()
        loadSLAMSettings()
        loadStorageSettings()
    }
    
    // ========== SENSOR SETTINGS ==========
    
    data class SensorSettings(
        val wifiRttEnabled: Boolean = true,
        val wifiRttFrequency: Int = 10, // Hz
        val wifiRttAccuracyTarget: Float = 0.10f, // meters
        
        val bluetoothEnabled: Boolean = true,
        val bluetoothFrequency: Int = 5, // Hz
        val bluetoothAccuracyTarget: Float = 0.15f, // meters
        
        val acousticEnabled: Boolean = true,
        val acousticFrequency: Int = 8, // Hz
        val acousticAccuracyTarget: Float = 0.20f, // meters
        
        val imuEnabled: Boolean = true,
        val imuFrequency: Int = 50, // Hz
        
        val cameraEnabled: Boolean = true,
        val cameraFrameRate: Int = 30 // FPS
    )
    
    private fun loadSensorSettings() {
        _sensorSettings.value = SensorSettings(
            wifiRttEnabled = prefs.getBoolean("wifi_rtt_enabled", true),
            wifiRttFrequency = prefs.getInt("wifi_rtt_frequency", 10),
            wifiRttAccuracyTarget = prefs.getFloat("wifi_rtt_accuracy", 0.10f),
            
            bluetoothEnabled = prefs.getBoolean("bluetooth_enabled", true),
            bluetoothFrequency = prefs.getInt("bluetooth_frequency", 5),
            bluetoothAccuracyTarget = prefs.getFloat("bluetooth_accuracy", 0.15f),
            
            acousticEnabled = prefs.getBoolean("acoustic_enabled", true),
            acousticFrequency = prefs.getInt("acoustic_frequency", 8),
            acousticAccuracyTarget = prefs.getFloat("acoustic_accuracy", 0.20f),
            
            imuEnabled = prefs.getBoolean("imu_enabled", true),
            imuFrequency = prefs.getInt("imu_frequency", 50),
            
            cameraEnabled = prefs.getBoolean("camera_enabled", true),
            cameraFrameRate = prefs.getInt("camera_frame_rate", 30)
        )
    }
    
    fun updateSensorSettings(settings: SensorSettings) {
        prefs.edit().apply {
            putBoolean("wifi_rtt_enabled", settings.wifiRttEnabled)
            putInt("wifi_rtt_frequency", settings.wifiRttFrequency)
            putFloat("wifi_rtt_accuracy", settings.wifiRttAccuracyTarget)
            
            putBoolean("bluetooth_enabled", settings.bluetoothEnabled)
            putInt("bluetooth_frequency", settings.bluetoothFrequency)
            putFloat("bluetooth_accuracy", settings.bluetoothAccuracyTarget)
            
            putBoolean("acoustic_enabled", settings.acousticEnabled)
            putInt("acoustic_frequency", settings.acousticFrequency)
            putFloat("acoustic_accuracy", settings.acousticAccuracyTarget)
            
            putBoolean("imu_enabled", settings.imuEnabled)
            putInt("imu_frequency", settings.imuFrequency)
            
            putBoolean("camera_enabled", settings.cameraEnabled)
            putInt("camera_frame_rate", settings.cameraFrameRate)
            
            apply()
        }
        _sensorSettings.value = settings
    }
    
    // ========== VISUALIZATION SETTINGS ==========
    
    data class VisualizationSettings(
        val pointSize: Float = 5.0f,
        val colorMode: ColorMode = ColorMode.HEIGHT,
        val showGrid: Boolean = true,
        val showAxes: Boolean = true,
        val showTrajectory: Boolean = true,
        val enableLighting: Boolean = true,
        val backgroundColor: Int = 0xFF1A1A1A.toInt(), // Dark gray
        val cameraSensitivity: Float = 1.0f,
        val autoRotate: Boolean = false,
        val autoRotateSpeed: Float = 0.5f
    )
    
    enum class ColorMode {
        HEIGHT,
        INTENSITY,
        CUSTOM,
        GRADIENT,
        UNIFORM
    }
    
    private fun loadVisualizationSettings() {
        _visualizationSettings.value = VisualizationSettings(
            pointSize = prefs.getFloat("point_size", 5.0f),
            colorMode = ColorMode.valueOf(prefs.getString("color_mode", "HEIGHT") ?: "HEIGHT"),
            showGrid = prefs.getBoolean("show_grid", true),
            showAxes = prefs.getBoolean("show_axes", true),
            showTrajectory = prefs.getBoolean("show_trajectory", true),
            enableLighting = prefs.getBoolean("enable_lighting", true),
            backgroundColor = prefs.getInt("background_color", 0xFF1A1A1A.toInt()),
            cameraSensitivity = prefs.getFloat("camera_sensitivity", 1.0f),
            autoRotate = prefs.getBoolean("auto_rotate", false),
            autoRotateSpeed = prefs.getFloat("auto_rotate_speed", 0.5f)
        )
    }
    
    fun updateVisualizationSettings(settings: VisualizationSettings) {
        prefs.edit().apply {
            putFloat("point_size", settings.pointSize)
            putString("color_mode", settings.colorMode.name)
            putBoolean("show_grid", settings.showGrid)
            putBoolean("show_axes", settings.showAxes)
            putBoolean("show_trajectory", settings.showTrajectory)
            putBoolean("enable_lighting", settings.enableLighting)
            putInt("background_color", settings.backgroundColor)
            putFloat("camera_sensitivity", settings.cameraSensitivity)
            putBoolean("auto_rotate", settings.autoRotate)
            putFloat("auto_rotate_speed", settings.autoRotateSpeed)
            apply()
        }
        _visualizationSettings.value = settings
    }
    
    // ========== SLAM SETTINGS ==========
    
    data class SLAMSettings(
        val ekfProcessNoise: Float = 0.01f,
        val ekfMeasurementNoise: Float = 0.1f,
        val loopClosureEnabled: Boolean = true,
        val loopClosureThreshold: Float = 0.5f, // meters
        val landmarkConfidenceThreshold: Float = 0.7f,
        val maxLandmarkDistance: Float = 20.0f, // meters
        val optimizationEnabled: Boolean = true,
        val optimizationInterval: Int = 100, // frames
        val enableOutlierRejection: Boolean = true,
        val outlierThreshold: Float = 2.5f // standard deviations
    )
    
    private fun loadSLAMSettings() {
        _slamSettings.value = SLAMSettings(
            ekfProcessNoise = prefs.getFloat("ekf_process_noise", 0.01f),
            ekfMeasurementNoise = prefs.getFloat("ekf_measurement_noise", 0.1f),
            loopClosureEnabled = prefs.getBoolean("loop_closure_enabled", true),
            loopClosureThreshold = prefs.getFloat("loop_closure_threshold", 0.5f),
            landmarkConfidenceThreshold = prefs.getFloat("landmark_confidence", 0.7f),
            maxLandmarkDistance = prefs.getFloat("max_landmark_distance", 20.0f),
            optimizationEnabled = prefs.getBoolean("optimization_enabled", true),
            optimizationInterval = prefs.getInt("optimization_interval", 100),
            enableOutlierRejection = prefs.getBoolean("outlier_rejection_enabled", true),
            outlierThreshold = prefs.getFloat("outlier_threshold", 2.5f)
        )
    }
    
    fun updateSLAMSettings(settings: SLAMSettings) {
        prefs.edit().apply {
            putFloat("ekf_process_noise", settings.ekfProcessNoise)
            putFloat("ekf_measurement_noise", settings.ekfMeasurementNoise)
            putBoolean("loop_closure_enabled", settings.loopClosureEnabled)
            putFloat("loop_closure_threshold", settings.loopClosureThreshold)
            putFloat("landmark_confidence", settings.landmarkConfidenceThreshold)
            putFloat("max_landmark_distance", settings.maxLandmarkDistance)
            putBoolean("optimization_enabled", settings.optimizationEnabled)
            putInt("optimization_interval", settings.optimizationInterval)
            putBoolean("outlier_rejection_enabled", settings.enableOutlierRejection)
            putFloat("outlier_threshold", settings.outlierThreshold)
            apply()
        }
        _slamSettings.value = settings
    }
    
    // ========== STORAGE SETTINGS ==========
    
    data class StorageSettings(
        val autoSaveEnabled: Boolean = true,
        val autoSaveInterval: Int = 300, // seconds
        val maxSnapshotsLimit: Int = 50,
        val compressionEnabled: Boolean = true,
        val exportFormat: ExportFormat = ExportFormat.JSON
    )
    
    enum class ExportFormat {
        JSON,
        CSV,
        PLY,
        LAS
    }
    
    private fun loadStorageSettings() {
        _storageSettings.value = StorageSettings(
            autoSaveEnabled = prefs.getBoolean("auto_save_enabled", true),
            autoSaveInterval = prefs.getInt("auto_save_interval", 300),
            maxSnapshotsLimit = prefs.getInt("max_snapshots", 50),
            compressionEnabled = prefs.getBoolean("compression_enabled", true),
            exportFormat = ExportFormat.valueOf(prefs.getString("export_format", "JSON") ?: "JSON")
        )
    }
    
    fun updateStorageSettings(settings: StorageSettings) {
        prefs.edit().apply {
            putBoolean("auto_save_enabled", settings.autoSaveEnabled)
            putInt("auto_save_interval", settings.autoSaveInterval)
            putInt("max_snapshots", settings.maxSnapshotsLimit)
            putBoolean("compression_enabled", settings.compressionEnabled)
            putString("export_format", settings.exportFormat.name)
            apply()
        }
        _storageSettings.value = settings
    }
    
    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
        loadAllSettings()
    }
}
