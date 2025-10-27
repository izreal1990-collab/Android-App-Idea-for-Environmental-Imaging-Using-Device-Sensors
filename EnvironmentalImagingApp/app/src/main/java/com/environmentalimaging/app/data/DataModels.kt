package com.environmentalimaging.app.data

/**
 * Data classes for sensor measurements and environmental reconstruction
 */

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeFloat(x)
        parcel.writeFloat(y)
        parcel.writeFloat(z)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<Point3D> {
        override fun createFromParcel(parcel: android.os.Parcel): Point3D {
            return Point3D(parcel)
        }

        override fun newArray(size: Int): Array<Point3D?> {
            return arrayOfNulls(size)
        }
    }
}

data class DevicePose(
    val position: Point3D,
    val orientation: FloatArray, // Quaternion [w, x, y, z]
    val timestamp: Long
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readParcelable(Point3D::class.java.classLoader) ?: Point3D(0f, 0f, 0f),
        parcel.createFloatArray() ?: floatArrayOf(1f, 0f, 0f, 0f),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeParcelable(position, flags)
        parcel.writeFloatArray(orientation)
        parcel.writeLong(timestamp)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<DevicePose> {
        override fun createFromParcel(parcel: android.os.Parcel): DevicePose {
            return DevicePose(parcel)
        }

        override fun newArray(size: Int): Array<DevicePose?> {
            return arrayOfNulls(size)
        }
    }

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
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readString() ?: "",
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readLong(),
        RangingType.valueOf(parcel.readString() ?: "WIFI_RTT")
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(sourceId)
        parcel.writeFloat(distance)
        parcel.writeFloat(accuracy)
        parcel.writeLong(timestamp)
        parcel.writeString(measurementType.name)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<RangingMeasurement> {
        override fun createFromParcel(parcel: android.os.Parcel): RangingMeasurement {
            return RangingMeasurement(parcel)
        }

        override fun newArray(size: Int): Array<RangingMeasurement?> {
            return arrayOfNulls(size)
        }
    }
}

enum class RangingType : android.os.Parcelable {
    WIFI_RTT,
    BLUETOOTH_CHANNEL_SOUNDING,
    ACOUSTIC_FMCW;

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(name)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR = object : android.os.Parcelable.Creator<RangingType> {
            override fun createFromParcel(parcel: android.os.Parcel): RangingType {
                return valueOf(parcel.readString() ?: "WIFI_RTT")
            }

            override fun newArray(size: Int): Array<RangingType?> {
                return arrayOfNulls(size)
            }
        }
    }
}

data class IMUMeasurement(
    val acceleration: FloatArray, // [x, y, z] in m/s²
    val angularVelocity: FloatArray, // [x, y, z] in rad/s
    val magneticField: FloatArray?, // [x, y, z] in μT (optional)
    val timestamp: Long
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.createFloatArray() ?: floatArrayOf(0f, 0f, 0f),
        parcel.createFloatArray() ?: floatArrayOf(0f, 0f, 0f),
        parcel.createFloatArray(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeFloatArray(acceleration)
        parcel.writeFloatArray(angularVelocity)
        parcel.writeFloatArray(magneticField)
        parcel.writeLong(timestamp)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<IMUMeasurement> {
        override fun createFromParcel(parcel: android.os.Parcel): IMUMeasurement {
            return IMUMeasurement(parcel)
        }

        override fun newArray(size: Int): Array<IMUMeasurement?> {
            return arrayOfNulls(size)
        }
    }

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
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readParcelable(DevicePose::class.java.classLoader) ?: DevicePose(Point3D(0f, 0f, 0f), floatArrayOf(1f, 0f, 0f, 0f), 0L),
        parcel.createTypedArrayList(Point3D.CREATOR) ?: emptyList(),
        parcel.createTypedArrayList(RangingMeasurement.CREATOR) ?: emptyList(),
        parcel.createTypedArrayList(IMUMeasurement.CREATOR) ?: emptyList(),
        emptyMap() // Simplified for parcelable
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeLong(timestamp)
        parcel.writeParcelable(devicePose, flags)
        parcel.writeTypedList(pointCloud)
        parcel.writeTypedList(rangingMeasurements)
        parcel.writeTypedList(imuData)
        // Skip metadata for simplicity
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<EnvironmentalSnapshot> {
        override fun createFromParcel(parcel: android.os.Parcel): EnvironmentalSnapshot {
            return EnvironmentalSnapshot(parcel)
        }

        override fun newArray(size: Int): Array<EnvironmentalSnapshot?> {
            return arrayOfNulls(size)
        }
    }
}

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

data class ScanSession(
    val id: String,
    val startTime: Long,
    val endTime: Long,
    val dataPoints: List<Point3D>,
    val landmarks: List<Point3D>,
    val trajectory: List<DevicePose>,
    val snapshots: List<EnvironmentalSnapshot>,
    val metadata: Map<String, Any>
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readLong(),
        parcel.createTypedArrayList(Point3D.CREATOR) ?: emptyList(),
        parcel.createTypedArrayList(Point3D.CREATOR) ?: emptyList(),
        parcel.createTypedArrayList(DevicePose.CREATOR) ?: emptyList(),
        parcel.createTypedArrayList(EnvironmentalSnapshot.CREATOR) ?: emptyList(),
        (parcel.readHashMap(Any::class.java.classLoader) as? Map<String, Any>) ?: emptyMap()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeLong(startTime)
        parcel.writeLong(endTime)
        parcel.writeTypedList(dataPoints)
        parcel.writeTypedList(landmarks)
        parcel.writeTypedList(trajectory)
        parcel.writeTypedList(snapshots)
        parcel.writeMap(metadata)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : android.os.Parcelable.Creator<ScanSession> {
        override fun createFromParcel(parcel: android.os.Parcel): ScanSession {
            return ScanSession(parcel)
        }

        override fun newArray(size: Int): Array<ScanSession?> {
            return arrayOfNulls(size)
        }
    }
}