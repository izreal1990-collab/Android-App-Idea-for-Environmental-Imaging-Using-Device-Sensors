package com.environmentalimaging.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.data.*
import com.environmentalimaging.app.sensors.DataAcquisitionModule
import com.environmentalimaging.app.slam.SLAMProcessor
import com.environmentalimaging.app.storage.SnapshotStorageManager
import com.environmentalimaging.app.storage.BackgroundStorageProcessor
import com.environmentalimaging.app.visualization.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * Main Activity for Environmental Imaging App
 * Orchestrates data acquisition, SLAM processing, 3D visualization, and snapshot management
 */
class MainActivity : AppCompatActivity() {
    
    // Core modules
    private lateinit var dataAcquisition: DataAcquisitionModule
    private lateinit var slamProcessor: SLAMProcessor
    private lateinit var reconstructionModule: ReconstructionModule
    private lateinit var storageManager: SnapshotStorageManager
    private lateinit var backgroundStorageProcessor: BackgroundStorageProcessor
    
    // UI components
    private lateinit var visualizationView: EnvironmentalVisualizationView
    private lateinit var scanButton: MaterialButton
    private lateinit var snapshotButton: MaterialButton
    private lateinit var resetButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var snapshotsButton: MaterialButton
    
    // Status indicators
    private lateinit var statusText: android.widget.TextView
    private lateinit var landmarkCountText: android.widget.TextView
    private lateinit var measurementCountText: android.widget.TextView
    private lateinit var loadingOverlay: View
    private lateinit var loadingText: android.widget.TextView
    
    // Sensor status icons
    private lateinit var wifiStatusIcon: android.widget.ImageView
    private lateinit var bluetoothStatusIcon: android.widget.ImageView
    private lateinit var acousticStatusIcon: android.widget.ImageView
    private lateinit var imuStatusIcon: android.widget.ImageView
    
    // State
    private var isScanning = false
    private var slamProcessingJob: Job? = null
    private var reconstructionJob: Job? = null
    
    // Statistics
    private var landmarkCount = 0
    private var measurementCount = 0
    private var currentDevicePose: DevicePose? = null
    private var deviceTrajectory = mutableListOf<DevicePose>()
    
    companion object {
        private const val TAG = "MainActivity"
        
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        ).let { permissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions + arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            } else permissions
        }.let { permissions ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions + arrayOf(
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            } else permissions
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            initializeApplication()
        } else {
            showPermissionError()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        Log.d(TAG, "Environmental Imaging App starting")
        
        initializeUI()
        checkPermissions()
    }
    
    private fun initializeUI() {
        // Get UI components
        visualizationView = findViewById(R.id.visualizationView)
        scanButton = findViewById(R.id.scanButton)
        snapshotButton = findViewById(R.id.snapshotButton)
        resetButton = findViewById(R.id.resetButton)
        settingsButton = findViewById(R.id.settingsButton)
        snapshotsButton = findViewById(R.id.snapshotsButton)
        
        statusText = findViewById(R.id.statusText)
        landmarkCountText = findViewById(R.id.landmarkCountText)
        measurementCountText = findViewById(R.id.measurementCountText)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingText = findViewById(R.id.loadingText)
        
        wifiStatusIcon = findViewById(R.id.wifiStatusIcon)
        bluetoothStatusIcon = findViewById(R.id.bluetoothStatusIcon)
        acousticStatusIcon = findViewById(R.id.acousticStatusIcon)
        imuStatusIcon = findViewById(R.id.imuStatusIcon)
        
        // Set up button listeners
        scanButton.setOnClickListener { toggleScanning() }
        snapshotButton.setOnClickListener { takeSnapshot() }
        resetButton.setOnClickListener { resetScan() }
        settingsButton.setOnClickListener { openSettings() }
        snapshotsButton.setOnClickListener { openSnapshotGallery() }
        
        // Set initial state
        updateUI()
    }
    
    private fun checkPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            initializeApplication()
        }
    }
    
    private fun initializeApplication() {
        lifecycleScope.launch {
            showLoading("Initializing sensors...")
            
            try {
                // Initialize core modules
                dataAcquisition = DataAcquisitionModule(this@MainActivity)
                slamProcessor = SLAMProcessor()
                reconstructionModule = ReconstructionModule()
                storageManager = SnapshotStorageManager(this@MainActivity)
                backgroundStorageProcessor = BackgroundStorageProcessor(this@MainActivity, storageManager)
                
                // Check sensor availability
                val sensorStatus = dataAcquisition.checkSensorAvailability()
                updateSensorStatusIcons(sensorStatus)
                
                if (!sensorStatus.anyAvailable) {
                    showError("No environmental sensors available")
                    return@launch
                }
                
                // Set up data flows
                setupDataFlows()
                
                hideLoading()
                updateStatus("Ready to scan")
                
                Log.d(TAG, "Application initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing application", e)
                hideLoading()
                showError("Initialization failed: " + e.message)
            }
        }
    }
    
    private fun setupDataFlows() {
        // Connect SLAM processor to data acquisition
        lifecycleScope.launch {
            slamProcessor.slamState.collect { slamState ->
                landmarkCount = slamState.landmarks.size
                currentDevicePose = slamState.devicePose
                
                // Update trajectory
                if (deviceTrajectory.isEmpty() || 
                    calculateDistance(deviceTrajectory.last().position, slamState.devicePose.position) > 0.1f) {
                    deviceTrajectory.add(slamState.devicePose)
                }
                
                // Update visualization
                visualizationView.updateDevicePose(slamState.devicePose)
                visualizationView.updateTrajectory(deviceTrajectory)
                
                runOnUiThread { updateUI() }
            }
        }
        
        // Connect reconstruction module to SLAM
        lifecycleScope.launch {
            reconstructionModule.pointCloud.collect { pointCloud ->
                visualizationView.updatePointCloud(pointCloud)
            }
        }
        
        // Monitor ranging measurements for statistics
        lifecycleScope.launch {
            dataAcquisition.rangingMeasurements.collect { measurements ->
                measurementCount += measurements.size
                runOnUiThread { updateUI() }
            }
        }
    }
    
    private fun toggleScanning() {
        if (isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }
    
    private fun startScanning() {
        lifecycleScope.launch {
            try {
                showLoading("Starting environmental scan...")
                
                // Start data acquisition
                dataAcquisition.startAcquisition()
                
                // Start SLAM processing
                slamProcessingJob = slamProcessor.startProcessing(
                    dataAcquisition.imuMeasurements,
                    dataAcquisition.rangingMeasurements
                )
                
                // Start 3D reconstruction
                reconstructionModule.startReconstruction(slamProcessor.slamState)
                
                isScanning = true
                hideLoading()
                updateStatus("Scanning environment...")
                updateUI()
                
                Toast.makeText(this@MainActivity, "Environmental scan started", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Environmental scanning started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting scan", e)
                hideLoading()
                showError("Failed to start scan: " + e.message)
            }
        }
    }
    
    private fun stopScanning() {
        lifecycleScope.launch {
            try {
                showLoading("Stopping scan...")
                
                // Stop all processing
                dataAcquisition.stopAcquisition()
                slamProcessor.stopProcessing()
                reconstructionModule.stopReconstruction()
                
                slamProcessingJob?.cancel()
                slamProcessingJob = null
                
                isScanning = false
                hideLoading()
                updateStatus("Scan stopped")
                updateUI()
                
                Toast.makeText(this@MainActivity, "Environmental scan stopped", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Environmental scanning stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping scan", e)
                showError("Error stopping scan: " + e.message)
            }
        }
    }
    
    private fun takeSnapshot() {
        if (currentDevicePose == null) {
            Toast.makeText(this, "No scan data available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show dialog to get snapshot name
        val dialogView = layoutInflater.inflate(R.layout.dialog_snapshot_name, null)
        val textInputLayout = dialogView.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = dialogView.findViewById<TextInputEditText>(R.id.nameEditText)
        
        editText.setText("Environmental Scan ${Date()}")
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Save Environmental Snapshot")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = editText.text?.toString() ?: "Unnamed Snapshot"
                saveSnapshot(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun saveSnapshot(name: String) {
        lifecycleScope.launch {
            try {
                showLoading("Saving snapshot...")
                
                // Create snapshot data
                val snapshot = EnvironmentalSnapshot(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    devicePose = currentDevicePose!!,
                    pointCloud = slamProcessor.getCurrentMap(),
                    rangingMeasurements = emptyList(), // Could be accumulated if needed
                    imuData = emptyList(), // Could be accumulated if needed
                    metadata = mapOf(
                        "title" to name,
                        "description" to "Environmental scan from ${Date()}",
                        "trajectory" to deviceTrajectory,
                        "landmarkCount" to landmarkCount,
                        "measurementCount" to measurementCount
                    )
                )
                
                // Use background processor to prevent memory crashes
                backgroundStorageProcessor.saveSnapshotAsync(
                    snapshot = snapshot,
                    onProgress = { progress ->
                        runOnUiThread {
                            // Update progress if we have a progress indicator
                            Log.d(TAG, "Save progress: ${(progress * 100).toInt()}%")
                        }
                    },
                    onComplete = { success ->
                        runOnUiThread {
                            hideLoading()
                            
                            if (success) {
                                Toast.makeText(this@MainActivity, "Snapshot saved successfully", Toast.LENGTH_SHORT).show()
                                Log.d(TAG, "Snapshot saved: ${snapshot.id}")
                            } else {
                                showError("Failed to save snapshot - may be too large for available memory")
                            }
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving snapshot", e)
                hideLoading()
                showError("Error saving snapshot: " + e.message)
            }
        }
    }
    
    private fun resetScan() {
        lifecycleScope.launch {
            try {
                showLoading("Resetting scan data...")
                
                // Stop scanning if active
                if (isScanning) {
                    dataAcquisition.stopAcquisition()
                    slamProcessor.stopProcessing()
                    reconstructionModule.stopReconstruction()
                    isScanning = false
                }
                
                // Reset processors
                slamProcessor.reset()
                
                // Clear visualization
                visualizationView.updatePointCloud(emptyList())
                visualizationView.updateTrajectory(emptyList())
                visualizationView.resetCamera()
                
                // Reset statistics
                landmarkCount = 0
                measurementCount = 0
                currentDevicePose = null
                deviceTrajectory.clear()
                
                hideLoading()
                updateStatus("Ready to scan")
                updateUI()
                
                Toast.makeText(this@MainActivity, "Scan data reset", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Scan data reset")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting scan", e)
                showError("Error resetting scan: " + e.message)
            }
        }
    }
    
    private fun openSettings() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Environmental Imaging Settings")
            .setItems(arrayOf(
                "Sensor Configuration",
                "Storage Settings", 
                "3D Visualization",
                "SLAM Parameters",
                "Export Data",
                "Clear Cache"
            )) { _, which ->
                when (which) {
                    0 -> showSensorSettings()
                    1 -> showStorageSettings()
                    2 -> show3DSettings()
                    3 -> showSLAMSettings()
                    4 -> exportData()
                    5 -> clearCache()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun openSnapshotGallery() {
        lifecycleScope.launch {
            try {
                showLoading("Loading snapshots...")
                
                // Get list of available snapshots
                val snapshots = storageManager.listSnapshots()
                
                hideLoading()
                
                if (snapshots.isEmpty()) {
                    Toast.makeText(this@MainActivity, "No snapshots available", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // Create snapshot list for dialog
                val snapshotNames = snapshots.map { snapshot ->
                    "${snapshot.id} - ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(snapshot.timestamp))}"
                }.toTypedArray()
                
                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Environmental Snapshots (${snapshots.size})")
                    .setItems(snapshotNames) { _, which ->
                        val selectedSnapshotInfo = snapshots[which]
                        // Load full snapshot for details
                        lifecycleScope.launch {
                            try {
                                val fullSnapshot = storageManager.loadSnapshot(selectedSnapshotInfo.id)
                                fullSnapshot?.let { showSnapshotDetails(it) }
                                    ?: showError("Could not load snapshot details")
                            } catch (e: Exception) {
                                showError("Error loading snapshot: ${e.message}")
                            }
                        }
                    }
                    .setNeutralButton("Clear All") { _, _ ->
                        confirmClearAllSnapshots()
                    }
                    .setNegativeButton("Close", null)
                    .show()
                    
            } catch (e: Exception) {
                hideLoading()
                showError("Error loading snapshots: " + e.message)
            }
        }
    }
    
    private fun updateUI() {
        scanButton.text = if (isScanning) "Stop Scan" else "Start Scan"
        scanButton.icon = ContextCompat.getDrawable(
            this, 
            if (isScanning) R.drawable.ic_stop else R.drawable.ic_play_arrow
        )
        
        snapshotButton.isEnabled = !isScanning && currentDevicePose != null
        
        landmarkCountText.text = "Landmarks: $landmarkCount"
        measurementCountText.text = "Measurements: $measurementCount"
    }
    
    private fun updateStatus(status: String) {
        statusText.text = status
    }
    
    private fun updateSensorStatusIcons(sensorStatus: com.environmentalimaging.app.sensors.SensorAvailability) {
        val activeColor = ContextCompat.getColor(this, R.color.status_active)
        val inactiveColor = ContextCompat.getColor(this, R.color.status_inactive)
        
        wifiStatusIcon.setColorFilter(if (sensorStatus.wifiRtt) activeColor else inactiveColor)
        bluetoothStatusIcon.setColorFilter(if (sensorStatus.bluetooth) activeColor else inactiveColor)
        acousticStatusIcon.setColorFilter(if (sensorStatus.acoustic) activeColor else inactiveColor)
        imuStatusIcon.setColorFilter(if (sensorStatus.imu) activeColor else inactiveColor)
    }
    
    private fun showLoading(message: String) {
        loadingText.text = message
        loadingOverlay.visibility = View.VISIBLE
    }
    
    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }
    
    private fun showPermissionError() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("This app requires location, audio, and Bluetooth permissions to function properly.")
            .setPositiveButton("Grant Permissions") { _, _ ->
                checkPermissions()
            }
            .setNegativeButton("Exit") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun calculateDistance(point1: Point3D, point2: Point3D): Float {
        val dx = point1.x - point2.x
        val dy = point1.y - point2.y
        val dz = point1.z - point2.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    override fun onResume() {
        super.onResume()
        visualizationView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        visualizationView.onPause()
        
        // Stop scanning if active to save battery
        if (isScanning) {
            stopScanning()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up resources
        lifecycleScope.launch {
            try {
                if (::dataAcquisition.isInitialized) {
                    dataAcquisition.stopAcquisition()
                }
                if (::slamProcessor.isInitialized) {
                    slamProcessor.stopProcessing()
                }
                if (::reconstructionModule.isInitialized) {
                    reconstructionModule.stopReconstruction()
                }
                if (::backgroundStorageProcessor.isInitialized) {
                    backgroundStorageProcessor.shutdown()
                }
                
                slamProcessingJob?.cancel()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            }
        }
        
        Log.d(TAG, "MainActivity destroyed")
    }
    
    // Settings Functions
    private fun showSensorSettings() {
        val sensorStatus = dataAcquisition.checkSensorAvailability()
        MaterialAlertDialogBuilder(this)
            .setTitle("Sensor Configuration")
            .setMessage("Current sensors:\n" +
                "• WiFi RTT: ${if(sensorStatus.wifiRtt) "Available" else "Unavailable"}\n" +
                "• Bluetooth: ${if(sensorStatus.bluetooth) "Available" else "Unavailable"}\n" +
                "• Acoustic: ${if(sensorStatus.acoustic) "Available" else "Unavailable"}\n" +
                "• IMU: ${if(sensorStatus.imu) "Available" else "Unavailable"}")
            .setPositiveButton("Refresh") { _, _ ->
                // Refresh sensor availability and show updated dialog
                showSensorSettings()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun showStorageSettings() {
        lifecycleScope.launch {
            val snapshots = storageManager.listSnapshots()
            val storageInfo = "Snapshots: ${snapshots.size}\nStorage location: Internal storage"
            
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Storage Settings")
                .setMessage(storageInfo)
                .setPositiveButton("Clear All Data") { _, _ ->
                    confirmClearAllSnapshots()
                }
                .setNegativeButton("Close", null)
                .show()
        }
    }
    
    private fun show3DSettings() {
        Toast.makeText(this, "3D visualization settings coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSLAMSettings() {
        Toast.makeText(this, "SLAM parameter tuning coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun exportData() {
        Toast.makeText(this, "Data export feature coming soon", Toast.LENGTH_SHORT).show()
    }
    
    private fun clearCache() {
        lifecycleScope.launch {
            try {
                // Clear any cached data
                Toast.makeText(this@MainActivity, "Cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Error clearing cache: " + e.message)
            }
        }
    }
    
    private fun showSnapshotDetails(snapshot: EnvironmentalSnapshot) {
        val details = "Snapshot Details:\n\n" +
                "ID: ${snapshot.id}\n" +
                "Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(snapshot.timestamp))}\n" +
                "Point Cloud: ${snapshot.pointCloud.size} points\n" +
                "Ranging Data: ${snapshot.rangingMeasurements.size} measurements\n" +
                "IMU Data: ${snapshot.imuData.size} readings"
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Environmental Snapshot")
            .setMessage(details)
            .setPositiveButton("Load") { _, _ ->
                // TODO: Load and visualize this snapshot
                Toast.makeText(this, "Loading snapshot...", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Delete") { _, _ ->
                confirmDeleteSnapshot(snapshot.id)
            }
            .setNegativeButton("Close", null)
            .show()
    }
    
    private fun confirmClearAllSnapshots() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear All Snapshots")
            .setMessage("This will permanently delete all environmental snapshots. Continue?")
            .setPositiveButton("Delete All") { _, _ ->
                lifecycleScope.launch {
                    try {
                        showLoading("Clearing snapshots...")
                        storageManager.clearAllSnapshots()
                        hideLoading()
                        Toast.makeText(this@MainActivity, "All snapshots cleared", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        hideLoading()
                        showError("Error clearing snapshots: " + e.message)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDeleteSnapshot(snapshotId: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Snapshot")
            .setMessage("Delete snapshot $snapshotId?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        storageManager.deleteSnapshot(snapshotId)
                        Toast.makeText(this@MainActivity, "Snapshot deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        showError("Error deleting snapshot: " + e.message)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}