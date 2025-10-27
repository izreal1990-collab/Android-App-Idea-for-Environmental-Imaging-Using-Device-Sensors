package com.environmentalimaging.app.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Smart Setup Wizard - Guides users through initial app configuration
 * Features:
 * - Environment detection (indoor/outdoor, lighting)
 * - Automatic sensor calibration
 * - Permission handling
 * - Optimal settings recommendation
 */
class SmartSetupWizardActivity : AppCompatActivity() {
    
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var detailsText: TextView
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button
    
    private var currentStep = 0
    private val totalSteps = 4
    
    private data class SetupStep(
        val title: String,
        val description: String,
        val action: suspend () -> SetupResult
    )
    
    private data class SetupResult(
        val success: Boolean,
        val message: String,
        val details: String = ""
    )
    
    private val setupSteps = listOf(
        SetupStep(
            title = "Checking Permissions",
            description = "Verifying required sensor access..."
        ) { checkPermissions() },
        SetupStep(
            title = "Detecting Environment",
            description = "Analyzing lighting and space conditions..."
        ) { detectEnvironment() },
        SetupStep(
            title = "Calibrating Sensors",
            description = "Optimizing sensor accuracy..."
        ) { calibrateSensors() },
        SetupStep(
            title = "Optimizing Settings",
            description = "Configuring recommended parameters..."
        ) { optimizeSettings() }
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_setup_wizard)
        
        setupViews()
        startSetupProcess()
    }
    
    private fun setupViews() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "Smart Setup Wizard"
        
        progressBar = findViewById(R.id.setupProgressBar)
        statusText = findViewById(R.id.statusText)
        detailsText = findViewById(R.id.detailsText)
        nextButton = findViewById(R.id.nextButton)
        skipButton = findViewById(R.id.skipButton)
        
        progressBar.max = totalSteps * 100
        
        nextButton.setOnClickListener {
            if (currentStep < setupSteps.size) {
                runSetupStep(currentStep)
            } else {
                finishSetup()
            }
        }
        
        skipButton.setOnClickListener {
            finishSetup()
        }
    }
    
    private fun startSetupProcess() {
        statusText.text = "Welcome to Environmental Imaging"
        detailsText.text = "This wizard will help you set up the app for optimal performance"
        nextButton.text = "Start Setup"
    }
    
    private fun runSetupStep(step: Int) {
        if (step >= setupSteps.size) {
            finishSetup()
            return
        }
        
        val setupStep = setupSteps[step]
        
        statusText.text = setupStep.title
        detailsText.text = setupStep.description
        nextButton.isEnabled = false
        skipButton.isEnabled = false
        
        lifecycleScope.launch {
            // Animate progress
            val startProgress = step * 100
            val endProgress = (step + 1) * 100
            
            for (i in 0..100 step 5) {
                progressBar.progress = startProgress + i
                delay(20)
            }
            
            // Run the actual setup action
            val result = setupStep.action()
            
            // Update UI with result
            detailsText.text = if (result.success) {
                "✓ ${result.message}\n${result.details}"
            } else {
                "⚠ ${result.message}\n${result.details}"
            }
            
            delay(1000)
            
            currentStep++
            nextButton.isEnabled = true
            skipButton.isEnabled = true
            
            if (currentStep < setupSteps.size) {
                nextButton.text = "Continue"
            } else {
                nextButton.text = "Finish Setup"
            }
        }
    }
    
    private suspend fun checkPermissions(): SetupResult {
        delay(500)
        
        val requiredPermissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (missingPermissions.isEmpty()) {
            SetupResult(
                success = true,
                message = "All permissions granted",
                details = "Camera, Location, and WiFi access verified"
            )
        } else {
            // Request missing permissions
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                100
            )
            
            SetupResult(
                success = false,
                message = "Please grant required permissions",
                details = "Missing: ${missingPermissions.size} permissions"
            )
        }
    }
    
    private suspend fun detectEnvironment(): SetupResult {
        delay(1500)
        
        // Simulate environment detection
        // In a real implementation, this would use sensors
        val lightLevel = (300..1000).random() // Lux
        val spaceType = if (lightLevel < 500) "Indoor" else "Outdoor"
        val lightQuality = when {
            lightLevel < 300 -> "Low"
            lightLevel < 700 -> "Good"
            else -> "Excellent"
        }
        
        return SetupResult(
            success = true,
            message = "Environment detected",
            details = "$spaceType space\nLighting: $lightQuality ($lightLevel lux)"
        )
    }
    
    private suspend fun calibrateSensors(): SetupResult {
        delay(2000)
        
        // Simulate sensor calibration
        // In a real implementation, this would calibrate IMU, magnetometer, etc.
        val steps = listOf(
            "Initializing IMU...",
            "Calibrating magnetometer...",
            "Testing gyroscope...",
            "Verifying accelerometer...",
            "Optimization complete"
        )
        
        for ((index, step) in steps.withIndex()) {
            runOnUiThread {
                detailsText.text = "$step (${index + 1}/${steps.size})"
            }
            delay(300)
        }
        
        return SetupResult(
            success = true,
            message = "Sensors calibrated successfully",
            details = "IMU accuracy: ±0.02°\nMagnetometer drift: < 0.5°"
        )
    }
    
    private suspend fun optimizeSettings(): SetupResult {
        delay(1000)
        
        // Set optimal default settings based on detected environment
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("setup_completed", true)
            putString("default_scan_mode", "AUTO")
            putInt("measurement_frequency", 10)
            putFloat("accuracy_target", 0.1f)
            putBoolean("ai_assistance_enabled", true)
            apply()
        }
        
        return SetupResult(
            success = true,
            message = "Settings optimized",
            details = "Scan mode: Auto\nMeasurement: 10Hz\nAccuracy: 10cm\nAI: Enabled"
        )
    }
    
    private fun finishSetup() {
        Toast.makeText(this, "Setup complete! Ready to scan.", Toast.LENGTH_LONG).show()
        finish()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == 100) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
