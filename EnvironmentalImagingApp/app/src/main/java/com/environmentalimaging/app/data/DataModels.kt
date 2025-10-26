package com.environmentalimaging.app.data

/**
 * Data classes for sensor measurements and environmental reconstruction
 */

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class DevicePose(
    val position: Point3D,
    val orientation: FloatArray, // Quaternion [w, x, y, z]
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DevicePose

        if (position != other.position) return false
        if (!orientation.contentEquals(other.orientation)) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = position.hashCode()
        result = 31 * result + orientation.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

data class RangingMeasurement(
    val sourceId: String,
    val distance: Float,
    val accuracy: Float,
    val timestamp: Long,
    val measurementType: RangingType
)

enum class RangingType {
    WIFI_RTT,
    BLUETOOTH_CHANNEL_SOUNDING,
    ACOUSTIC_FMCW
}

data class IMUMeasurement(
    val acceleration: FloatArray, // [x, y, z] in m/s²
    val angularVelocity: FloatArray, // [x, y, z] in rad/s
    val magneticField: FloatArray?, // [x, y, z] in μT (optional)
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IMUMeasurement

        if (!acceleration.contentEquals(other.acceleration)) return false
        if (!angularVelocity.contentEquals(other.angularVelocity)) return false
        if (magneticField != null) {
            if (other.magneticField == null) return false
            if (!magneticField.contentEquals(other.magneticField)) return false
        } else if (other.magneticField != null) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = acceleration.contentHashCode()
        result = 31 * result + angularVelocity.contentHashCode()
        result = 31 * result + (magneticField?.contentHashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

data class EnvironmentalSnapshot(
    val id: String,
    val timestamp: Long,
    val devicePose: DevicePose,
    val pointCloud: List<Point3D>,
    val rangingMeasurements: List<RangingMeasurement>,
    val imuData: List<IMUMeasurement>,
    val metadata: Map<String, Any>
)

data class SlamState(
    val devicePose: DevicePose,
    val landmarks: List<Point3D>,
    val covariance: Array<FloatArray>, // State covariance matrix
    val confidence: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SlamState

        if (devicePose != other.devicePose) return false
        if (landmarks != other.landmarks) return false
        if (!covariance.contentDeepEquals(other.covariance)) return false
        if (confidence != other.confidence) return false

        return true
    }

    override fun hashCode(): Int {
        var result = devicePose.hashCode()
        result = 31 * result + landmarks.hashCode()
        result = 31 * result + covariance.contentDeepHashCode()
        result = 31 * result + confidence.hashCode()
        return result
    }
}