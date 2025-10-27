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

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.data.ScanSession
import com.environmentalimaging.app.data.SettingsManager
import com.environmentalimaging.app.visualization.Enhanced3DVisualizationEngine
import com.environmentalimaging.app.visualization.EnvironmentalVisualizationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Activity for enhanced 3D visualization with advanced rendering features
 * Provides interactive controls for camera, point cloud display, and visualization settings
 */
class Enhanced3DVisualizationActivity : AppCompatActivity() {
    
    private lateinit var visualizationView: EnvironmentalVisualizationView
    private lateinit var controlsPanel: View
    private lateinit var pointCountText: TextView
    private lateinit var resetCameraButton: MaterialButton
    private lateinit var toggleControlsButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    
    private var scanSession: ScanSession? = null
    private var controlsVisible = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_3d_visualization)
        
        // Initialize SettingsManager
        SettingsManager.initialize(this)
        
        // Load scan session data
        scanSession = intent.getParcelableExtra("scan_session")
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = scanSession?.id ?: "3D Visualization"
        
        initializeUI()
        loadScanData()
        observeSettings()
    }
    
    private fun initializeUI() {
        visualizationView = findViewById(R.id.visualizationView)
        controlsPanel = findViewById(R.id.controlsPanel)
        pointCountText = findViewById(R.id.pointCountText)
        resetCameraButton = findViewById(R.id.resetCameraButton)
        toggleControlsButton = findViewById(R.id.toggleControlsButton)
        settingsButton = findViewById(R.id.settingsButton)
        
        resetCameraButton.setOnClickListener {
            visualizationView.resetCamera()
        }
        
        toggleControlsButton.setOnClickListener {
            toggleControls()
        }
        
        settingsButton.setOnClickListener {
            showVisualizationSettings()
        }
    }
    
    private fun loadScanData() {
        scanSession?.let { session ->
            // Update visualization with scan data
            visualizationView.updatePointCloud(session.dataPoints)
            visualizationView.updateTrajectory(session.trajectory)
            
            // Update point count display
            pointCountText.text = "${session.dataPoints.size} points"
        }
    }
    
    private fun observeSettings() {
        lifecycleScope.launch {
            SettingsManager.visualizationSettings.collect { settings ->
                // Apply settings to visualization
                applyVisualizationSettings(settings)
            }
        }
    }
    
    private fun applyVisualizationSettings(settings: SettingsManager.VisualizationSettings) {
        // Settings are applied through the renderer
        // The renderer accesses SettingsManager directly for real-time updates
        visualizationView.requestRender()
    }
    
    private fun toggleControls() {
        controlsVisible = !controlsVisible
        controlsPanel.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        toggleControlsButton.text = if (controlsVisible) "Hide Controls" else "Show Controls"
    }
    
    private fun showVisualizationSettings() {
        val currentSettings = SettingsManager.visualizationSettings.value
        val view = layoutInflater.inflate(R.layout.dialog_visualization_settings, null)
        
        // Point size slider
        val pointSizeSlider = view.findViewById<Slider>(R.id.pointSizeSlider)
        val pointSizeValue = view.findViewById<TextView>(R.id.pointSizeValue)
        pointSizeSlider.value = currentSettings.pointSize
        pointSizeValue.text = String.format("%.1f", currentSettings.pointSize)
        pointSizeSlider.addOnChangeListener { _, value, _ ->
            pointSizeValue.text = String.format("%.1f", value)
        }
        
        // Camera sensitivity slider
        val cameraSensitivitySlider = view.findViewById<Slider>(R.id.cameraSensitivitySlider)
        val cameraSensitivityValue = view.findViewById<TextView>(R.id.cameraSensitivityValue)
        cameraSensitivitySlider.value = currentSettings.cameraSensitivity
        cameraSensitivityValue.text = String.format("%.1f", currentSettings.cameraSensitivity)
        cameraSensitivitySlider.addOnChangeListener { _, value, _ ->
            cameraSensitivityValue.text = String.format("%.1f", value)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("3D Visualization Settings")
            .setView(view)
            .setPositiveButton("Apply") { _, _ ->
                val newSettings = currentSettings.copy(
                    pointSize = pointSizeSlider.value,
                    cameraSensitivity = cameraSensitivitySlider.value
                )
                SettingsManager.updateVisualizationSettings(newSettings)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_3d_visualization, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_color_mode -> {
                showColorModeDialog()
                true
            }
            R.id.action_toggle_grid -> {
                toggleGrid()
                true
            }
            R.id.action_toggle_axes -> {
                toggleAxes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showColorModeDialog() {
        val currentSettings = SettingsManager.visualizationSettings.value
        val colorModes = SettingsManager.ColorMode.values()
        val currentIndex = colorModes.indexOf(currentSettings.colorMode)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Point Cloud Color Mode")
            .setSingleChoiceItems(
                colorModes.map { it.name }.toTypedArray(),
                currentIndex
            ) { dialog, which ->
                val newSettings = currentSettings.copy(colorMode = colorModes[which])
                SettingsManager.updateVisualizationSettings(newSettings)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun toggleGrid() {
        val currentSettings = SettingsManager.visualizationSettings.value
        val newSettings = currentSettings.copy(showGrid = !currentSettings.showGrid)
        SettingsManager.updateVisualizationSettings(newSettings)
    }
    
    private fun toggleAxes() {
        val currentSettings = SettingsManager.visualizationSettings.value
        val newSettings = currentSettings.copy(showAxes = !currentSettings.showAxes)
        SettingsManager.updateVisualizationSettings(newSettings)
    }
    
    override fun onResume() {
        super.onResume()
        visualizationView.onResume()
    }
    
    override fun onPause() {
        super.onPause()
        visualizationView.onPause()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
