package com.environmentalimaging.app.storage

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.environmentalimaging.app.data.EnvironmentalSnapshot
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Background storage processor that handles large environmental snapshots
 * without blocking the UI or causing memory issues
 */
class BackgroundStorageProcessor(
    private val context: Context,
    private val storageManager: SnapshotStorageManager
) {
    companion object {
        private const val TAG = "BackgroundStorageProcessor"
        private const val MAX_CONCURRENT_OPERATIONS = 2
        private const val MEMORY_THRESHOLD_MB = 50 // Free memory threshold in MB
    }
    
    private val processingScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + 
        CoroutineName("StorageProcessor")
    )
    
    private val operationQueue = Channel<StorageOperation>(capacity = Channel.UNLIMITED)
    private val activeOperations = AtomicInteger(0)
    private val isProcessing = AtomicBoolean(false)
    
    sealed class StorageOperation {
        data class Save(
            val snapshot: EnvironmentalSnapshot,
            val onProgress: (Float) -> Unit = {},
            val onComplete: (Boolean) -> Unit = {}
        ) : StorageOperation()
        
        data class Load(
            val snapshotId: String,
            val onProgress: (Float) -> Unit = {},
            val onComplete: (EnvironmentalSnapshot?) -> Unit = {}
        ) : StorageOperation()
        
        data class Delete(
            val snapshotId: String,
            val onComplete: (Boolean) -> Unit = {}
        ) : StorageOperation()
    }
    
    init {
        startProcessor()
    }
    
    /**
     * Queue a snapshot for background saving
     */
    suspend fun saveSnapshotAsync(
        snapshot: EnvironmentalSnapshot,
        onProgress: (Float) -> Unit = {},
        onComplete: (Boolean) -> Unit = {}
    ) {
        val operation = StorageOperation.Save(snapshot, onProgress, onComplete)
        operationQueue.trySend(operation)
        Log.d(TAG, "Queued snapshot for saving: ${snapshot.id}")
    }
    
    /**
     * Queue a snapshot for background loading
     */
    suspend fun loadSnapshotAsync(
        snapshotId: String,
        onProgress: (Float) -> Unit = {},
        onComplete: (EnvironmentalSnapshot?) -> Unit = {}
    ) {
        val operation = StorageOperation.Load(snapshotId, onProgress, onComplete)
        operationQueue.trySend(operation)
        Log.d(TAG, "Queued snapshot for loading: $snapshotId")
    }
    
    /**
     * Queue a snapshot for deletion
     */
    suspend fun deleteSnapshotAsync(
        snapshotId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val operation = StorageOperation.Delete(snapshotId, onComplete)
        operationQueue.trySend(operation)
        Log.d(TAG, "Queued snapshot for deletion: $snapshotId")
    }
    
    /**
     * Get processing status
     */
    fun getProcessingStatus(): ProcessingStatus {
        return ProcessingStatus(
            isProcessing = isProcessing.get(),
            activeOperations = activeOperations.get(),
            queuedOperations = operationQueue.tryReceive().getOrNull()?.let { 1 } ?: 0
        )
    }
    
    /**
     * Start the background processor
     */
    private fun startProcessor() {
        processingScope.launch {
            isProcessing.set(true)
            
            try {
                while (true) {
                    // Wait for next operation
                    val operation = operationQueue.receive()
                    
                    // Check if we can process more operations
                    while (activeOperations.get() >= MAX_CONCURRENT_OPERATIONS) {
                        delay(100) // Wait for active operations to complete
                    }
                    
                    // Check memory before processing
                    if (!hasEnoughMemory()) {
                        Log.w(TAG, "Low memory detected, requesting GC before processing")
                        System.gc()
                        delay(1000) // Give GC time to work
                        
                        if (!hasEnoughMemory()) {
                            Log.e(TAG, "Still low on memory, skipping operation")
                            when (operation) {
                                is StorageOperation.Save -> operation.onComplete(false)
                                is StorageOperation.Load -> operation.onComplete(null)
                                is StorageOperation.Delete -> operation.onComplete(false)
                            }
                            continue
                        }
                    }
                    
                    // Process the operation
                    activeOperations.incrementAndGet()
                    launch {
                        try {
                            processOperation(operation)
                        } finally {
                            activeOperations.decrementAndGet()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in background processor", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }
    
    /**
     * Process individual storage operation
     */
    private suspend fun processOperation(operation: StorageOperation) {
        when (operation) {
            is StorageOperation.Save -> {
                try {
                    Log.d(TAG, "Processing save operation: ${operation.snapshot.id}")
                    operation.onProgress(0.1f)
                    
                    // Optimize snapshot before saving
                    val optimizedSnapshot = optimizeSnapshot(operation.snapshot) { progress ->
                        operation.onProgress(0.1f + progress * 0.4f) // 10-50% for optimization
                    }
                    
                    operation.onProgress(0.5f)
                    
                    // Save with progress monitoring
                    val success = storageManager.saveSnapshot(optimizedSnapshot)
                    
                    operation.onProgress(1.0f)
                    operation.onComplete(success)
                    
                    Log.d(TAG, "Save operation completed: ${operation.snapshot.id}, success: $success")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving snapshot: ${operation.snapshot.id}", e)
                    operation.onComplete(false)
                }
            }
            
            is StorageOperation.Load -> {
                try {
                    Log.d(TAG, "Processing load operation: ${operation.snapshotId}")
                    operation.onProgress(0.1f)
                    
                    val snapshot = storageManager.loadSnapshot(operation.snapshotId)
                    
                    operation.onProgress(1.0f)
                    operation.onComplete(snapshot)
                    
                    Log.d(TAG, "Load operation completed: ${operation.snapshotId}")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading snapshot: ${operation.snapshotId}", e)
                    operation.onComplete(null)
                }
            }
            
            is StorageOperation.Delete -> {
                try {
                    Log.d(TAG, "Processing delete operation: ${operation.snapshotId}")
                    
                    val success = storageManager.deleteSnapshot(operation.snapshotId)
                    operation.onComplete(success)
                    
                    Log.d(TAG, "Delete operation completed: ${operation.snapshotId}, success: $success")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error deleting snapshot: ${operation.snapshotId}", e)
                    operation.onComplete(false)
                }
            }
        }
    }
    
    /**
     * Optimize snapshot to reduce memory usage
     */
    private suspend fun optimizeSnapshot(
        snapshot: EnvironmentalSnapshot,
        onProgress: (Float) -> Unit
    ): EnvironmentalSnapshot = withContext(Dispatchers.Default) {
        
        try {
            onProgress(0.0f)
            
            // Reduce point cloud density if too large
            val optimizedPointCloud = if (snapshot.pointCloud.size > 10000) {
                Log.d(TAG, "Optimizing large point cloud: ${snapshot.pointCloud.size} points")
                
                // Sample every nth point to reduce density
                val sampleRate = snapshot.pointCloud.size / 8000 // Target ~8000 points
                snapshot.pointCloud.filterIndexed { index, _ -> 
                    index % maxOf(1, sampleRate) == 0 
                }
            } else {
                snapshot.pointCloud
            }
            
            onProgress(0.3f)
            
            // Reduce ranging measurements if too many
            val optimizedRanging = if (snapshot.rangingMeasurements.size > 1000) {
                Log.d(TAG, "Optimizing ranging measurements: ${snapshot.rangingMeasurements.size}")
                snapshot.rangingMeasurements.takeLast(1000) // Keep most recent
            } else {
                snapshot.rangingMeasurements
            }
            
            onProgress(0.6f)
            
            // Reduce IMU data if too much
            val optimizedIMU = if (snapshot.imuData.size > 2000) {
                Log.d(TAG, "Optimizing IMU data: ${snapshot.imuData.size}")
                snapshot.imuData.filterIndexed { index, _ -> index % 2 == 0 } // Sample every other
            } else {
                snapshot.imuData
            }
            
            onProgress(1.0f)
            
            val optimized = snapshot.copy(
                pointCloud = optimizedPointCloud,
                rangingMeasurements = optimizedRanging,
                imuData = optimizedIMU
            )
            
            Log.d(TAG, "Snapshot optimized - Points: ${snapshot.pointCloud.size} -> ${optimized.pointCloud.size}, " +
                    "Ranging: ${snapshot.rangingMeasurements.size} -> ${optimized.rangingMeasurements.size}, " +
                    "IMU: ${snapshot.imuData.size} -> ${optimized.imuData.size}")
            
            optimized
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing snapshot", e)
            snapshot // Return original if optimization fails
        }
    }
    
    /**
     * Check if there's enough memory for processing
     */
    private fun hasEnoughMemory(): Boolean {
        val runtime = Runtime.getRuntime()
        val freeMemory = runtime.freeMemory()
        val totalMemory = runtime.totalMemory()
        val maxMemory = runtime.maxMemory()
        
        val availableMemory = maxMemory - (totalMemory - freeMemory)
        val availableMB = availableMemory / (1024 * 1024)
        
        Log.d(TAG, "Available memory: ${availableMB}MB")
        return availableMB > MEMORY_THRESHOLD_MB
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        Log.d(TAG, "Shutting down background storage processor")
        processingScope.cancel()
        operationQueue.close()
    }
}

/**
 * Processing status information
 */
data class ProcessingStatus(
    val isProcessing: Boolean,
    val activeOperations: Int,
    val queuedOperations: Int
)