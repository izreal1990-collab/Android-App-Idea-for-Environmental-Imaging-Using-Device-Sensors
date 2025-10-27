package com.environmentalimaging.app.slam

import android.util.Log
import com.environmentalimaging.app.data.*
import com.environmentalimaging.app.sensors.MeasurementValidator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * Graph-based SLAM processor implementing Simultaneous Localization and Mapping
 * Combines Extended Kalman Filter with loop closure detection for robust environmental reconstruction
 */
class SLAMProcessor {
    
    private val kalmanFilter = ExtendedKalmanFilter()
    private val loopClosureDetector = LoopClosureDetector()
    private val measurementValidator = MeasurementValidator()
    
    // SLAM state
    private var lastIMUTimestamp = 0L
    private var isProcessing = false
    
    // Processed data flows
    private val _slamState = MutableSharedFlow<SlamState>()
    val slamState: SharedFlow<SlamState> = _slamState.asSharedFlow()
    
    private val _devicePose = MutableSharedFlow<DevicePose>()
    val devicePose: SharedFlow<DevicePose> = _devicePose.asSharedFlow()
    
    // Processing statistics
    private var measurementCount = 0
    private var loopClosureCount = 0
    private var rejectedMeasurementCount = 0
    
    companion object {
        private const val TAG = "SLAMProcessor"
        private const val MIN_DELTA_TIME = 0.01 // Minimum time step (10ms)
        private const val MAX_DELTA_TIME = 1.0  // Maximum time step (1s)
    }
    
    /**
     * Start SLAM processing with sensor data streams
     */
    fun startProcessing(
        imuMeasurements: Flow<IMUMeasurement>,
        rangingMeasurements: Flow<List<RangingMeasurement>>
    ): Job {
        Log.d(TAG, "Starting SLAM processing")
        isProcessing = true
        measurementCount = 0
        loopClosureCount = 0
        
        return CoroutineScope(Dispatchers.Default).launch {
            // Process IMU measurements for prediction steps
            launch {
                imuMeasurements.collect { imuData ->
                    if (isProcessing) {
                        processIMUMeasurement(imuData)
                    }
                }
            }
            
            // Process ranging measurements for correction steps
            launch {
                rangingMeasurements.collect { rangingData ->
                    if (isProcessing) {
                        processRangingMeasurements(rangingData)
                    }
                }
            }
        }
    }
    
    /**
     * Stop SLAM processing
     */
    fun stopProcessing() {
        Log.d(TAG, "Stopping SLAM processing")
        isProcessing = false
        Log.d(TAG, "SLAM statistics - Measurements: $measurementCount, Loop closures: $loopClosureCount, Rejected: $rejectedMeasurementCount")
    }
    
    /**
     * Process IMU measurement for state prediction
     */
    private suspend fun processIMUMeasurement(imuMeasurement: IMUMeasurement) {
        try {
            // Calculate time delta
            val deltaTime = if (lastIMUTimestamp > 0) {
                val dt = (imuMeasurement.timestamp - lastIMUTimestamp) / 1000.0
                dt.coerceIn(MIN_DELTA_TIME, MAX_DELTA_TIME)
            } else {
                MIN_DELTA_TIME
            }
            
            lastIMUTimestamp = imuMeasurement.timestamp
            
            // Perform prediction step
            kalmanFilter.predict(imuMeasurement, deltaTime)
            
            // Emit updated device pose
            val currentPose = kalmanFilter.getCurrentPose()
            _devicePose.emit(currentPose)
            
            // Emit current SLAM state
            emitCurrentSlamState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing IMU measurement", e)
        }
    }
    
    /**
     * Process ranging measurements for state correction
     */
    private suspend fun processRangingMeasurements(measurements: List<RangingMeasurement>) {
        try {
            // Pre-filter measurements through validation layer
            val validMeasurements = measurements.filter { measurement ->
                val validationResult = measurementValidator.validate(measurement)
                if (!validationResult.isValid) {
                    rejectedMeasurementCount++
                    Log.d(TAG, "Rejected measurement from ${measurement.sourceId}: ${validationResult.errors}")
                    false
                } else {
                    true
                }
            }
            
            if (validMeasurements.isEmpty()) {
                Log.d(TAG, "No valid measurements to process")
                return
            }
            
            // Apply adaptive sensor weighting based on accuracy
            val weightedMeasurements = prioritizeMeasurements(validMeasurements)
            
            for (measurement in weightedMeasurements) {
                // Check for loop closure before processing measurement
                val loopClosure = loopClosureDetector.detectLoopClosure(measurement, kalmanFilter.getCurrentPose())
                
                if (loopClosure != null) {
                    Log.d(TAG, "Loop closure detected: ${loopClosure.landmarkId}")
                    handleLoopClosure(loopClosure)
                    loopClosureCount++
                }
                
                // Update Kalman filter with measurement (now has outlier rejection)
                kalmanFilter.update(measurement)
                measurementCount++
                
                Log.d(TAG, "Processed ${measurement.measurementType} measurement: distance=${measurement.distance}m (accuracy=${measurement.accuracy}m)")
            }
            
            // Emit updated pose and state
            val currentPose = kalmanFilter.getCurrentPose()
            _devicePose.emit(currentPose)
            emitCurrentSlamState()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ranging measurements", e)
        }
    }
    
    /**
     * Prioritize measurements based on accuracy and sensor type
     * Process most accurate first: Acoustic > WiFi RTT > Bluetooth RSSI
     */
    private fun prioritizeMeasurements(measurements: List<RangingMeasurement>): List<RangingMeasurement> {
        return measurements.sortedBy { measurement ->
            // Assign priority score (lower = higher priority)
            val typePriority = when (measurement.measurementType) {
                RangingType.ACOUSTIC_FMCW -> 1.0
                RangingType.WIFI_RTT -> 2.0
                RangingType.BLUETOOTH_CHANNEL_SOUNDING -> 3.0
            }
            
            // Combine type priority with actual accuracy
            typePriority * measurement.accuracy
        }
    }
    
    /**
     * Handle detected loop closure
     */
    private fun handleLoopClosure(loopClosure: LoopClosure) {
        try {
            // In a full implementation, this would:
            // 1. Add constraint to pose graph
            // 2. Perform graph optimization
            // 3. Update all poses and landmarks
            
            // For now, we'll just log the detection
            Log.d(TAG, "Handling loop closure: ${loopClosure.landmarkId} with confidence ${loopClosure.confidence}")
            
            // Could trigger pose graph optimization here
            // optimizePoseGraph()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling loop closure", e)
        }
    }
    
    /**
     * Emit current SLAM state
     */
    private suspend fun emitCurrentSlamState() {
        try {
            val pose = kalmanFilter.getCurrentPose()
            val landmarks = kalmanFilter.getLandmarks()
            val covariance = kalmanFilter.getCovariance()
            val confidence = calculateStateConfidence(covariance)
            
            val slamState = SlamState(
                devicePose = pose,
                landmarks = landmarks,
                covariance = covariance,
                confidence = confidence
            )
            
            _slamState.emit(slamState)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error emitting SLAM state", e)
        }
    }
    
    /**
     * Calculate state confidence from covariance matrix
     */
    private fun calculateStateConfidence(covariance: Array<FloatArray>): Float {
        try {
            // Calculate trace of position covariance submatrix
            val positionUncertainty = covariance[0][0] + covariance[1][1] + covariance[2][2]
            
            // Convert uncertainty to confidence (0-1 scale)
            val maxUncertainty = 10.0f // Maximum expected uncertainty
            val confidence = (1.0f - (positionUncertainty / maxUncertainty).coerceIn(0f, 1f))
            
            return confidence.coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating confidence", e)
            return 0.5f // Default confidence
        }
    }
    
    /**
     * Get current processing statistics
     */
    fun getProcessingStatistics(): ProcessingStatistics {
        return ProcessingStatistics(
            measurementCount = measurementCount,
            loopClosureCount = loopClosureCount,
            rejectedMeasurementCount = rejectedMeasurementCount,
            isProcessing = isProcessing,
            landmarkCount = kalmanFilter.getLandmarks().size
        )
    }
    
    /**
     * Reset SLAM state
     */
    fun reset() {
        Log.d(TAG, "Resetting SLAM processor")
        // In a full implementation, this would reinitialize the Kalman filter
        // and clear all landmarks and pose history
        measurementCount = 0
        loopClosureCount = 0
        rejectedMeasurementCount = 0
        lastIMUTimestamp = 0L
        loopClosureDetector.reset()
        measurementValidator.reset()
    }
    
    /**
     * Get current map (landmarks) for visualization
     */
    fun getCurrentMap(): List<Point3D> {
        return kalmanFilter.getLandmarks()
    }
    
    /**
     * Get current device pose
     */
    fun getCurrentPose(): DevicePose {
        return kalmanFilter.getCurrentPose()
    }
}

/**
 * Loop closure detection for SLAM optimization
 */
class LoopClosureDetector {
    
    private val visitedLocations = mutableListOf<VisitedLocation>()
    
    companion object {
        private const val TAG = "LoopClosureDetector"
        private const val LOCATION_THRESHOLD = 2.0f // meters
        private const val LANDMARK_SIMILARITY_THRESHOLD = 0.6f // Increased for robustness
        private const val MIN_CONFIDENCE = 0.75f // Increased threshold
        private const val MIN_LANDMARK_MATCHES = 2 // Require multiple landmark matches
    }
    
    /**
     * Detect if current measurement indicates a loop closure
     */
    fun detectLoopClosure(measurement: RangingMeasurement, currentPose: DevicePose): LoopClosure? {
        try {
            // Check if we've been near this location before
            for (visitedLocation in visitedLocations) {
                val distance = calculateDistance(currentPose.position, visitedLocation.pose.position)
                
                if (distance < LOCATION_THRESHOLD) {
                    // Check landmark similarity with geometric consistency
                    val matchingLandmarks = visitedLocation.landmarks.count { it == measurement.sourceId }
                    
                    if (matchingLandmarks > 0) {
                        // Check for geometric consistency
                        val isGeometricallyConsistent = checkGeometricConsistency(
                            currentPose, 
                            visitedLocation.pose, 
                            measurement
                        )
                        
                        if (isGeometricallyConsistent) {
                            val confidence = calculateLoopClosureConfidence(
                                distance, 
                                measurement.accuracy,
                                matchingLandmarks,
                                visitedLocation.landmarks.size
                            )
                            
                            if (confidence > MIN_CONFIDENCE) {
                                Log.d(TAG, "Strong loop closure: $matchingLandmarks matching landmarks, confidence=$confidence")
                                return LoopClosure(
                                    landmarkId = measurement.sourceId,
                                    historicPose = visitedLocation.pose,
                                    currentPose = currentPose,
                                    confidence = confidence
                                )
                            }
                        } else {
                            Log.d(TAG, "Geometric consistency check failed - rejecting loop closure")
                        }
                    }
                }
            }
            
            // Record current location and landmarks
            recordVisitedLocation(currentPose, measurement.sourceId)
            
            return null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting loop closure", e)
            return null
        }
    }
    
    /**
     * Check geometric consistency between poses and measurement
     */
    private fun checkGeometricConsistency(
        currentPose: DevicePose,
        historicPose: DevicePose,
        measurement: RangingMeasurement
    ): Boolean {
        // Calculate expected distance change based on pose displacement
        val poseDistance = calculateDistance(currentPose.position, historicPose.position)
        
        // If poses are very close, measurements should be similar
        // Allow for sensor noise (use 3-sigma threshold)
        val expectedVariance = 3.0f * measurement.accuracy
        
        return poseDistance <= expectedVariance
    }
    
    /**
     * Record visited location with associated landmarks
     */
    private fun recordVisitedLocation(pose: DevicePose, landmarkId: String) {
        // Find existing location or create new one
        val existingIndex = visitedLocations.indexOfFirst { visitedLocation ->
            calculateDistance(pose.position, visitedLocation.pose.position) < LOCATION_THRESHOLD
        }
        
        if (existingIndex >= 0) {
            // Add landmark to existing location
            val existing = visitedLocations[existingIndex]
            if (!existing.landmarks.contains(landmarkId)) {
                val updatedLandmarks = existing.landmarks.toMutableList()
                updatedLandmarks.add(landmarkId)
                visitedLocations[existingIndex] = VisitedLocation(existing.pose, updatedLandmarks)
            }
        } else {
            // Create new location record
            visitedLocations.add(VisitedLocation(pose, mutableListOf(landmarkId)))
        }
    }
    
    /**
     * Calculate loop closure confidence with landmark similarity
     */
    private fun calculateLoopClosureConfidence(
        distance: Float, 
        measurementAccuracy: Float,
        matchingLandmarks: Int,
        totalLandmarks: Int
    ): Float {
        // Distance-based confidence
        val distanceFactor = (LOCATION_THRESHOLD - distance) / LOCATION_THRESHOLD
        
        // Accuracy-based confidence
        val accuracyFactor = 1.0f / (1.0f + measurementAccuracy)
        
        // Landmark similarity confidence
        val similarityFactor = matchingLandmarks.toFloat() / totalLandmarks.toFloat()
        
        // Combined confidence (weighted average)
        return ((distanceFactor * 0.3f) + (accuracyFactor * 0.3f) + (similarityFactor * 0.4f)).coerceIn(0f, 1f)
    }
    
    /**
     * Calculate distance between two 3D points
     */
    private fun calculateDistance(point1: Point3D, point2: Point3D): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        val dz = point1.z - point2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Reset loop closure detector
     */
    fun reset() {
        visitedLocations.clear()
        Log.d(TAG, "Loop closure detector reset")
    }
}

/**
 * Data class for visited location with landmarks
 */
data class VisitedLocation(
    val pose: DevicePose,
    val landmarks: MutableList<String>
)

/**
 * Data class for loop closure information
 */
data class LoopClosure(
    val landmarkId: String,
    val historicPose: DevicePose,
    val currentPose: DevicePose,
    val confidence: Float
)

/**
 * Processing statistics
 */
data class ProcessingStatistics(
    val measurementCount: Int,
    val loopClosureCount: Int,
    val rejectedMeasurementCount: Int,
    val isProcessing: Boolean,
    val landmarkCount: Int
)