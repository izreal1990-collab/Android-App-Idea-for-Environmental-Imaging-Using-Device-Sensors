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
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.environmentalimaging.app.data.IMUMeasurement
import com.environmentalimaging.app.data.RangingMeasurement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Unified Data Acquisition Module that coordinates all environmental sensors
 * Implements the modular sensor fusion architecture from the research specifications
 */
class DataAcquisitionModule(private val context: Context) {
    
    // Sensor components
    private val wifiRttSensor: WiFiRttSensor? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WiFiRttSensor(context)
        } else null
    }
    
    private val bluetoothSensor: BluetoothRangingSensor by lazy {
        BluetoothRangingSensor(context)
    }
    
    private val acousticSensor: AcousticRangingSensor by lazy {
        AcousticRangingSensor(context)
    }
    
    private val imuSensor: IMUSensor by lazy {
        IMUSensor(context)
    }
    
    // Data flows
    private val _rangingMeasurements = MutableSharedFlow<List<RangingMeasurement>>()
    val rangingMeasurements: SharedFlow<List<RangingMeasurement>> = _rangingMeasurements.asSharedFlow()
    
    private val _imuMeasurements = MutableSharedFlow<IMUMeasurement>()
    val imuMeasurements: SharedFlow<IMUMeasurement> = _imuMeasurements.asSharedFlow()
    
    // Acquisition state
    private var acquisitionJob: Job? = null
    private var isAcquiring = false
    
    companion object {
        private const val TAG = "DataAcquisitionModule"
        
        // Acquisition intervals
        private const val WIFI_RTT_INTERVAL_MS = 1000L
        private const val BLUETOOTH_INTERVAL_MS = 2000L
        private const val ACOUSTIC_INTERVAL_MS = 3000L
    }
    
    /**
     * Check availability of all sensors
     */
    fun checkSensorAvailability(): SensorAvailability {
        val wifiRttAvailable = wifiRttSensor?.isAvailable() ?: false
        val bluetoothAvailable = bluetoothSensor.isAvailable()
        val acousticAvailable = acousticSensor.isAvailable()
        val imuAvailable = imuSensor.isAvailable()
        
        Log.d(TAG, "Sensor availability - WiFi RTT: $wifiRttAvailable, Bluetooth: $bluetoothAvailable, Acoustic: $acousticAvailable, IMU: $imuAvailable")
        
        return SensorAvailability(
            wifiRtt = wifiRttAvailable,
            bluetooth = bluetoothAvailable,
            acoustic = acousticAvailable,
            imu = imuAvailable
        )
    }
    
    /**
     * Start data acquisition from all available sensors
     */
    fun startAcquisition(config: AcquisitionConfig = AcquisitionConfig()) {
        if (isAcquiring) {
            Log.w(TAG, "Data acquisition already running")
            return
        }
        
        Log.d(TAG, "Starting data acquisition")
        isAcquiring = true
        
        acquisitionJob = CoroutineScope(Dispatchers.IO).launch {
            // Start IMU monitoring (continuous)
            if (config.enableIMU && imuSensor.isAvailable()) {
                launch {
                    startIMUAcquisition()
                }
            }
            
            // Start ranging sensors (periodic)
            if (config.enableWifiRtt && wifiRttSensor?.isAvailable() == true) {
                launch {
                    startWiFiRttAcquisition()
                }
            }
            
            if (config.enableBluetooth && bluetoothSensor.isAvailable()) {
                launch {
                    startBluetoothAcquisition()
                }
            }
            
            if (config.enableAcoustic && acousticSensor.isAvailable()) {
                launch {
                    startAcousticAcquisition()
                }
            }
        }
    }
    
    /**
     * Stop data acquisition
     */
    fun stopAcquisition() {
        if (!isAcquiring) {
            Log.w(TAG, "Data acquisition not running")
            return
        }
        
        Log.d(TAG, "Stopping data acquisition")
        isAcquiring = false
        
        acquisitionJob?.cancel()
        acquisitionJob = null
        
        // Stop individual sensors
        imuSensor.stopMonitoring()
        wifiRttSensor?.stopContinuousRanging()
        bluetoothSensor.stopContinuousRanging()
        acousticSensor.stopContinuousRanging()
    }
    
    /**
     * Start IMU data acquisition
     */
    private suspend fun startIMUAcquisition() = withContext(Dispatchers.IO) {
        try {
            // Calibrate IMU before starting
            imuSensor.calibrateGyroscope()
            
            // Start monitoring
            imuSensor.startMonitoring()
            
            // Collect IMU measurements and forward them
            imuSensor.measurementFlow.collect { measurement ->
                _imuMeasurements.emit(measurement)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in IMU acquisition", e)
        }
    }
    
    /**
     * Start WiFi RTT acquisition
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun startWiFiRttAcquisition() = withContext(Dispatchers.IO) {
        try {
            while (isAcquiring) {
                // Scan for RTT-capable access points
                val accessPoints = wifiRttSensor?.scanForRttCapableAPs() ?: emptyList()
                
                if (accessPoints.isNotEmpty()) {
                    // Perform ranging to found access points
                    val measurements = wifiRttSensor?.performRanging(accessPoints) ?: emptyList()
                    
                    if (measurements.isNotEmpty()) {
                        _rangingMeasurements.emit(measurements)
                    }
                }
                
                delay(WIFI_RTT_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in WiFi RTT acquisition", e)
        }
    }
    
    /**
     * Start Bluetooth ranging acquisition
     */
    private suspend fun startBluetoothAcquisition() = withContext(Dispatchers.IO) {
        try {
            while (isAcquiring) {
                // Scan for nearby Bluetooth devices
                val devices = bluetoothSensor.scanForDevices()
                
                if (devices.isNotEmpty()) {
                    // Try Channel Sounding first (if supported), fallback to RSSI
                    val measurements = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && 
                                          bluetoothSensor.isChannelSoundingSupported()) {
                        bluetoothSensor.performChannelSounding(devices)
                    } else {
                        bluetoothSensor.performRSSIRanging(devices)
                    }
                    
                    if (measurements.isNotEmpty()) {
                        _rangingMeasurements.emit(measurements)
                    }
                }
                
                delay(BLUETOOTH_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in Bluetooth acquisition", e)
        }
    }
    
    /**
     * Start acoustic ranging acquisition
     */
    private suspend fun startAcousticAcquisition() = withContext(Dispatchers.IO) {
        try {
            while (isAcquiring) {
                // Perform acoustic ranging measurement
                val measurements = acousticSensor.performSingleRanging()
                
                if (measurements.isNotEmpty()) {
                    _rangingMeasurements.emit(measurements)
                }
                
                delay(ACOUSTIC_INTERVAL_MS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in acoustic acquisition", e)
        }
    }
    
    /**
     * Perform single measurement cycle (for testing or one-shot acquisition)
     */
    suspend fun performSingleMeasurement(): AcquisitionResult = withContext(Dispatchers.IO) {
        try {
            val rangingMeasurements = mutableListOf<RangingMeasurement>()
            val currentIMU = imuSensor.getCurrentOrientation()
            
            // WiFi RTT measurement
            if (wifiRttSensor?.isAvailable() == true) {
                val accessPoints = wifiRttSensor?.scanForRttCapableAPs() ?: emptyList()
                if (accessPoints.isNotEmpty()) {
                    rangingMeasurements.addAll(wifiRttSensor?.performRanging(accessPoints) ?: emptyList())
                }
            }
            
            // Bluetooth measurement
            if (bluetoothSensor.isAvailable()) {
                val devices = bluetoothSensor.scanForDevices()
                if (devices.isNotEmpty()) {
                    rangingMeasurements.addAll(bluetoothSensor.performRSSIRanging(devices))
                }
            }
            
            // Acoustic measurement
            if (acousticSensor.isAvailable()) {
                rangingMeasurements.addAll(acousticSensor.performSingleRanging())
            }
            
            AcquisitionResult(
                rangingMeasurements = rangingMeasurements,
                deviceOrientation = currentIMU,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in single measurement", e)
            AcquisitionResult(emptyList(), floatArrayOf(0f, 0f, 0f), System.currentTimeMillis())
        }
    }
    
    /**
     * Get current sensor status
     */
    fun getSensorStatus(): SensorStatus {
        return SensorStatus(
            isAcquiring = isAcquiring,
            availability = checkSensorAvailability(),
            imuCalibrated = true // Simplified for now
        )
    }
}

/**
 * Configuration for data acquisition
 */
data class AcquisitionConfig(
    val enableWifiRtt: Boolean = true,
    val enableBluetooth: Boolean = true,
    val enableAcoustic: Boolean = true,
    val enableIMU: Boolean = true,
    val wifiRttInterval: Long = 1000L,
    val bluetoothInterval: Long = 2000L,
    val acousticInterval: Long = 3000L
)

/**
 * Sensor availability status
 */
data class SensorAvailability(
    val wifiRtt: Boolean,
    val bluetooth: Boolean,
    val acoustic: Boolean,
    val imu: Boolean
) {
    val anyAvailable: Boolean get() = wifiRtt || bluetooth || acoustic || imu
    val allAvailable: Boolean get() = wifiRtt && bluetooth && acoustic && imu
}

/**
 * Overall sensor status
 */
data class SensorStatus(
    val isAcquiring: Boolean,
    val availability: SensorAvailability,
    val imuCalibrated: Boolean
)

/**
 * Result of a single acquisition cycle
 */
data class AcquisitionResult(
    val rangingMeasurements: List<RangingMeasurement>,
    val deviceOrientation: FloatArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AcquisitionResult

        if (rangingMeasurements != other.rangingMeasurements) return false
        if (!deviceOrientation.contentEquals(other.deviceOrientation)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rangingMeasurements.hashCode()
        result = 31 * result + deviceOrientation.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}