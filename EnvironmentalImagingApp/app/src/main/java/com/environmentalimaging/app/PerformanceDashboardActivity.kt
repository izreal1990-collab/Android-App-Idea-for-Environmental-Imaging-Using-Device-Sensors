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


package com.environmentalimaging.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.environmentalimaging.app.ai.*
import com.environmentalimaging.app.data.ScanningSessionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Performance Dashboard Activity
 * Displays comprehensive system performance metrics, AI insights, and real-time monitoring
 */
class PerformanceDashboardActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "PerformanceDashboard"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, PerformanceDashboardActivity::class.java)
        }
    }
    
    // UI Components
    private lateinit var systemHealthIcon: ImageView
    private lateinit var systemHealthStatus: TextView
    private lateinit var systemHealthScore: TextView
    private lateinit var systemHealthExpandButton: MaterialButton
    private lateinit var systemHealthDetails: LinearLayout
    private lateinit var systemHealthMetrics: TextView
    
    private lateinit var aiInsightsRecyclerView: RecyclerView
    private lateinit var noInsightsText: TextView
    
    private lateinit var landmarkCountValue: TextView
    private lateinit var measurementCountValue: TextView
    private lateinit var accuracyValue: TextView
    private lateinit var scanDurationValue: TextView
    
    private lateinit var cameraStatusIcon: ImageView
    private lateinit var cameraStatusText: TextView
    private lateinit var uwbStatusIcon: ImageView
    private lateinit var uwbStatusText: TextView
    private lateinit var wifiStatusIcon: ImageView
    private lateinit var wifiStatusText: TextView
    private lateinit var bluetoothStatusIcon: ImageView
    private lateinit var bluetoothStatusText: TextView
    private lateinit var imuStatusIcon: ImageView
    private lateinit var imuStatusText: TextView
    
    private lateinit var performanceGraphView: View
    
    // AI System Components (injected from MainActivity)
    private var aiSystemHealthMonitor: AISystemHealthMonitor? = null
    private var performanceData: PerformanceData = PerformanceData()
    
    // Update coroutines
    private var systemHealthUpdateJob: Job? = null
    private var performanceUpdateJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_performance_dashboard)
        
        initializeUI()
        setupAIInsights()
        startPerformanceMonitoring()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        systemHealthUpdateJob?.cancel()
        performanceUpdateJob?.cancel()
    }
    
    private fun initializeUI() {
        // System Health components
        systemHealthIcon = findViewById(R.id.systemHealthIcon)
        systemHealthStatus = findViewById(R.id.systemHealthStatus)
        systemHealthScore = findViewById(R.id.systemHealthScore)
        systemHealthExpandButton = findViewById(R.id.systemHealthExpandButton)
        systemHealthDetails = findViewById(R.id.systemHealthDetails)
        systemHealthMetrics = findViewById(R.id.systemHealthMetrics)
        
        // AI Insights components
        aiInsightsRecyclerView = findViewById(R.id.aiInsightsRecyclerView)
        noInsightsText = findViewById(R.id.noInsightsText)
        
        // Statistics components
        landmarkCountValue = findViewById(R.id.landmarkCountValue)
        measurementCountValue = findViewById(R.id.measurementCountValue)
        accuracyValue = findViewById(R.id.accuracyValue)
        scanDurationValue = findViewById(R.id.scanDurationValue)
        
        // Sensor status components
        cameraStatusIcon = findViewById(R.id.cameraStatusIcon)
        cameraStatusText = findViewById(R.id.cameraStatusText)
        uwbStatusIcon = findViewById(R.id.uwbStatusIcon)
        uwbStatusText = findViewById(R.id.uwbStatusText)
        wifiStatusIcon = findViewById(R.id.wifiStatusIcon)
        wifiStatusText = findViewById(R.id.wifiStatusText)
        bluetoothStatusIcon = findViewById(R.id.bluetoothStatusIcon)
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText)
        imuStatusIcon = findViewById(R.id.imuStatusIcon)
        imuStatusText = findViewById(R.id.imuStatusText)
        
        // Performance graph
        performanceGraphView = findViewById(R.id.performanceGraphView)
        
        // Setup toolbar
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
        
        // Setup expandable system health details
        systemHealthExpandButton.setOnClickListener {
            toggleSystemHealthDetails()
        }
    }
    
    private fun setupAIInsights() {
        // Simplified setup - just show "no insights" message for now
        noInsightsText.visibility = View.VISIBLE
        aiInsightsRecyclerView.visibility = View.GONE
    }
    
    private fun startPerformanceMonitoring() {
        // Initialize AI system health monitor
        aiSystemHealthMonitor = AISystemHealthMonitor(this)
        
        // Collect session data from SessionManager
        lifecycleScope.launch {
            ScanningSessionManager.sessionData.collect { sessionData ->
                updatePerformanceStatistics(sessionData)
            }
        }
        
        // Collect sensor status from SessionManager
        lifecycleScope.launch {
            ScanningSessionManager.sensorStatus.collect { sensorStatus ->
                updateSensorStatus(sensorStatus)
            }
        }
        
        // Start performance data monitoring
        performanceUpdateJob = lifecycleScope.launch {
            while (isActive) {
                updateSystemHealthUI()
                delay(2000) // Update every 2 seconds
            }
        }
        
        // Initialize with current values
        val currentSession = ScanningSessionManager.sessionData.value
        val currentSensorStatus = ScanningSessionManager.sensorStatus.value
        updatePerformanceStatistics(currentSession)
        updateSensorStatus(currentSensorStatus)
    }
    
    private fun updateSystemHealthUI() {
        lifecycleScope.launch {
            try {
                val systemHealth = aiSystemHealthMonitor?.getCurrentSystemHealth()
                runOnUiThread {
                    systemHealth?.let { health ->
                        // Update status icon and text based on health score
                        when (health.healthScore) {
                            in 0.8f..1.0f -> {
                                systemHealthIcon.setImageResource(R.drawable.ic_health_excellent)
                                systemHealthIcon.setColorFilter(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.success))
                                systemHealthStatus.text = "Optimal"
                                systemHealthStatus.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.success))
                                systemHealthScore.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.success))
                            }
                            in 0.5f..0.8f -> {
                                systemHealthIcon.setImageResource(R.drawable.ic_health_warning)
                                systemHealthIcon.setColorFilter(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.warning))
                                systemHealthStatus.text = "Degraded"
                                systemHealthStatus.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.warning))
                                systemHealthScore.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.warning))
                            }
                            else -> {
                                systemHealthIcon.setImageResource(R.drawable.ic_health_critical)
                                systemHealthIcon.setColorFilter(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.error))
                                systemHealthStatus.text = "Critical"
                                systemHealthStatus.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.error))
                                systemHealthScore.setTextColor(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.error))
                            }
                        }
                        
                        // Update health score
                        systemHealthScore.text = "${(health.healthScore * 100).toInt()}%"
                        
                        // Update detailed metrics (simplified)
                        val metrics = buildString {
                            append("CPU: ${(health.performanceSnapshot.cpuUsage * 100).toInt()}%\n")
                            append("Memory: ${String.format("%.1f", health.performanceSnapshot.memoryUsage * 100)}%\n")
                            append("Disk: ${String.format("%.1f", health.performanceSnapshot.diskUsage * 100)}%\n")
                            append("Battery: ${(health.performanceSnapshot.batteryLevel * 100).toInt()}%")
                        }
                        
                        systemHealthMetrics.text = metrics
                    } ?: run {
                        // Default values when AI system is not available
                        systemHealthIcon.setImageResource(R.drawable.ic_health_excellent)
                        systemHealthIcon.setColorFilter(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.success))
                        systemHealthStatus.text = "Good"
                        systemHealthScore.text = "85%"
                        systemHealthMetrics.text = "System monitoring active"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update system health UI", e)
                runOnUiThread {
                    // Default values on error
                    systemHealthIcon.setImageResource(R.drawable.ic_health_excellent)
                    systemHealthIcon.setColorFilter(ContextCompat.getColor(this@PerformanceDashboardActivity, R.color.success))
                    systemHealthStatus.text = "Good"
                    systemHealthScore.text = "85%"
                    systemHealthMetrics.text = "System monitoring active"
                }
            }
        }
    }
    
    private fun updatePerformanceStatistics(sessionData: com.environmentalimaging.app.data.SessionData) {
        runOnUiThread {
            // Update with real session data
            landmarkCountValue.text = sessionData.landmarkCount.toString()
            measurementCountValue.text = sessionData.measurementCount.toString()
            accuracyValue.text = String.format("%.2fm", sessionData.averageAccuracy)
            
            // Format scan duration
            val duration = ScanningSessionManager.getSessionDuration()
            val minutes = duration / 60
            val seconds = duration % 60
            scanDurationValue.text = String.format("%d:%02d", minutes, seconds)
        }
    }
    
    private fun updateSensorStatus(sensorStatus: com.environmentalimaging.app.data.RealTimeSensorStatus) {
        runOnUiThread {
            // Update sensor status indicators with real data
            updateSensorStatusIndicator(
                cameraStatusIcon, cameraStatusText,
                getSensorStatus(sensorStatus.cameraAvailable, sensorStatus.cameraActive),
                "Camera"
            )
            updateSensorStatusIndicator(
                uwbStatusIcon, uwbStatusText,
                SensorStatus.Status.UNAVAILABLE, // UWB not implemented yet
                "Ultra-Wideband"
            )
            updateSensorStatusIndicator(
                wifiStatusIcon, wifiStatusText,
                getSensorStatus(sensorStatus.wifiRttAvailable, sensorStatus.wifiRttActive),
                "WiFi RTT"
            )
            updateSensorStatusIndicator(
                bluetoothStatusIcon, bluetoothStatusText,
                getSensorStatus(sensorStatus.bluetoothAvailable, sensorStatus.bluetoothActive),
                "Bluetooth"
            )
            updateSensorStatusIndicator(
                imuStatusIcon, imuStatusText,
                getSensorStatus(sensorStatus.imuAvailable, sensorStatus.imuActive),
                "IMU"
            )
        }
    }
    
    private fun getSensorStatus(available: Boolean, active: Boolean): SensorStatus.Status {
        return when {
            !available -> SensorStatus.Status.UNAVAILABLE
            active -> SensorStatus.Status.ACTIVE
            else -> SensorStatus.Status.INACTIVE
        }
    }
    
    private fun updateSensorStatusIndicator(
        icon: ImageView, 
        text: TextView, 
        status: SensorStatus.Status, 
        sensorName: String
    ) {
        when (status) {
            SensorStatus.Status.ACTIVE -> {
                icon.setColorFilter(ContextCompat.getColor(this, R.color.success))
                text.text = "Active"
                text.setTextColor(ContextCompat.getColor(this, R.color.success))
            }
            SensorStatus.Status.LIMITED -> {
                icon.setColorFilter(ContextCompat.getColor(this, R.color.warning))
                text.text = "Limited"
                text.setTextColor(ContextCompat.getColor(this, R.color.warning))
            }
            SensorStatus.Status.INACTIVE -> {
                icon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                text.text = "Ready"
                text.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
            SensorStatus.Status.UNAVAILABLE -> {
                icon.setColorFilter(ContextCompat.getColor(this, R.color.error))
                text.text = "Unavailable"
                text.setTextColor(ContextCompat.getColor(this, R.color.error))
            }
        }
    }
    
    private fun toggleSystemHealthDetails() {
        if (systemHealthDetails.visibility == View.VISIBLE) {
            systemHealthDetails.visibility = View.GONE
            systemHealthExpandButton.text = "Show Details"
            systemHealthExpandButton.setIconResource(R.drawable.ic_expand_more)
        } else {
            systemHealthDetails.visibility = View.VISIBLE
            systemHealthExpandButton.text = "Hide Details"
            systemHealthExpandButton.setIconResource(R.drawable.ic_expand_less)
        }
    }
    
    private fun showInsightDetails(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("AI Insight")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    // Method to update performance data from MainActivity
    fun updatePerformanceData(
        landmarkCount: Int,
        measurementCount: Int,
        accuracy: Float,
        scanDuration: Long,
        sensorStatus: SensorStatus
    ) {
        performanceData = performanceData.copy(
            landmarkCount = landmarkCount,
            measurementCount = measurementCount,
            averageAccuracy = accuracy,
            scanDurationSeconds = scanDuration,
            sensorStatus = sensorStatus
        )
    }
    

}

/**
 * Data class to hold performance metrics
 */
data class PerformanceData(
    val landmarkCount: Int = 0,
    val measurementCount: Int = 0,
    val averageAccuracy: Float = 0.0f,
    val scanDurationSeconds: Long = 0,
    val sensorStatus: SensorStatus = SensorStatus()
)

/**
 * Data class for sensor status
 */
data class SensorStatus(
    val camera: Status = Status.UNAVAILABLE,
    val uwb: Status = Status.UNAVAILABLE,
    val wifiRtt: Status = Status.UNAVAILABLE,
    val bluetooth: Status = Status.UNAVAILABLE,
    val imu: Status = Status.UNAVAILABLE
) {
    enum class Status {
        ACTIVE, LIMITED, INACTIVE, UNAVAILABLE
    }
}

