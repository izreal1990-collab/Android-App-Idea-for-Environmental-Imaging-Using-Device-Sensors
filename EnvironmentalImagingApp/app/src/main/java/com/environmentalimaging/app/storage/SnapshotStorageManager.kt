package com.environmentalimaging.app.storage

import android.content.Context
import android.util.Log
import com.environmentalimaging.app.data.*
import com.environmentalimaging.app.visualization.EnvironmentMesh
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Temporal Snapshot Storage Module
 * Handles saving and loading of environmental snapshots with structured format
 * Uses compressed archives with JSON metadata and binary 3D model data
 */
class SnapshotStorageManager(private val context: Context) {
    
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()
    
    private val storageDir: File by lazy {
        File(context.filesDir, SNAPSHOTS_DIRECTORY).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    companion object {
        private const val TAG = "SnapshotStorageManager"
        private const val SNAPSHOTS_DIRECTORY = "environmental_snapshots"
        private const val METADATA_FILE = "metadata.json"
        private const val POINT_CLOUD_FILE = "point_cloud.bin"
        private const val MESH_FILE = "mesh.bin"
        private const val RANGING_DATA_FILE = "ranging_data.json"
        private const val IMU_DATA_FILE = "imu_data.bin"
        private const val TRAJECTORY_FILE = "trajectory.bin"
        private const val SNAPSHOT_EXTENSION = ".eisnapshot"
    }
    
    /**
     * Save environmental snapshot to storage
     */
    suspend fun saveSnapshot(snapshot: EnvironmentalSnapshot): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Saving snapshot: ${snapshot.id}")
            
            val snapshotFile = File(storageDir, "${snapshot.id}$SNAPSHOT_EXTENSION")
            
            // Create compressed archive
            ZipOutputStream(FileOutputStream(snapshotFile)).use { zipOut ->
                // Save metadata
                saveMetadata(zipOut, snapshot)
                
                // Save point cloud data
                savePointCloud(zipOut, snapshot.pointCloud)
                
                // Save ranging measurements
                saveRangingData(zipOut, snapshot.rangingMeasurements)
                
                // Save IMU data
                saveIMUData(zipOut, snapshot.imuData)
                
                // If we have trajectory data in metadata, save it
                val trajectory = snapshot.metadata["trajectory"] as? List<DevicePose>
                trajectory?.let { saveTrajectory(zipOut, it) }
                
                // If we have mesh data in metadata, save it
                val mesh = snapshot.metadata["mesh"] as? EnvironmentMesh
                mesh?.let { saveMesh(zipOut, it) }
            }
            
            Log.d(TAG, "Snapshot saved successfully: ${snapshotFile.absolutePath}")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving snapshot: ${snapshot.id}", e)
            false
        }
    }
    
    /**
     * Load environmental snapshot from storage
     */
    suspend fun loadSnapshot(snapshotId: String): EnvironmentalSnapshot? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Loading snapshot: $snapshotId")
            
            val snapshotFile = File(storageDir, "$snapshotId$SNAPSHOT_EXTENSION")
            if (!snapshotFile.exists()) {
                Log.w(TAG, "Snapshot file not found: ${snapshotFile.name}")
                return@withContext null
            }
            
            var metadata: SnapshotMetadata? = null
            var pointCloud: List<Point3D> = emptyList()
            var rangingData: List<RangingMeasurement> = emptyList()
            var imuData: List<IMUMeasurement> = emptyList()
            var trajectory: List<DevicePose>? = null
            var mesh: EnvironmentMesh? = null
            
            // Extract from compressed archive
            ZipInputStream(FileInputStream(snapshotFile)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                
                while (entry != null) {
                    when (entry.name) {
                        METADATA_FILE -> {
                            metadata = loadMetadata(zipIn)
                        }
                        POINT_CLOUD_FILE -> {
                            pointCloud = loadPointCloud(zipIn)
                        }
                        RANGING_DATA_FILE -> {
                            rangingData = loadRangingData(zipIn)
                        }
                        IMU_DATA_FILE -> {
                            imuData = loadIMUData(zipIn)
                        }
                        TRAJECTORY_FILE -> {
                            trajectory = loadTrajectory(zipIn)
                        }
                        MESH_FILE -> {
                            mesh = loadMesh(zipIn)
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            
            // Reconstruct snapshot
            metadata?.let { meta ->
                val reconstructedMetadata = meta.additionalData.toMutableMap()
                trajectory?.let { reconstructedMetadata["trajectory"] = it }
                mesh?.let { reconstructedMetadata["mesh"] = it }
                
                val snapshot = EnvironmentalSnapshot(
                    id = meta.id,
                    timestamp = meta.timestamp,
                    devicePose = meta.devicePose,
                    pointCloud = pointCloud,
                    rangingMeasurements = rangingData,
                    imuData = imuData,
                    metadata = reconstructedMetadata
                )
                
                Log.d(TAG, "Snapshot loaded successfully: ${snapshot.id}")
                return@withContext snapshot
            }
            
            Log.w(TAG, "Failed to load snapshot metadata")
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading snapshot: $snapshotId", e)
            null
        }
    }
    
    /**
     * List all available snapshots
     */
    suspend fun listSnapshots(): List<SnapshotInfo> = withContext(Dispatchers.IO) {
        try {
            val snapshots = mutableListOf<SnapshotInfo>()
            
            storageDir.listFiles { _, name -> 
                name.endsWith(SNAPSHOT_EXTENSION) 
            }?.forEach { file ->
                try {
                    // Extract basic info from each snapshot
                    ZipInputStream(FileInputStream(file)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        
                        while (entry != null) {
                            if (entry.name == METADATA_FILE) {
                                val metadata = loadMetadata(zipIn)
                                metadata?.let { meta ->
                                    snapshots.add(
                                        SnapshotInfo(
                                            id = meta.id,
                                            timestamp = meta.timestamp,
                                            title = meta.title,
                                            description = meta.description,
                                            fileSize = file.length(),
                                            pointCount = meta.pointCount,
                                            measurementCount = meta.measurementCount
                                        )
                                    )
                                }
                                break
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading snapshot info: ${file.name}", e)
                }
            }
            
            // Sort by timestamp (newest first)
            snapshots.sortByDescending { it.timestamp }
            
            Log.d(TAG, "Found ${snapshots.size} snapshots")
            snapshots
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing snapshots", e)
            emptyList()
        }
    }
    
    /**
     * Delete snapshot from storage
     */
    suspend fun deleteSnapshot(snapshotId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val snapshotFile = File(storageDir, "$snapshotId$SNAPSHOT_EXTENSION")
            val deleted = snapshotFile.delete()
            
            if (deleted) {
                Log.d(TAG, "Snapshot deleted: $snapshotId")
            } else {
                Log.w(TAG, "Failed to delete snapshot: $snapshotId")
            }
            
            deleted
            
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting snapshot: $snapshotId", e)
            false
        }
    }
    
    /**
     * Get storage statistics
     */
    suspend fun getStorageStatistics(): StorageStatistics = withContext(Dispatchers.IO) {
        try {
            val files = storageDir.listFiles { _, name -> 
                name.endsWith(SNAPSHOT_EXTENSION) 
            } ?: emptyArray()
            
            val totalSize = files.sumOf { it.length() }
            val snapshotCount = files.size
            
            StorageStatistics(
                snapshotCount = snapshotCount,
                totalSizeBytes = totalSize,
                availableSpaceBytes = storageDir.freeSpace,
                storageDirectory = storageDir.absolutePath
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting storage statistics", e)
            StorageStatistics(0, 0L, 0L, "")
        }
    }
    
    // Private helper methods for saving data
    
    private fun saveMetadata(zipOut: ZipOutputStream, snapshot: EnvironmentalSnapshot) {
        val metadata = SnapshotMetadata(
            id = snapshot.id,
            timestamp = snapshot.timestamp,
            title = snapshot.metadata["title"] as? String ?: "Environmental Snapshot",
            description = snapshot.metadata["description"] as? String ?: "",
            devicePose = snapshot.devicePose,
            pointCount = snapshot.pointCloud.size,
            measurementCount = snapshot.rangingMeasurements.size,
            imuSampleCount = snapshot.imuData.size,
            additionalData = snapshot.metadata.filterKeys { it != "title" && it != "description" && it != "trajectory" && it != "mesh" }
        )
        
        zipOut.putNextEntry(ZipEntry(METADATA_FILE))
        val json = gson.toJson(metadata)
        zipOut.write(json.toByteArray())
    }
    
    private fun savePointCloud(zipOut: ZipOutputStream, pointCloud: List<Point3D>) {
        if (pointCloud.isEmpty()) return
        
        zipOut.putNextEntry(ZipEntry(POINT_CLOUD_FILE))
        val dataOut = DataOutputStream(zipOut)
        
        dataOut.writeInt(pointCloud.size)
        for (point in pointCloud) {
            dataOut.writeFloat(point.x)
            dataOut.writeFloat(point.y)
            dataOut.writeFloat(point.z)
        }
    }
    
    private fun saveRangingData(zipOut: ZipOutputStream, rangingData: List<RangingMeasurement>) {
        if (rangingData.isEmpty()) return
        
        zipOut.putNextEntry(ZipEntry(RANGING_DATA_FILE))
        val json = gson.toJson(rangingData)
        zipOut.write(json.toByteArray())
    }
    
    private fun saveIMUData(zipOut: ZipOutputStream, imuData: List<IMUMeasurement>) {
        if (imuData.isEmpty()) return
        
        zipOut.putNextEntry(ZipEntry(IMU_DATA_FILE))
        val dataOut = DataOutputStream(zipOut)
        
        dataOut.writeInt(imuData.size)
        for (measurement in imuData) {
            // Acceleration
            dataOut.writeFloat(measurement.acceleration[0])
            dataOut.writeFloat(measurement.acceleration[1])
            dataOut.writeFloat(measurement.acceleration[2])
            
            // Angular velocity
            dataOut.writeFloat(measurement.angularVelocity[0])
            dataOut.writeFloat(measurement.angularVelocity[1])
            dataOut.writeFloat(measurement.angularVelocity[2])
            
            // Magnetic field (optional)
            val hasMagField = measurement.magneticField != null
            dataOut.writeBoolean(hasMagField)
            if (hasMagField) {
                dataOut.writeFloat(measurement.magneticField!![0])
                dataOut.writeFloat(measurement.magneticField!![1])
                dataOut.writeFloat(measurement.magneticField!![2])
            }
            
            dataOut.writeLong(measurement.timestamp)
        }
    }
    
    private fun saveTrajectory(zipOut: ZipOutputStream, trajectory: List<DevicePose>) {
        if (trajectory.isEmpty()) return
        
        zipOut.putNextEntry(ZipEntry(TRAJECTORY_FILE))
        val dataOut = DataOutputStream(zipOut)
        
        dataOut.writeInt(trajectory.size)
        for (pose in trajectory) {
            // Position
            dataOut.writeFloat(pose.position.x)
            dataOut.writeFloat(pose.position.y)
            dataOut.writeFloat(pose.position.z)
            
            // Orientation quaternion
            dataOut.writeFloat(pose.orientation[0])
            dataOut.writeFloat(pose.orientation[1])
            dataOut.writeFloat(pose.orientation[2])
            dataOut.writeFloat(pose.orientation[3])
            
            dataOut.writeLong(pose.timestamp)
        }
    }
    
    private fun saveMesh(zipOut: ZipOutputStream, mesh: EnvironmentMesh) {
        if (mesh.vertices.isEmpty()) return
        
        zipOut.putNextEntry(ZipEntry(MESH_FILE))
        val dataOut = DataOutputStream(zipOut)
        
        // Save vertices
        dataOut.writeInt(mesh.vertices.size)
        for (vertex in mesh.vertices) {
            dataOut.writeFloat(vertex.x)
            dataOut.writeFloat(vertex.y)
            dataOut.writeFloat(vertex.z)
        }
        
        // Save triangles
        dataOut.writeInt(mesh.triangles.size)
        for (triangle in mesh.triangles) {
            dataOut.writeInt(triangle.vertex1)
            dataOut.writeInt(triangle.vertex2)
            dataOut.writeInt(triangle.vertex3)
        }
        
        // Save normals
        dataOut.writeInt(mesh.normals.size)
        for (normal in mesh.normals) {
            dataOut.writeFloat(normal.x)
            dataOut.writeFloat(normal.y)
            dataOut.writeFloat(normal.z)
        }
    }
    
    // Private helper methods for loading data
    
    private fun loadMetadata(zipIn: ZipInputStream): SnapshotMetadata? {
        return try {
            val json = zipIn.readBytes().toString(Charsets.UTF_8)
            gson.fromJson(json, SnapshotMetadata::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata", e)
            null
        }
    }
    
    private fun loadPointCloud(zipIn: ZipInputStream): List<Point3D> {
        return try {
            val dataIn = DataInputStream(zipIn)
            val count = dataIn.readInt()
            val points = mutableListOf<Point3D>()
            
            repeat(count) {
                val x = dataIn.readFloat()
                val y = dataIn.readFloat()
                val z = dataIn.readFloat()
                points.add(Point3D(x, y, z))
            }
            
            points
        } catch (e: Exception) {
            Log.e(TAG, "Error loading point cloud", e)
            emptyList()
        }
    }
    
    private fun loadRangingData(zipIn: ZipInputStream): List<RangingMeasurement> {
        return try {
            val json = zipIn.readBytes().toString(Charsets.UTF_8)
            val type = object : com.google.gson.reflect.TypeToken<List<RangingMeasurement>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ranging data", e)
            emptyList()
        }
    }
    
    private fun loadIMUData(zipIn: ZipInputStream): List<IMUMeasurement> {
        return try {
            val dataIn = DataInputStream(zipIn)
            val count = dataIn.readInt()
            val measurements = mutableListOf<IMUMeasurement>()
            
            repeat(count) {
                val acceleration = floatArrayOf(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                )
                
                val angularVelocity = floatArrayOf(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                )
                
                val magneticField = if (dataIn.readBoolean()) {
                    floatArrayOf(
                        dataIn.readFloat(),
                        dataIn.readFloat(),
                        dataIn.readFloat()
                    )
                } else null
                
                val timestamp = dataIn.readLong()
                
                measurements.add(
                    IMUMeasurement(acceleration, angularVelocity, magneticField, timestamp)
                )
            }
            
            measurements
        } catch (e: Exception) {
            Log.e(TAG, "Error loading IMU data", e)
            emptyList()
        }
    }
    
    private fun loadTrajectory(zipIn: ZipInputStream): List<DevicePose> {
        return try {
            val dataIn = DataInputStream(zipIn)
            val count = dataIn.readInt()
            val poses = mutableListOf<DevicePose>()
            
            repeat(count) {
                val position = Point3D(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                )
                
                val orientation = floatArrayOf(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                )
                
                val timestamp = dataIn.readLong()
                
                poses.add(DevicePose(position, orientation, timestamp))
            }
            
            poses
        } catch (e: Exception) {
            Log.e(TAG, "Error loading trajectory", e)
            emptyList()
        }
    }
    
    private fun loadMesh(zipIn: ZipInputStream): EnvironmentMesh {
        return try {
            val dataIn = DataInputStream(zipIn)
            
            // Load vertices
            val vertexCount = dataIn.readInt()
            val vertices = mutableListOf<Point3D>()
            repeat(vertexCount) {
                vertices.add(Point3D(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                ))
            }
            
            // Load triangles
            val triangleCount = dataIn.readInt()
            val triangles = mutableListOf<com.environmentalimaging.app.visualization.Triangle>()
            repeat(triangleCount) {
                triangles.add(com.environmentalimaging.app.visualization.Triangle(
                    dataIn.readInt(),
                    dataIn.readInt(),
                    dataIn.readInt()
                ))
            }
            
            // Load normals
            val normalCount = dataIn.readInt()
            val normals = mutableListOf<Point3D>()
            repeat(normalCount) {
                normals.add(Point3D(
                    dataIn.readFloat(),
                    dataIn.readFloat(),
                    dataIn.readFloat()
                ))
            }
            
            EnvironmentMesh(vertices, triangles, normals)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading mesh", e)
            EnvironmentMesh(emptyList(), emptyList(), emptyList())
        }
    }
    
    /**
     * Clear all snapshots from storage
     */
    suspend fun clearAllSnapshots(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Clearing all snapshots")
            
            val files = storageDir.listFiles { _, name -> 
                name.endsWith(SNAPSHOT_EXTENSION) 
            } ?: emptyArray()
            
            var allDeleted = true
            files.forEach { file ->
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete: ${file.name}")
                    allDeleted = false
                }
            }
            
            Log.d(TAG, "Cleared ${files.size} snapshots, success: $allDeleted")
            allDeleted
            
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all snapshots", e)
            false
        }
    }
}

/**
 * Snapshot metadata for storage
 */
data class SnapshotMetadata(
    val id: String,
    val timestamp: Long,
    val title: String,
    val description: String,
    val devicePose: DevicePose,
    val pointCount: Int,
    val measurementCount: Int,
    val imuSampleCount: Int,
    val additionalData: Map<String, Any>
)

/**
 * Snapshot information for listing
 */
data class SnapshotInfo(
    val id: String,
    val timestamp: Long,
    val title: String,
    val description: String,
    val fileSize: Long,
    val pointCount: Int,
    val measurementCount: Int
)

/**
 * Storage statistics
 */
data class StorageStatistics(
    val snapshotCount: Int,
    val totalSizeBytes: Long,
    val availableSpaceBytes: Long,
    val storageDirectory: String
)