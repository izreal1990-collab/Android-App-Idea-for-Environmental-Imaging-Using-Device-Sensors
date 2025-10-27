package com.environmentalimaging.app.ai

import com.environmentalimaging.app.data.Point3D
import com.environmentalimaging.app.data.RangingMeasurement

/**
 * Data models for AI analysis results and insights
 */

data class SensorInsight(
    val type: InsightType,
    val message: String,
    val confidence: Float,
    val severity: SeverityLevel,
    val timestamp: Long,
    val data: Map<String, Any> = emptyMap()
)

enum class InsightType {
    ANOMALY_DETECTED,
    LOW_ACCURACY,
    HIGH_ACCURACY,
    MOVEMENT_PATTERN,
    SENSOR_DRIFT,
    CALIBRATION_NEEDED,
    OPTIMAL_CONDITIONS,
    ENVIRONMENTAL_CHANGE
}

enum class SeverityLevel {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

data class SystemHealthReport(
    val timestamp: Long,
    val overallHealth: Float, // 0.0 to 1.0
    val issues: List<String>,
    val recommendations: List<String>,
    val performanceMetrics: Map<String, Float>
)

data class EnvironmentalAnalysis(
    val mappingQuality: MappingQuality,
    val landmarkCount: Int,
    val positionStability: Float,
    val recommendations: List<String>,
    val timestamp: Long
) {
    companion object {
        fun default() = EnvironmentalAnalysis(
            mappingQuality = MappingQuality.UNKNOWN,
            landmarkCount = 0,
            positionStability = 0f,
            recommendations = listOf("Insufficient data for analysis"),
            timestamp = System.currentTimeMillis()
        )
    }
}

enum class MappingQuality {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    UNKNOWN
}

data class EnvironmentalInsights(
    val roomDimensions: RoomDimensions,
    val detectedObjects: List<DetectedObject>,
    val spatialAnalysis: SpatialAnalysis,
    val recommendations: List<String>,
    val confidence: Float,
    val timestamp: Long
) {
    companion object {
        fun empty() = EnvironmentalInsights(
            roomDimensions = RoomDimensions.empty(),
            detectedObjects = emptyList(),
            spatialAnalysis = SpatialAnalysis.empty(),
            recommendations = listOf("Insufficient data for analysis"),
            confidence = 0f,
            timestamp = System.currentTimeMillis()
        )
    }
}

data class RoomDimensions(
    val width: Float,
    val height: Float,
    val depth: Float,
    val volume: Float
) {
    companion object {
        fun empty() = RoomDimensions(0f, 0f, 0f, 0f)
    }
    
    fun getDescription(): String {
        return "Room: ${width.format(1)}m × ${height.format(1)}m × ${depth.format(1)}m (${volume.format(1)}m³)"
    }
    
    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
}

data class DetectedObject(
    val type: ObjectType,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val pointCount: Int
)

enum class ObjectType {
    WALL,
    FLOOR,
    CEILING,
    TABLE,
    CHAIR,
    DOOR,
    WINDOW,
    FURNITURE,
    UNKNOWN
}

data class BoundingBox(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val depth: Float get() = maxZ - minZ
    val volume: Float get() = width * height * depth
    val center: Point3D get() = Point3D((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)
}

data class SpatialAnalysis(
    val pointDensity: Float,
    val spatialCoverage: Float,
    val uniformity: Float,
    val totalPoints: Int
) {
    companion object {
        fun empty() = SpatialAnalysis(0f, 0f, 0f, 0)
    }
    
    fun getQualityDescription(): String {
        val overall = (pointDensity + spatialCoverage + uniformity) / 3f
        return when {
            overall > 0.8f -> "Excellent spatial coverage"
            overall > 0.6f -> "Good spatial coverage"
            overall > 0.4f -> "Fair spatial coverage"
            else -> "Poor spatial coverage"
        }
    }
}

data class LogAnalysisResult(
    val healthScore: Float,
    val detectedIssues: List<String>,
    val recommendations: List<String>,
    val performanceMetrics: Map<String, Float>
)

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR
}

data class IMUData(
    val timestamp: Long,
    val acceleration: FloatArray,
    val gyroscope: FloatArray,
    val magnetometer: FloatArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IMUData

        if (timestamp != other.timestamp) return false
        if (!acceleration.contentEquals(other.acceleration)) return false
        if (!gyroscope.contentEquals(other.gyroscope)) return false
        if (magnetometer != null) {
            if (other.magnetometer == null) return false
            if (!magnetometer.contentEquals(other.magnetometer)) return false
        } else if (other.magnetometer != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + acceleration.contentHashCode()
        result = 31 * result + gyroscope.contentHashCode()
        result = 31 * result + (magnetometer?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * AI Assistant conversation data models
 */
data class AIConversation(
    val id: String,
    val messages: List<AIMessage>,
    val context: ConversationContext,
    val timestamp: Long
)

data class AIMessage(
    val id: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val attachments: List<MessageAttachment> = emptyList()
)

data class MessageAttachment(
    val type: AttachmentType,
    val data: Any,
    val description: String
)

enum class AttachmentType {
    SENSOR_DATA,
    POINT_CLOUD,
    SLAM_STATE,
    SYSTEM_LOGS,
    SCREENSHOT
}

data class ConversationContext(
    val currentSensorData: List<RangingMeasurement>,
    val currentSLAMState: Any?, // SLAMState
    val currentEnvironment: List<Point3D>,
    val recentInsights: List<SensorInsight>,
    val systemHealth: SystemHealthReport?
)

/**
 * ML Model data structures
 */
data class ModelPrediction(
    val prediction: FloatArray,
    val confidence: Float,
    val modelVersion: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelPrediction

        if (!prediction.contentEquals(other.prediction)) return false
        if (confidence != other.confidence) return false
        if (modelVersion != other.modelVersion) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = prediction.contentHashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

data class TrainingData(
    val inputs: FloatArray,
    val outputs: FloatArray,
    val label: String,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TrainingData

        if (!inputs.contentEquals(other.inputs)) return false
        if (!outputs.contentEquals(other.outputs)) return false
        if (label != other.label) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = inputs.contentHashCode()
        result = 31 * result + outputs.contentHashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * Performance monitoring data
 */
data class PerformanceMetrics(
    val cpuUsage: Float,
    val memoryUsage: Float,
    val batteryLevel: Float,
    val networkLatency: Float,
    val framerate: Float,
    val timestamp: Long
)

data class AIPerformanceReport(
    val modelInferenceTime: Long,
    val processingLatency: Long,
    val accuracyScore: Float,
    val resourceUsage: PerformanceMetrics,
    val timestamp: Long
)

// Unified SystemIssue data class for both log analysis and system health monitoring
data class SystemIssue(
    val type: String, // Can be IssueType enum name or custom string
    val description: String,
    val severity: SeverityLevel,
    val timestamp: Long,
    val recommendations: List<String>,
    val value: Float? = null, // Optional numeric value for system metrics
    val metadata: Map<String, Any> = emptyMap() // Additional context data
)

// Additional enums for system monitoring
enum class IssueType {
    HIGH_CPU_USAGE, HIGH_MEMORY_USAGE, LOW_BATTERY, HIGH_TEMPERATURE,
    MEMORY_EXHAUSTION, CPU_OVERLOAD, BATTERY_DEPLETION, THERMAL_THROTTLING
}

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class SuggestionPriority {
    LOW, MEDIUM, HIGH
}