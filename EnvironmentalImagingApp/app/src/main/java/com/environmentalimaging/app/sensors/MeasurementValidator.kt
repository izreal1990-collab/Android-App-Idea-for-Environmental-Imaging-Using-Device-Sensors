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

import android.util.Log
import com.environmentalimaging.app.data.RangingMeasurement
import com.environmentalimaging.app.data.RangingType
import java.util.LinkedList
import kotlin.math.abs

/**
 * Measurement validation layer to pre-filter ranging data before SLAM processing
 * Checks for physical plausibility, consistency, and sensor-specific error patterns
 */
class MeasurementValidator {
    
    companion object {
        private const val TAG = "MeasurementValidator"
        
        // Physical constraints
        private const val MIN_DISTANCE = 0.1f // 10cm minimum (below this is noise)
        private const val MAX_DISTANCE = 50.0f // 50m maximum reasonable indoor range
        private const val MAX_SPEED = 10.0f // 10 m/s maximum reasonable device speed
        
        // Consistency check parameters
        private const val HISTORY_SIZE = 10
        private const val MAX_RATE_OF_CHANGE = 5.0f // m/s maximum distance change rate
        private const val CONSISTENCY_THRESHOLD = 3.0f // Standard deviations
        
        // Sensor-specific accuracy thresholds
        private const val WIFI_RTT_MAX_ACCURACY = 10.0f
        private const val BLUETOOTH_MAX_ACCURACY = 5.0f
        private const val ACOUSTIC_MAX_ACCURACY = 1.0f
    }
    
    // Measurement history per source
    private val measurementHistory = mutableMapOf<String, LinkedList<MeasurementRecord>>()
    
    /**
     * Validate a ranging measurement
     * Returns true if measurement passes all validation checks
     */
    fun validate(measurement: RangingMeasurement): ValidationResult {
        val validationErrors = mutableListOf<String>()
        
        // Check 1: Physical plausibility
        if (!isPhysicallyPlausible(measurement)) {
            validationErrors.add("Distance ${measurement.distance}m outside physical bounds [${MIN_DISTANCE}, ${MAX_DISTANCE}]")
        }
        
        // Check 2: Sensor-specific accuracy threshold
        if (!isAccuracyReasonable(measurement)) {
            validationErrors.add("Accuracy ${measurement.accuracy}m exceeds threshold for ${measurement.measurementType}")
        }
        
        // Check 3: Temporal consistency
        val consistencyCheck = checkTemporalConsistency(measurement)
        if (!consistencyCheck.isConsistent) {
            validationErrors.add("Temporal inconsistency: ${consistencyCheck.reason}")
        }
        
        // Check 4: Rate of change
        if (!isRateOfChangeReasonable(measurement)) {
            validationErrors.add("Rate of change exceeds maximum expected speed")
        }
        
        // Record measurement for future validation
        recordMeasurement(measurement)
        
        val isValid = validationErrors.isEmpty()
        if (!isValid) {
            Log.w(TAG, "Measurement validation failed for ${measurement.sourceId}: ${validationErrors.joinToString("; ")}")
        }
        
        return ValidationResult(isValid, validationErrors)
    }
    
    /**
     * Check if measurement is physically plausible
     */
    private fun isPhysicallyPlausible(measurement: RangingMeasurement): Boolean {
        return measurement.distance in MIN_DISTANCE..MAX_DISTANCE &&
               measurement.accuracy >= 0f &&
               measurement.accuracy < measurement.distance // Accuracy can't be larger than distance itself
    }
    
    /**
     * Check if accuracy is reasonable for sensor type
     */
    private fun isAccuracyReasonable(measurement: RangingMeasurement): Boolean {
        val maxAccuracy = when (measurement.measurementType) {
            RangingType.WIFI_RTT -> WIFI_RTT_MAX_ACCURACY
            RangingType.BLUETOOTH_CHANNEL_SOUNDING -> BLUETOOTH_MAX_ACCURACY
            RangingType.ACOUSTIC_FMCW -> ACOUSTIC_MAX_ACCURACY
        }
        
        return measurement.accuracy <= maxAccuracy
    }
    
    /**
     * Check temporal consistency with previous measurements
     */
    private fun checkTemporalConsistency(measurement: RangingMeasurement): ConsistencyResult {
        val history = measurementHistory[measurement.sourceId] ?: return ConsistencyResult(true, "No history")
        
        if (history.isEmpty()) {
            return ConsistencyResult(true, "First measurement")
        }
        
        // Check against recent measurements
        val recentMeasurements = history.takeLast(5)
        val recentDistances = recentMeasurements.map { it.distance }
        
        if (recentDistances.size < 2) {
            return ConsistencyResult(true, "Insufficient history")
        }
        
        // Calculate mean and standard deviation
        val mean = recentDistances.average().toFloat()
        val variance = recentDistances.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance).toFloat()
        
        // Check if new measurement is within reasonable range (3-sigma)
        val deviation = abs(measurement.distance - mean)
        val isConsistent = stdDev == 0f || deviation <= CONSISTENCY_THRESHOLD * stdDev
        
        return if (isConsistent) {
            ConsistencyResult(true, "Within ${CONSISTENCY_THRESHOLD}-sigma")
        } else {
            ConsistencyResult(false, "Deviation ${deviation}m exceeds ${CONSISTENCY_THRESHOLD * stdDev}m threshold")
        }
    }
    
    /**
     * Check if rate of change is reasonable
     */
    private fun isRateOfChangeReasonable(measurement: RangingMeasurement): Boolean {
        val history = measurementHistory[measurement.sourceId] ?: return true
        
        if (history.isEmpty()) return true
        
        val lastMeasurement = history.last()
        val timeDelta = (measurement.timestamp - lastMeasurement.timestamp) / 1000.0f // Convert to seconds
        
        if (timeDelta <= 0f) return true // Avoid division by zero
        
        val distanceChange = abs(measurement.distance - lastMeasurement.distance)
        val rateOfChange = distanceChange / timeDelta
        
        // Rate of change should not exceed maximum expected device speed
        return rateOfChange <= MAX_RATE_OF_CHANGE
    }
    
    /**
     * Record measurement for future validation
     */
    private fun recordMeasurement(measurement: RangingMeasurement) {
        val history = measurementHistory.getOrPut(measurement.sourceId) { LinkedList() }
        
        history.add(MeasurementRecord(
            distance = measurement.distance,
            accuracy = measurement.accuracy,
            timestamp = measurement.timestamp
        ))
        
        // Keep only recent history
        while (history.size > HISTORY_SIZE) {
            history.removeFirst()
        }
    }
    
    /**
     * Reset validator state
     */
    fun reset() {
        measurementHistory.clear()
        Log.d(TAG, "Measurement validator reset")
    }
    
    /**
     * Get statistics about validated measurements
     */
    fun getStatistics(): ValidationStatistics {
        val totalSources = measurementHistory.size
        val totalMeasurements = measurementHistory.values.sumOf { it.size }
        
        return ValidationStatistics(
            totalSources = totalSources,
            totalMeasurements = totalMeasurements
        )
    }
}

/**
 * Measurement record for history tracking
 */
private data class MeasurementRecord(
    val distance: Float,
    val accuracy: Float,
    val timestamp: Long
)

/**
 * Validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Consistency check result
 */
private data class ConsistencyResult(
    val isConsistent: Boolean,
    val reason: String
)

/**
 * Validation statistics
 */
data class ValidationStatistics(
    val totalSources: Int,
    val totalMeasurements: Int
)
