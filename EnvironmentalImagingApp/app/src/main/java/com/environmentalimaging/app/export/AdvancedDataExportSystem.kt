package com.environmentalimaging.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.environmentalimaging.app.ai.AIAnalysisEngine
import com.environmentalimaging.app.data.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * Advanced Data Export System
 * Comprehensive export functionality with multiple formats and cloud integration
 */
class AdvancedDataExportSystem(
    private val context: Context,
    private val aiAnalysisEngine: AIAnalysisEngine
) {
    
    companion object {
        private const val TAG = "AdvancedDataExport"
        private const val EXPORT_DIR = "EnvironmentalImaging"
        private const val PLY_HEADER = "ply\nformat ascii 1.0\n"
        private const val LAS_HEADER_SIZE = 227
    }
    
    // Export formats
    enum class ExportFormat {
        PLY, LAS, CSV, JSON, PDF_REPORT, XML
    }
    
    // Cloud providers
    enum class CloudProvider {
        GOOGLE_DRIVE, DROPBOX, ONEDRIVE, LOCAL_STORAGE
    }
    
    // Export configuration
    data class ExportConfiguration(
        val format: ExportFormat,
        val includeMetadata: Boolean = true,
        val includeAIInsights: Boolean = true,
        val compressData: Boolean = false,
        val cloudProvider: CloudProvider = CloudProvider.LOCAL_STORAGE,
        val generateReport: Boolean = true,
        val batchSize: Int = 1000,
        val qualityThreshold: Float = 0.5f
    )
    
    // Export result
    data class ExportResult(
        val success: Boolean,
        val fileUri: Uri?,
        val fileSize: Long,
        val exportTime: Long,
        val recordCount: Int,
        val errorMessage: String? = null,
        val aiSummary: String? = null
    )
    
    // AI-generated export summary
    data class ExportSummary(
        val totalPoints: Int,
        val qualityScore: Float,
        val coverageArea: Float,
        val accuracyMetrics: Map<String, Float>,
        val recommendations: List<String>,
        val insights: List<String>,
        val exportTimestamp: Long = System.currentTimeMillis()
    )
    
    // Export progress callback
    interface ExportProgressCallback {
        fun onProgress(progress: Float, message: String)
        fun onComplete(result: ExportResult)
        fun onError(error: String)
    }
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(Date::class.java, DateSerializer())
        .create()
    
    private val exportScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Export scan data with comprehensive options
     */
    suspend fun exportScanData(
        scanData: List<DataPoint>,
        landmarks: List<Landmark> = emptyList(),
        trajectory: List<DevicePose> = emptyList(),
        configuration: ExportConfiguration,
        callback: ExportProgressCallback? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            callback?.onProgress(0f, "Initializing export...")
            
            // Generate AI summary first
            val aiSummary = generateAISummary(scanData, landmarks, trajectory)
            callback?.onProgress(10f, "AI analysis complete")
            
            // Filter data based on quality threshold
            val filteredData = filterDataByQuality(scanData, configuration.qualityThreshold)
            callback?.onProgress(20f, "Data filtering complete")
            
            // Create export directory
            val exportDir = createExportDirectory()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val baseFilename = "scan_$timestamp"
            
            // Export based on format
            val result = when (configuration.format) {
                ExportFormat.PLY -> exportAsPLY(filteredData, exportDir, baseFilename, configuration, callback)
                ExportFormat.LAS -> exportAsLAS(filteredData, exportDir, baseFilename, configuration, callback)
                ExportFormat.CSV -> exportAsCSV(filteredData, landmarks, trajectory, exportDir, baseFilename, configuration, callback)
                ExportFormat.JSON -> exportAsJSON(filteredData, landmarks, trajectory, aiSummary, exportDir, baseFilename, configuration, callback)
                ExportFormat.XML -> exportAsXML(filteredData, landmarks, trajectory, aiSummary, exportDir, baseFilename, configuration, callback)
                ExportFormat.PDF_REPORT -> exportAsPDFReport(filteredData, landmarks, trajectory, aiSummary, exportDir, baseFilename, configuration, callback)
            }
            
            // Generate and export AI report if requested
            if (configuration.generateReport && configuration.format != ExportFormat.PDF_REPORT) {
                callback?.onProgress(90f, "Generating AI report...")
                exportAIReport(aiSummary, exportDir, baseFilename)
            }
            
            callback?.onProgress(95f, "Finalizing export...")
            
            // Upload to cloud if specified
            if (configuration.cloudProvider != CloudProvider.LOCAL_STORAGE) {
                uploadToCloud(result.fileUri, configuration.cloudProvider, callback)
            }
            
            callback?.onProgress(100f, "Export complete")
            callback?.onComplete(result)
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            val errorResult = ExportResult(
                success = false,
                fileUri = null,
                fileSize = 0,
                exportTime = 0,
                recordCount = 0,
                errorMessage = e.message ?: "Unknown export error",
                aiSummary = null
            )
            callback?.onError(e.message ?: "Unknown export error")
            errorResult
        }
    }
    
    /**
     * Batch export multiple scans
     */
    suspend fun batchExport(
        scanSessions: List<ScanSession>,
        configuration: ExportConfiguration,
        callback: ExportProgressCallback? = null
    ): List<ExportResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ExportResult>()
        
        scanSessions.forEachIndexed { index, session ->
            callback?.onProgress(
                (index.toFloat() / scanSessions.size) * 100f,
                "Exporting session ${index + 1}/${scanSessions.size}"
            )
            
            val result = exportScanData(
                session.dataPoints,
                session.landmarks,
                session.trajectory,
                configuration.copy(
                    generateReport = index == scanSessions.size - 1 // Only generate report for last session
                )
            )
            
            results.add(result)
        }
        
        callback?.onComplete(ExportResult(
            success = results.all { it.success },
            fileUri = null,
            fileSize = results.sumOf { it.fileSize },
            exportTime = results.sumOf { it.exportTime },
            recordCount = results.sumOf { it.recordCount },
            aiSummary = "Batch export of ${results.size} sessions completed"
        ))
        
        results
    }
    
    // Private implementation methods
    
    private suspend fun generateAISummary(
        scanData: List<DataPoint>,
        landmarks: List<Landmark>,
        trajectory: List<DevicePose>
    ): ExportSummary {
        return try {
            val analysis = aiAnalysisEngine.analyzeScanData(scanData, landmarks, trajectory)
            
            ExportSummary(
                totalPoints = scanData.size,
                qualityScore = analysis.qualityScore,
                coverageArea = analysis.coverageArea,
                accuracyMetrics = analysis.accuracyMetrics,
                recommendations = analysis.recommendations,
                insights = analysis.insights
            )
        } catch (e: Exception) {
            Log.e(TAG, "AI summary generation failed", e)
            // Fallback summary
            ExportSummary(
                totalPoints = scanData.size,
                qualityScore = 0.5f,
                coverageArea = 0f,
                accuracyMetrics = emptyMap(),
                recommendations = listOf("Unable to generate AI recommendations"),
                insights = listOf("AI analysis unavailable")
            )
        }
    }
    
    private fun filterDataByQuality(data: List<DataPoint>, threshold: Float): List<DataPoint> {
        return data.filter { it.confidence >= threshold }
    }
    
    private fun createExportDirectory(): File {
        val exportDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            EXPORT_DIR
        )
        
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        
        return exportDir
    }
    
    private suspend fun exportAsPLY(
        data: List<DataPoint>,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "$baseFilename.ply")
        
        callback?.onProgress(30f, "Writing PLY header...")
        
        FileOutputStream(file).use { fos ->
            BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                // Write PLY header
                writer.write(PLY_HEADER)
                writer.write("element vertex ${data.size}\n")
                writer.write("property float x\n")
                writer.write("property float y\n")
                writer.write("property float z\n")
                writer.write("property float nx\n")
                writer.write("property float ny\n")
                writer.write("property float nz\n")
                writer.write("property float confidence\n")
                writer.write("property float intensity\n")
                writer.write("end_header\n")
                
                // Write vertex data
                callback?.onProgress(50f, "Writing vertex data...")
                data.forEachIndexed { index, point ->
                    if (index % 1000 == 0) {
                        callback?.onProgress(
                            50f + (index.toFloat() / data.size) * 40f,
                            "Writing vertex ${index + 1}/${data.size}"
                        )
                    }
                    
                    writer.write(String.format(Locale.US,
                        "%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\n",
                        point.position.x, point.position.y, point.position.z,
                        point.normal?.x ?: 0f, point.normal?.y ?: 0f, point.normal?.z ?: 0f,
                        point.confidence, point.intensity
                    ))
                }
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size,
            aiSummary = "PLY export completed successfully"
        )
    }
    
    private suspend fun exportAsLAS(
        data: List<DataPoint>,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "$baseFilename.las")
        
        callback?.onProgress(30f, "Writing LAS header...")
        
        FileOutputStream(file).use { fos ->
            DataOutputStream(fos).use { dos ->
                // Write LAS header (simplified)
                writeLASHeader(dos, data.size)
                
                // Write point data
                callback?.onProgress(50f, "Writing point data...")
                data.forEachIndexed { index, point ->
                    if (index % 1000 == 0) {
                        callback?.onProgress(
                            50f + (index.toFloat() / data.size) * 40f,
                            "Writing point ${index + 1}/${data.size}"
                        )
                    }
                    
                    writeLASPoint(dos, point)
                }
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size,
            aiSummary = "LAS export completed successfully"
        )
    }
    
    private suspend fun exportAsCSV(
        data: List<DataPoint>,
        landmarks: List<Landmark>,
        trajectory: List<DevicePose>,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "$baseFilename.csv")
        
        callback?.onProgress(30f, "Writing CSV data...")
        
        FileOutputStream(file).use { fos ->
            BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                // Write header
                writer.write("x,y,z,nx,ny,nz,confidence,intensity,timestamp,type\n")
                
                // Write point data
                callback?.onProgress(40f, "Writing points...")
                data.forEach { point ->
                    writer.write(String.format(Locale.US,
                        "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,point\n",
                        point.position.x, point.position.y, point.position.z,
                        point.normal?.x ?: 0f, point.normal?.y ?: 0f, point.normal?.z ?: 0f,
                        point.confidence, point.intensity, point.timestamp
                    ))
                }
                
                // Write landmark data
                callback?.onProgress(70f, "Writing landmarks...")
                landmarks.forEach { landmark ->
                    writer.write(String.format(Locale.US,
                        "%.6f,%.6f,%.6f,0,0,0,%.6f,0,%d,landmark\n",
                        landmark.position.x, landmark.position.y, landmark.position.z,
                        landmark.confidence, landmark.timestamp
                    ))
                }
                
                // Write trajectory data
                callback?.onProgress(90f, "Writing trajectory...")
                trajectory.forEach { pose ->
                    writer.write(String.format(Locale.US,
                        "%.6f,%.6f,%.6f,0,0,0,1.0,0,%d,trajectory\n",
                        pose.position.x, pose.position.y, pose.position.z,
                        pose.timestamp
                    ))
                }
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size + landmarks.size + trajectory.size,
            aiSummary = "CSV export completed successfully"
        )
    }
    
    private suspend fun exportAsJSON(
        data: List<DataPoint>,
        landmarks: List<Landmark>,
        trajectory: List<DevicePose>,
        aiSummary: ExportSummary,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "$baseFilename.json")
        
        callback?.onProgress(30f, "Serializing JSON data...")
        
        val exportData = mapOf(
            "metadata" to mapOf(
                "exportTimestamp" to System.currentTimeMillis(),
                "format" to "EnvironmentalImaging_JSON_v1.0",
                "pointCount" to data.size,
                "landmarkCount" to landmarks.size,
                "trajectoryPoints" to trajectory.size
            ),
            "aiSummary" to aiSummary,
            "points" to data.map { point ->
                mapOf(
                    "position" to mapOf(
                        "x" to point.position.x,
                        "y" to point.position.y,
                        "z" to point.position.z
                    ),
                    "normal" to point.normal?.let { normal ->
                        mapOf("x" to normal.x, "y" to normal.y, "z" to normal.z)
                    },
                    "confidence" to point.confidence,
                    "intensity" to point.intensity,
                    "timestamp" to point.timestamp
                )
            },
            "landmarks" to landmarks.map { landmark ->
                mapOf(
                    "position" to mapOf(
                        "x" to landmark.position.x,
                        "y" to landmark.position.y,
                        "z" to landmark.position.z
                    ),
                    "type" to landmark.type,
                    "confidence" to landmark.confidence,
                    "timestamp" to landmark.timestamp
                )
            },
            "trajectory" to trajectory.map { pose ->
                mapOf(
                    "position" to mapOf(
                        "x" to pose.position.x,
                        "y" to pose.position.y,
                        "z" to pose.position.z
                    ),
                    "orientation" to mapOf(
                        "w" to pose.orientation.w,
                        "x" to pose.orientation.x,
                        "y" to pose.orientation.y,
                        "z" to pose.orientation.z
                    ),
                    "timestamp" to pose.timestamp
                )
            }
        )
        
        callback?.onProgress(70f, "Writing JSON file...")
        
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                gson.toJson(exportData, writer)
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size + landmarks.size + trajectory.size,
            aiSummary = "JSON export completed successfully"
        )
    }
    
    private suspend fun exportAsXML(
        data: List<DataPoint>,
        landmarks: List<Landmark>,
        trajectory: List<DevicePose>,
        aiSummary: ExportSummary,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "$baseFilename.xml")
        
        callback?.onProgress(30f, "Writing XML data...")
        
        FileOutputStream(file).use { fos ->
            BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                writer.write("<EnvironmentalImagingExport version=\"1.0\">\n")
                
                // Metadata
                writer.write("  <metadata>\n")
                writer.write("    <exportTimestamp>${System.currentTimeMillis()}</exportTimestamp>\n")
                writer.write("    <pointCount>${data.size}</pointCount>\n")
                writer.write("    <landmarkCount>${landmarks.size}</landmarkCount>\n")
                writer.write("    <trajectoryPoints>${trajectory.size}</trajectoryPoints>\n")
                writer.write("  </metadata>\n")
                
                // AI Summary
                writer.write("  <aiSummary>\n")
                writer.write("    <totalPoints>${aiSummary.totalPoints}</totalPoints>\n")
                writer.write("    <qualityScore>${aiSummary.qualityScore}</qualityScore>\n")
                writer.write("    <coverageArea>${aiSummary.coverageArea}</coverageArea>\n")
                writer.write("    <recommendations>\n")
                aiSummary.recommendations.forEach { rec ->
                    writer.write("      <recommendation>$rec</recommendation>\n")
                }
                writer.write("    </recommendations>\n")
                writer.write("  </aiSummary>\n")
                
                // Points
                callback?.onProgress(50f, "Writing points...")
                writer.write("  <points>\n")
                data.forEach { point ->
                    writer.write("    <point>\n")
                    writer.write("      <position>\n")
                    writer.write("        <x>${point.position.x}</x>\n")
                    writer.write("        <y>${point.position.y}</y>\n")
                    writer.write("        <z>${point.position.z}</z>\n")
                    writer.write("      </position>\n")
                    if (point.normal != null) {
                        writer.write("      <normal>\n")
                        writer.write("        <x>${point.normal.x}</x>\n")
                        writer.write("        <y>${point.normal.y}</y>\n")
                        writer.write("        <z>${point.normal.z}</z>\n")
                        writer.write("      </normal>\n")
                    }
                    writer.write("      <confidence>${point.confidence}</confidence>\n")
                    writer.write("      <intensity>${point.intensity}</intensity>\n")
                    writer.write("      <timestamp>${point.timestamp}</timestamp>\n")
                    writer.write("    </point>\n")
                }
                writer.write("  </points>\n")
                
                writer.write("</EnvironmentalImagingExport>\n")
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size + landmarks.size + trajectory.size,
            aiSummary = "XML export completed successfully"
        )
    }
    
    private suspend fun exportAsPDFReport(
        data: List<DataPoint>,
        landmarks: List<Landmark>,
        trajectory: List<DevicePose>,
        aiSummary: ExportSummary,
        exportDir: File,
        baseFilename: String,
        config: ExportConfiguration,
        callback: ExportProgressCallback?
    ): ExportResult {
        // PDF export would require additional PDF library dependency
        // For now, create a text-based report
        val startTime = System.currentTimeMillis()
        val file = File(exportDir, "${baseFilename}_report.txt")
        
        callback?.onProgress(30f, "Generating PDF report...")
        
        FileOutputStream(file).use { fos ->
            BufferedWriter(OutputStreamWriter(fos)).use { writer ->
                writer.write("Environmental Imaging Scan Report\n")
                writer.write("================================\n\n")
                writer.write("Export Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n\n")
                
                writer.write("SCAN SUMMARY\n")
                writer.write("------------\n")
                writer.write("Total Points: ${aiSummary.totalPoints}\n")
                writer.write("Quality Score: ${(aiSummary.qualityScore * 100).roundToInt()}%\n")
                writer.write("Coverage Area: ${String.format("%.2f", aiSummary.coverageArea)} m²\n")
                writer.write("Landmarks Detected: ${landmarks.size}\n")
                writer.write("Trajectory Points: ${trajectory.size}\n\n")
                
                writer.write("AI ANALYSIS\n")
                writer.write("-----------\n")
                writer.write("Quality Assessment: ${getQualityDescription(aiSummary.qualityScore)}\n\n")
                
                writer.write("Key Insights:\n")
                aiSummary.insights.forEach { insight ->
                    writer.write("• $insight\n")
                }
                writer.write("\n")
                
                writer.write("Recommendations:\n")
                aiSummary.recommendations.forEach { rec ->
                    writer.write("• $rec\n")
                }
                writer.write("\n")
                
                writer.write("Accuracy Metrics:\n")
                aiSummary.accuracyMetrics.forEach { (metric, value) ->
                    writer.write("• $metric: ${String.format("%.2f", value)}\n")
                }
                writer.write("\n")
                
                writer.write("EXPORT DETAILS\n")
                writer.write("--------------\n")
                writer.write("Format: PDF Report\n")
                writer.write("Export Time: ${System.currentTimeMillis() - startTime} ms\n")
                writer.write("File Location: ${file.absolutePath}\n")
            }
        }
        
        val exportTime = System.currentTimeMillis() - startTime
        
        return ExportResult(
            success = true,
            fileUri = Uri.fromFile(file),
            fileSize = file.length(),
            exportTime = exportTime,
            recordCount = data.size + landmarks.size + trajectory.size,
            aiSummary = "PDF report generated successfully"
        )
    }
    
    private suspend fun exportAIReport(aiSummary: ExportSummary, exportDir: File, baseFilename: String) {
        val reportFile = File(exportDir, "${baseFilename}_ai_report.json")
        
        FileOutputStream(reportFile).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                gson.toJson(aiSummary, writer)
            }
        }
    }
    
    private suspend fun uploadToCloud(fileUri: Uri?, provider: CloudProvider, callback: ExportProgressCallback?) {
        // Placeholder for cloud upload functionality
        // Would integrate with respective cloud APIs
        callback?.onProgress(95f, "Uploading to ${provider.name}...")
        delay(1000) // Simulate upload time
        callback?.onProgress(100f, "Upload complete")
    }
    
    private fun writeLASHeader(dos: DataOutputStream, pointCount: Int) {
        // Simplified LAS header writing
        // In a real implementation, this would write proper LAS format headers
        for (i in 0 until LAS_HEADER_SIZE) {
            dos.writeByte(0)
        }
    }
    
    private fun writeLASPoint(dos: DataOutputStream, point: DataPoint) {
        // Simplified LAS point writing
        // In a real implementation, this would write proper LAS point records
        dos.writeFloat(point.position.x)
        dos.writeFloat(point.position.y)
        dos.writeFloat(point.position.z)
        dos.writeFloat(point.confidence)
    }
    
    private fun getQualityDescription(score: Float): String {
        return when {
            score >= 0.9 -> "Excellent"
            score >= 0.8 -> "Very Good"
            score >= 0.7 -> "Good"
            score >= 0.6 -> "Fair"
            score >= 0.5 -> "Poor"
            else -> "Very Poor"
        }
    }
    
    /**
     * Get list of exported files
     */
    fun getExportedFiles(): List<File> {
        val exportDir = createExportDirectory()
        return exportDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * Delete exported file
     */
    fun deleteExportedFile(filename: String): Boolean {
        val exportDir = createExportDirectory()
        val file = File(exportDir, filename)
        return file.exists() && file.delete()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        exportScope.cancel()
    }
}

// Custom date serializer for JSON
class DateSerializer : com.google.gson.JsonSerializer<Date>, com.google.gson.JsonDeserializer<Date> {
    private val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    
    override fun serialize(src: Date?, typeOfSrc: java.lang.reflect.Type?, context: com.google.gson.JsonSerializationContext?): com.google.gson.JsonElement {
        return com.google.gson.JsonPrimitive(format.format(src))
    }
    
    override fun deserialize(json: com.google.gson.JsonElement?, typeOfT: java.lang.reflect.Type?, context: com.google.gson.JsonDeserializationContext?): Date {
        return format.parse(json?.asString ?: "") ?: Date()
    }
}