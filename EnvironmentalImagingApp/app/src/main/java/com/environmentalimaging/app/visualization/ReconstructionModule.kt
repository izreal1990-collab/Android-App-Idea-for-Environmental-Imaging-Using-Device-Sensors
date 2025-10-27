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


package com.environmentalimaging.app.visualization

import android.util.Log
import com.environmentalimaging.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * 3D Reconstruction Module that generates point clouds and meshes from SLAM data
 * Implements environmental reconstruction algorithms based on research specifications
 */
class ReconstructionModule {
    
    // Reconstruction parameters
    private var gridResolution = 0.1f // 10cm voxel size
    private var maxRange = 20.0f // Maximum reconstruction range
    private var minConfidence = 0.3f // Minimum confidence threshold
    
    // Generated data
    private val _pointCloud = MutableSharedFlow<List<Point3D>>()
    val pointCloud: SharedFlow<List<Point3D>> = _pointCloud.asSharedFlow()
    
    private val _environmentMesh = MutableSharedFlow<EnvironmentMesh>()
    val environmentMesh: SharedFlow<EnvironmentMesh> = _environmentMesh.asSharedFlow()
    
    // Reconstruction state
    private var isReconstructing = false
    private var reconstructionJob: Job? = null
    
    // Accumulated data for reconstruction
    private val accumulatedLandmarks = mutableListOf<Point3D>()
    private val accumulatedPoses = mutableListOf<DevicePose>()
    private val landmarkConfidences = mutableMapOf<Point3D, Float>()
    
    companion object {
        private const val TAG = "ReconstructionModule"
        private const val MIN_LANDMARKS_FOR_RECONSTRUCTION = 10
        private const val RECONSTRUCTION_UPDATE_INTERVAL = 2000L // 2 seconds
    }
    
    /**
     * Start 3D reconstruction from SLAM data
     */
    fun startReconstruction(slamStateFlow: Flow<SlamState>) {
        if (isReconstructing) {
            Log.w(TAG, "Reconstruction already running")
            return
        }
        
        Log.d(TAG, "Starting 3D reconstruction")
        isReconstructing = true
        
        reconstructionJob = CoroutineScope(Dispatchers.Default).launch {
            slamStateFlow.collect { slamState ->
                if (isReconstructing) {
                    updateReconstruction(slamState)
                }
            }
        }
    }
    
    /**
     * Stop 3D reconstruction
     */
    fun stopReconstruction() {
        Log.d(TAG, "Stopping 3D reconstruction")
        isReconstructing = false
        reconstructionJob?.cancel()
        reconstructionJob = null
    }
    
    /**
     * Update reconstruction with new SLAM state
     */
    private suspend fun updateReconstruction(slamState: SlamState) = withContext(Dispatchers.Default) {
        try {
            // Accumulate landmarks and poses
            accumulateSlamData(slamState)
            
            // Generate point cloud from accumulated data
            val pointCloud = generatePointCloud()
            
            if (pointCloud.isNotEmpty()) {
                _pointCloud.emit(pointCloud)
                
                // Generate mesh if we have enough data
                if (accumulatedLandmarks.size >= MIN_LANDMARKS_FOR_RECONSTRUCTION) {
                    val mesh = generateEnvironmentMesh(pointCloud)
                    if (mesh.vertices.isNotEmpty()) {
                        _environmentMesh.emit(mesh)
                    }
                }
            }
            
            Unit // Explicit return to ensure these are not treated as expressions
            
        } catch (e: Exception) {
            Log.e(TAG, "Error updating reconstruction", e)
        }
    }
    
    /**
     * Accumulate SLAM data for reconstruction
     */
    private fun accumulateSlamData(slamState: SlamState) {
        // Add device pose to trajectory
        if (accumulatedPoses.isEmpty() || 
            calculateDistance(accumulatedPoses.last().position, slamState.devicePose.position) > 0.1f) {
            accumulatedPoses.add(slamState.devicePose)
        }
        
        // Add landmarks with confidence weighting
        for (landmark in slamState.landmarks) {
            val existingIndex = accumulatedLandmarks.indexOfFirst { existing ->
                calculateDistance(existing, landmark) < gridResolution
            }
            
            if (existingIndex >= 0) {
                // Update existing landmark confidence
                val existingLandmark = accumulatedLandmarks[existingIndex]
                val currentConfidence = landmarkConfidences[existingLandmark] ?: 0.5f
                val newConfidence = (currentConfidence + slamState.confidence) / 2f
                landmarkConfidences[existingLandmark] = newConfidence
            } else {
                // Add new landmark
                accumulatedLandmarks.add(landmark)
                landmarkConfidences[landmark] = slamState.confidence
            }
        }
        
        // Cleanup old or low-confidence landmarks
        cleanupLandmarks()
    }
    
    /**
     * Generate point cloud from accumulated landmarks
     */
    private fun generatePointCloud(): List<Point3D> {
        val filteredLandmarks = accumulatedLandmarks.filter { landmark ->
            val confidence = landmarkConfidences[landmark] ?: 0f
            confidence >= minConfidence && isWithinRange(landmark)
        }
        
        Log.d(TAG, "Generated point cloud with ${filteredLandmarks.size} points")
        return filteredLandmarks
    }
    
    /**
     * Generate environment mesh from point cloud using basic surface reconstruction
     */
    private fun generateEnvironmentMesh(pointCloud: List<Point3D>): EnvironmentMesh {
        try {
            // For this implementation, we'll create a simple voxel-based mesh
            val voxelGrid = createVoxelGrid(pointCloud)
            val mesh = generateMeshFromVoxels(voxelGrid)
            
            Log.d(TAG, "Generated mesh with ${mesh.vertices.size} vertices and ${mesh.triangles.size} triangles")
            return mesh
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating mesh", e)
            return EnvironmentMesh(emptyList(), emptyList(), emptyList())
        }
    }
    
    /**
     * Create voxel grid from point cloud
     */
    private fun createVoxelGrid(pointCloud: List<Point3D>): VoxelGrid {
        // Calculate bounds
        val minX = pointCloud.minOfOrNull { it.x } ?: 0f
        val maxX = pointCloud.maxOfOrNull { it.x } ?: 0f
        val minY = pointCloud.minOfOrNull { it.y } ?: 0f
        val maxY = pointCloud.maxOfOrNull { it.y } ?: 0f
        val minZ = pointCloud.minOfOrNull { it.z } ?: 0f
        val maxZ = pointCloud.maxOfOrNull { it.z } ?: 0f
        
        val sizeX = ((maxX - minX) / gridResolution).toInt() + 1
        val sizeY = ((maxY - minY) / gridResolution).toInt() + 1
        val sizeZ = ((maxZ - minZ) / gridResolution).toInt() + 1
        
        val voxels = Array(sizeX) { Array(sizeY) { BooleanArray(sizeZ) } }
        
        // Fill voxels with point data
        for (point in pointCloud) {
            val x = ((point.x - minX) / gridResolution).toInt().coerceIn(0, sizeX - 1)
            val y = ((point.y - minY) / gridResolution).toInt().coerceIn(0, sizeY - 1)
            val z = ((point.z - minZ) / gridResolution).toInt().coerceIn(0, sizeZ - 1)
            
            voxels[x][y][z] = true
        }
        
        return VoxelGrid(voxels, minX, minY, minZ, gridResolution)
    }
    
    /**
     * Generate mesh from voxel grid using marching cubes-like algorithm (simplified)
     */
    private fun generateMeshFromVoxels(voxelGrid: VoxelGrid): EnvironmentMesh {
        val vertices = mutableListOf<Point3D>()
        val triangles = mutableListOf<Triangle>()
        val normals = mutableListOf<Point3D>()
        
        val voxels = voxelGrid.voxels
        val sizeX = voxels.size
        val sizeY = voxels[0].size
        val sizeZ = voxels[0][0].size
        
        // Simple cube generation for occupied voxels
        for (x in 0 until sizeX - 1) {
            for (y in 0 until sizeY - 1) {
                for (z in 0 until sizeZ - 1) {
                    if (voxels[x][y][z]) {
                        // Check if this voxel is on the surface (has empty neighbors)
                        if (isOnSurface(voxels, x, y, z)) {
                            val cubeVertices = generateCubeVertices(voxelGrid, x, y, z)
                            val cubeTriangles = generateCubeTriangles(vertices.size)
                            val cubeNormals = generateCubeNormals()
                            
                            vertices.addAll(cubeVertices)
                            triangles.addAll(cubeTriangles)
                            normals.addAll(cubeNormals)
                        }
                    }
                }
            }
        }
        
        return EnvironmentMesh(vertices, triangles, normals)
    }
    
    /**
     * Check if voxel is on the surface (has at least one empty neighbor)
     */
    private fun isOnSurface(voxels: Array<Array<BooleanArray>>, x: Int, y: Int, z: Int): Boolean {
        val directions = arrayOf(
            intArrayOf(-1, 0, 0), intArrayOf(1, 0, 0),
            intArrayOf(0, -1, 0), intArrayOf(0, 1, 0),
            intArrayOf(0, 0, -1), intArrayOf(0, 0, 1)
        )
        
        for (dir in directions) {
            val nx = x + dir[0]
            val ny = y + dir[1]
            val nz = z + dir[2]
            
            if (nx < 0 || nx >= voxels.size ||
                ny < 0 || ny >= voxels[0].size ||
                nz < 0 || nz >= voxels[0][0].size ||
                !voxels[nx][ny][nz]) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Generate vertices for a voxel cube
     */
    private fun generateCubeVertices(voxelGrid: VoxelGrid, x: Int, y: Int, z: Int): List<Point3D> {
        val worldX = voxelGrid.originX + x * voxelGrid.resolution
        val worldY = voxelGrid.originY + y * voxelGrid.resolution
        val worldZ = voxelGrid.originZ + z * voxelGrid.resolution
        val size = voxelGrid.resolution
        
        return listOf(
            Point3D(worldX, worldY, worldZ),
            Point3D(worldX + size, worldY, worldZ),
            Point3D(worldX + size, worldY + size, worldZ),
            Point3D(worldX, worldY + size, worldZ),
            Point3D(worldX, worldY, worldZ + size),
            Point3D(worldX + size, worldY, worldZ + size),
            Point3D(worldX + size, worldY + size, worldZ + size),
            Point3D(worldX, worldY + size, worldZ + size)
        )
    }
    
    /**
     * Generate triangles for a cube (12 triangles, 2 per face)
     */
    private fun generateCubeTriangles(vertexOffset: Int): List<Triangle> {
        val faces = arrayOf(
            // Bottom face
            intArrayOf(0, 1, 2), intArrayOf(0, 2, 3),
            // Top face
            intArrayOf(4, 7, 6), intArrayOf(4, 6, 5),
            // Front face
            intArrayOf(0, 4, 5), intArrayOf(0, 5, 1),
            // Back face
            intArrayOf(2, 6, 7), intArrayOf(2, 7, 3),
            // Left face
            intArrayOf(0, 3, 7), intArrayOf(0, 7, 4),
            // Right face
            intArrayOf(1, 5, 6), intArrayOf(1, 6, 2)
        )
        
        return faces.map { face ->
            Triangle(
                vertexOffset + face[0],
                vertexOffset + face[1],
                vertexOffset + face[2]
            )
        }
    }
    
    /**
     * Generate normals for cube faces
     */
    private fun generateCubeNormals(): List<Point3D> {
        return listOf(
            Point3D(0f, 0f, -1f), Point3D(0f, 0f, -1f), // Bottom
            Point3D(0f, 0f, 1f), Point3D(0f, 0f, 1f),   // Top
            Point3D(0f, -1f, 0f), Point3D(0f, -1f, 0f), // Front
            Point3D(0f, 1f, 0f), Point3D(0f, 1f, 0f),   // Back
            Point3D(-1f, 0f, 0f), Point3D(-1f, 0f, 0f), // Left
            Point3D(1f, 0f, 0f), Point3D(1f, 0f, 0f)    // Right
        )
    }
    
    /**
     * Cleanup old or low-confidence landmarks
     */
    private fun cleanupLandmarks() {
        val iterator = accumulatedLandmarks.iterator()
        while (iterator.hasNext()) {
            val landmark = iterator.next()
            val confidence = landmarkConfidences[landmark] ?: 0f
            
            if (confidence < minConfidence * 0.5f || !isWithinRange(landmark)) {
                iterator.remove()
                landmarkConfidences.remove(landmark)
            }
        }
    }
    
    /**
     * Check if landmark is within reconstruction range
     */
    private fun isWithinRange(landmark: Point3D): Boolean {
        if (accumulatedPoses.isEmpty()) return true
        
        val lastPose = accumulatedPoses.last()
        val distance = calculateDistance(lastPose.position, landmark)
        return distance <= maxRange
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
     * Get reconstruction statistics
     */
    fun getStatistics(): ReconstructionStatistics {
        return ReconstructionStatistics(
            landmarkCount = accumulatedLandmarks.size,
            poseCount = accumulatedPoses.size,
            isReconstructing = isReconstructing,
            gridResolution = gridResolution
        )
    }
    
    /**
     * Update reconstruction parameters
     */
    fun updateParameters(resolution: Float, range: Float, confidence: Float) {
        gridResolution = resolution.coerceIn(0.05f, 1.0f)
        maxRange = range.coerceIn(5.0f, 50.0f)
        minConfidence = confidence.coerceIn(0.1f, 0.9f)
        
        Log.d(TAG, "Updated parameters - Resolution: $gridResolution, Range: $maxRange, Confidence: $minConfidence")
    }
}

/**
 * Voxel grid data structure
 */
data class VoxelGrid(
    val voxels: Array<Array<BooleanArray>>,
    val originX: Float,
    val originY: Float,
    val originZ: Float,
    val resolution: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VoxelGrid

        if (!voxels.contentDeepEquals(other.voxels)) return false
        if (originX != other.originX) return false
        if (originY != other.originY) return false
        if (originZ != other.originZ) return false
        if (resolution != other.resolution) return false

        return true
    }

    override fun hashCode(): Int {
        var result = voxels.contentDeepHashCode()
        result = 31 * result + originX.hashCode()
        result = 31 * result + originY.hashCode()
        result = 31 * result + originZ.hashCode()
        result = 31 * result + resolution.hashCode()
        return result
    }
}

/**
 * Environment mesh data structure
 */
data class EnvironmentMesh(
    val vertices: List<Point3D>,
    val triangles: List<Triangle>,
    val normals: List<Point3D>
)

/**
 * Triangle definition
 */
data class Triangle(
    val vertex1: Int,
    val vertex2: Int,
    val vertex3: Int
)

/**
 * Reconstruction statistics
 */
data class ReconstructionStatistics(
    val landmarkCount: Int,
    val poseCount: Int,
    val isReconstructing: Boolean,
    val gridResolution: Float
)