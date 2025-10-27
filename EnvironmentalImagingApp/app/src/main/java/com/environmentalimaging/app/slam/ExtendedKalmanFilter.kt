package com.environmentalimaging.app.slam

import android.util.Log
import com.environmentalimaging.app.data.*
import org.apache.commons.math3.linear.*
import kotlin.math.*

/**
 * Extended Kalman Filter implementation for SLAM state estimation
 * Handles device pose estimation and landmark mapping with uncertainty quantification
 */
class ExtendedKalmanFilter {
    
    companion object {
        private const val TAG = "ExtendedKalmanFilter"
        
        // State vector indices
        private const val POS_X = 0
        private const val POS_Y = 1
        private const val POS_Z = 2
        private const val VEL_X = 3
        private const val VEL_Y = 4
        private const val VEL_Z = 5
        private const val ORI_W = 6  // Quaternion w
        private const val ORI_X = 7  // Quaternion x
        private const val ORI_Y = 8  // Quaternion y
        private const val ORI_Z = 9  // Quaternion z
        
        private const val STATE_SIZE = 10
        private const val MEASUREMENT_SIZE = 1 // Distance measurement
        
        // Process noise parameters
        private const val POSITION_NOISE = 0.1
        private const val VELOCITY_NOISE = 0.5
        private const val ORIENTATION_NOISE = 0.01
        
        // Measurement noise parameters
        private const val WIFI_RTT_NOISE = 1.0
        private const val BLUETOOTH_NOISE = 3.0
        private const val ACOUSTIC_NOISE = 0.05
        
        // Outlier detection parameters
        private const val MAHALANOBIS_THRESHOLD = 9.0 // Chi-squared with 1 DOF at 99.7% (3-sigma)
    }
    
    // State vector [x, y, z, vx, vy, vz, qw, qx, qy, qz]
    private var state: RealVector
    
    // State covariance matrix
    private var covariance: RealMatrix
    
    // Process noise covariance
    private val processNoise: RealMatrix
    
    // Landmarks (detected environmental features)
    private val landmarks = mutableMapOf<String, Point3D>()
    private val landmarkUncertainties = mutableMapOf<String, Double>()
    
    init {
        // Initialize state vector (device starts at origin)
        state = ArrayRealVector(doubleArrayOf(
            0.0, 0.0, 0.0,  // position
            0.0, 0.0, 0.0,  // velocity
            1.0, 0.0, 0.0, 0.0  // quaternion (w, x, y, z)
        ))
        
        // Initialize covariance matrix
        covariance = MatrixUtils.createRealIdentityMatrix(STATE_SIZE).scalarMultiply(1.0)
        
        // Initialize process noise
        processNoise = MatrixUtils.createRealDiagonalMatrix(doubleArrayOf(
            POSITION_NOISE, POSITION_NOISE, POSITION_NOISE,  // position noise
            VELOCITY_NOISE, VELOCITY_NOISE, VELOCITY_NOISE,  // velocity noise
            ORIENTATION_NOISE, ORIENTATION_NOISE, ORIENTATION_NOISE, ORIENTATION_NOISE  // orientation noise
        ))
    }
    
    /**
     * Predict step using IMU measurements
     */
    fun predict(imuMeasurement: IMUMeasurement, deltaTime: Double) {
        try {
            // Extract current state
            val position = doubleArrayOf(state.getEntry(POS_X), state.getEntry(POS_Y), state.getEntry(POS_Z))
            val velocity = doubleArrayOf(state.getEntry(VEL_X), state.getEntry(VEL_Y), state.getEntry(VEL_Z))
            val quaternion = doubleArrayOf(state.getEntry(ORI_W), state.getEntry(ORI_X), state.getEntry(ORI_Y), state.getEntry(ORI_Z))
            
            // Predict position using current velocity
            for (i in 0..2) {
                position[i] += velocity[i] * deltaTime
            }
            
            // Predict velocity using accelerometer (remove gravity and transform to world frame)
            val worldAcceleration = transformToWorldFrame(imuMeasurement.acceleration, quaternion)
            worldAcceleration[2] -= 9.81 // Remove gravity
            
            for (i in 0..2) {
                velocity[i] += worldAcceleration[i] * deltaTime
            }
            
            // Predict orientation using gyroscope
            val angularVelocity = imuMeasurement.angularVelocity
            val quaternionUpdate = integrateAngularVelocity(angularVelocity, deltaTime)
            val newQuaternion = multiplyQuaternions(quaternion, quaternionUpdate)
            normalizeQuaternion(newQuaternion)
            
            // Update state vector
            for (i in 0..2) {
                state.setEntry(POS_X + i, position[i])
                state.setEntry(VEL_X + i, velocity[i])
                state.setEntry(ORI_W + i, newQuaternion[i])
            }
            state.setEntry(ORI_Z, newQuaternion[3])
            
            // Update covariance (simplified - would use Jacobian in full implementation)
            val F = computeStateTransitionJacobian(deltaTime)
            covariance = F.multiply(covariance).multiply(F.transpose()).add(processNoise)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in prediction step", e)
        }
    }
    
    /**
     * Update step using ranging measurements
     */
    fun update(rangingMeasurement: RangingMeasurement) {
        try {
            val landmarkId = rangingMeasurement.sourceId
            
            // Get or create landmark
            val landmark = landmarks[landmarkId] ?: run {
                // New landmark - initialize position estimate
                val estimatedPosition = estimateLandmarkPosition(rangingMeasurement)
                landmarks[landmarkId] = estimatedPosition
                landmarkUncertainties[landmarkId] = 10.0 // High initial uncertainty
                estimatedPosition
            }
            
            // Predicted measurement (distance to landmark)
            val devicePosition = doubleArrayOf(state.getEntry(POS_X), state.getEntry(POS_Y), state.getEntry(POS_Z))
            val predictedDistance = calculateDistance(devicePosition, landmark)
            
            // Innovation (measurement residual)
            val innovation = rangingMeasurement.distance - predictedDistance
            
            // Measurement noise based on ranging type
            val measurementNoise = when (rangingMeasurement.measurementType) {
                RangingType.WIFI_RTT -> WIFI_RTT_NOISE
                RangingType.BLUETOOTH_CHANNEL_SOUNDING -> BLUETOOTH_NOISE
                RangingType.ACOUSTIC_FMCW -> ACOUSTIC_NOISE
            }
            
            // Measurement Jacobian (gradient of measurement function)
            val H = computeMeasurementJacobian(landmark)
            
            // Innovation covariance
            val S = H.multiply(covariance).multiply(H.transpose()).add(
                MatrixUtils.createRealMatrix(1, 1).also { it.setEntry(0, 0, measurementNoise) }
            )
            
            // Mahalanobis distance-based outlier detection
            val mahalanobisDistance = (innovation * innovation) / S.getEntry(0, 0)
            
            if (mahalanobisDistance > MAHALANOBIS_THRESHOLD) {
                Log.w(TAG, "Outlier detected: Mahalanobis distance=$mahalanobisDistance for ${rangingMeasurement.measurementType} (innovation=$innovation)")
                // Reject this measurement - it's statistically inconsistent with current estimate
                return
            }
            
            // Kalman gain
            val K = covariance.multiply(H.transpose()).multiply(MatrixUtils.inverse(S))
            
            // Update state
            val innovationVector = ArrayRealVector(doubleArrayOf(innovation))
            val stateUpdate = K.operate(innovationVector)
            state = state.add(stateUpdate)
            
            // Update covariance
            val I = MatrixUtils.createRealIdentityMatrix(STATE_SIZE)
            covariance = I.subtract(K.multiply(H)).multiply(covariance)
            
            // Normalize quaternion after update
            normalizeQuaternionInState()
            
            Log.d(TAG, "Updated state with ${rangingMeasurement.measurementType} measurement: innovation=$innovation, mahalanobis=$mahalanobisDistance")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in update step", e)
        }
    }
    
    /**
     * Get current device pose
     */
    fun getCurrentPose(): DevicePose {
        val position = Point3D(
            state.getEntry(POS_X).toFloat(),
            state.getEntry(POS_Y).toFloat(),
            state.getEntry(POS_Z).toFloat()
        )
        
        val orientation = floatArrayOf(
            state.getEntry(ORI_W).toFloat(),
            state.getEntry(ORI_X).toFloat(),
            state.getEntry(ORI_Y).toFloat(),
            state.getEntry(ORI_Z).toFloat()
        )
        
        return DevicePose(position, orientation, System.currentTimeMillis())
    }
    
    /**
     * Get current landmarks
     */
    fun getLandmarks(): List<Point3D> {
        return landmarks.values.toList()
    }
    
    /**
     * Get state covariance for uncertainty estimation
     */
    fun getCovariance(): Array<FloatArray> {
        val covArray = Array(STATE_SIZE) { FloatArray(STATE_SIZE) }
        for (i in 0 until STATE_SIZE) {
            for (j in 0 until STATE_SIZE) {
                covArray[i][j] = covariance.getEntry(i, j).toFloat()
            }
        }
        return covArray
    }
    
    /**
     * Transform acceleration from body frame to world frame using quaternion
     */
    private fun transformToWorldFrame(bodyAcceleration: FloatArray, quaternion: DoubleArray): DoubleArray {
        // Convert to double array
        val accel = doubleArrayOf(bodyAcceleration[0].toDouble(), bodyAcceleration[1].toDouble(), bodyAcceleration[2].toDouble())
        
        // Quaternion rotation: v' = q * v * q*
        val accelQuat = doubleArrayOf(0.0, accel[0], accel[1], accel[2])
        val temp = multiplyQuaternions(quaternion, accelQuat)
        val conjugate = doubleArrayOf(quaternion[0], -quaternion[1], -quaternion[2], -quaternion[3])
        val result = multiplyQuaternions(temp, conjugate)
        
        return doubleArrayOf(result[1], result[2], result[3])
    }
    
    /**
     * Integrate angular velocity to get quaternion update
     */
    private fun integrateAngularVelocity(angularVelocity: FloatArray, deltaTime: Double): DoubleArray {
        val omega = doubleArrayOf(angularVelocity[0].toDouble(), angularVelocity[1].toDouble(), angularVelocity[2].toDouble())
        val magnitude = sqrt(omega[0] * omega[0] + omega[1] * omega[1] + omega[2] * omega[2])
        
        if (magnitude < 1e-8) {
            return doubleArrayOf(1.0, 0.0, 0.0, 0.0) // No rotation
        }
        
        val angle = magnitude * deltaTime
        val sinHalfAngle = sin(angle / 2.0)
        val cosHalfAngle = cos(angle / 2.0)
        
        return doubleArrayOf(
            cosHalfAngle,
            (omega[0] / magnitude) * sinHalfAngle,
            (omega[1] / magnitude) * sinHalfAngle,
            (omega[2] / magnitude) * sinHalfAngle
        )
    }
    
    /**
     * Multiply two quaternions
     */
    private fun multiplyQuaternions(q1: DoubleArray, q2: DoubleArray): DoubleArray {
        return doubleArrayOf(
            q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2] - q1[3] * q2[3],
            q1[0] * q2[1] + q1[1] * q2[0] + q1[2] * q2[3] - q1[3] * q2[2],
            q1[0] * q2[2] - q1[1] * q2[3] + q1[2] * q2[0] + q1[3] * q2[1],
            q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1] + q1[3] * q2[0]
        )
    }
    
    /**
     * Normalize quaternion
     */
    private fun normalizeQuaternion(quaternion: DoubleArray) {
        val magnitude = sqrt(quaternion[0] * quaternion[0] + quaternion[1] * quaternion[1] + 
                           quaternion[2] * quaternion[2] + quaternion[3] * quaternion[3])
        if (magnitude > 0) {
            for (i in quaternion.indices) {
                quaternion[i] /= magnitude
            }
        }
    }
    
    /**
     * Normalize quaternion in state vector
     */
    private fun normalizeQuaternionInState() {
        val quaternion = doubleArrayOf(
            state.getEntry(ORI_W), state.getEntry(ORI_X), 
            state.getEntry(ORI_Y), state.getEntry(ORI_Z)
        )
        normalizeQuaternion(quaternion)
        
        state.setEntry(ORI_W, quaternion[0])
        state.setEntry(ORI_X, quaternion[1])
        state.setEntry(ORI_Y, quaternion[2])
        state.setEntry(ORI_Z, quaternion[3])
    }
    
    /**
     * Estimate landmark position from ranging measurement
     */
    private fun estimateLandmarkPosition(measurement: RangingMeasurement): Point3D {
        // Simple estimation: place landmark at measured distance in current direction
        val currentPos = doubleArrayOf(state.getEntry(POS_X), state.getEntry(POS_Y), state.getEntry(POS_Z))
        val quaternion = doubleArrayOf(state.getEntry(ORI_W), state.getEntry(ORI_X), state.getEntry(ORI_Y), state.getEntry(ORI_Z))
        
        // Assume landmark is in front of device (simplified)
        val direction = transformToWorldFrame(floatArrayOf(0f, 0f, 1f), quaternion)
        val distance = measurement.distance.toDouble()
        
        return Point3D(
            (currentPos[0] + direction[0] * distance).toFloat(),
            (currentPos[1] + direction[1] * distance).toFloat(),
            (currentPos[2] + direction[2] * distance).toFloat()
        )
    }
    
    /**
     * Calculate Euclidean distance between two 3D points
     */
    private fun calculateDistance(point1: DoubleArray, point2: Point3D): Double {
        val dx = point1[0] - point2.x
        val dy = point1[1] - point2.y
        val dz = point1[2] - point2.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * Compute state transition Jacobian matrix
     */
    private fun computeStateTransitionJacobian(deltaTime: Double): RealMatrix {
        val F = MatrixUtils.createRealIdentityMatrix(STATE_SIZE)
        
        // Position derivatives with respect to velocity
        for (i in 0..2) {
            F.setEntry(POS_X + i, VEL_X + i, deltaTime)
        }
        
        return F
    }
    
    /**
     * Compute measurement Jacobian matrix
     */
    private fun computeMeasurementJacobian(landmark: Point3D): RealMatrix {
        val H = MatrixUtils.createRealMatrix(MEASUREMENT_SIZE, STATE_SIZE)
        
        val devicePos = doubleArrayOf(state.getEntry(POS_X), state.getEntry(POS_Y), state.getEntry(POS_Z))
        val distance = calculateDistance(devicePos, landmark)
        
        if (distance > 0) {
            // Partial derivatives of distance with respect to position
            H.setEntry(0, POS_X, (devicePos[0] - landmark.x) / distance)
            H.setEntry(0, POS_Y, (devicePos[1] - landmark.y) / distance)
            H.setEntry(0, POS_Z, (devicePos[2] - landmark.z) / distance)
        }
        
        return H
    }
}