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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.ai.AIAnalysisEngine
import com.environmentalimaging.app.ai.EnvironmentalAIAssistant
import com.environmentalimaging.app.scanning.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Scanning Modes Selection Activity
 * Allows users to select AI-optimized scanning modes
 */
class ScanningModesActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ScanningModesActivity"
        const val EXTRA_SELECTED_MODE = "selected_mode"
        const val EXTRA_CUSTOM_SETTINGS = "custom_settings"
        
        fun createIntent(context: Context): Intent {
            return Intent(context, ScanningModesActivity::class.java)
        }
    }
    
    // UI Components
    private lateinit var recommendationCard: MaterialCardView
    private lateinit var recommendationTitle: TextView
    private lateinit var recommendationReason: TextView
    private lateinit var recommendationDuration: TextView
    private lateinit var recommendationAccuracy: TextView
    private lateinit var acceptRecommendationButton: MaterialButton
    
    private lateinit var quickScanCard: MaterialCardView
    private lateinit var precisionScanCard: MaterialCardView
    private lateinit var autoModeCard: MaterialCardView
    private lateinit var customModeCard: MaterialCardView
    
    private lateinit var quickScanRadio: RadioButton
    private lateinit var precisionScanRadio: RadioButton
    private lateinit var autoModeRadio: RadioButton
    private lateinit var customModeRadio: RadioButton
    
    private lateinit var getRecommendationButton: MaterialButton
    private lateinit var startScanningButton: MaterialButton
    
    // Smart Scanning Components
    private lateinit var smartScanningManager: SmartScanningManager
    private var currentRecommendation: ModeRecommendation? = null
    private var selectedMode: ScanningMode = ScanningMode.AUTO
    private var customSettings: CustomScanSettings? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanning_modes)
        
        initializeUI()
        initializeSmartScanning()
        setupEventListeners()
    }
    
    private fun initializeUI() {
        // Recommendation card
        recommendationCard = findViewById(R.id.recommendationCard)
        recommendationTitle = findViewById(R.id.recommendationTitle)
        recommendationReason = findViewById(R.id.recommendationReason)
        recommendationDuration = findViewById(R.id.recommendationDuration)
        recommendationAccuracy = findViewById(R.id.recommendationAccuracy)
        acceptRecommendationButton = findViewById(R.id.acceptRecommendationButton)
        
        // Mode selection cards
        quickScanCard = findViewById(R.id.quickScanCard)
        precisionScanCard = findViewById(R.id.precisionScanCard)
        autoModeCard = findViewById(R.id.autoModeCard)
        customModeCard = findViewById(R.id.customModeCard)
        
        // Radio buttons
        quickScanRadio = findViewById(R.id.quickScanRadio)
        precisionScanRadio = findViewById(R.id.precisionScanRadio)
        autoModeRadio = findViewById(R.id.autoModeRadio)
        customModeRadio = findViewById(R.id.customModeRadio)
        
        // Action buttons
        getRecommendationButton = findViewById(R.id.getRecommendationButton)
        startScanningButton = findViewById(R.id.startScanningButton)
        
        // Setup toolbar
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }
        
        // Set default selection
        autoModeRadio.isChecked = true
        selectedMode = ScanningMode.AUTO
    }
    
    private fun initializeSmartScanning() {
        // Initialize AI components (in a real app, these would be injected or retrieved from application)
        val aiAnalysisEngine = AIAnalysisEngine(this)
        val aiAssistant = EnvironmentalAIAssistant(this)
        
        smartScanningManager = SmartScanningManager(this, aiAnalysisEngine, aiAssistant)
    }
    
    private fun setupEventListeners() {
        // Mode selection cards
        quickScanCard.setOnClickListener {
            selectMode(ScanningMode.QUICK_SCAN)
            quickScanRadio.isChecked = true
            clearOtherRadioButtons(quickScanRadio)
        }
        
        precisionScanCard.setOnClickListener {
            selectMode(ScanningMode.PRECISION_SCAN)
            precisionScanRadio.isChecked = true
            clearOtherRadioButtons(precisionScanRadio)
        }
        
        autoModeCard.setOnClickListener {
            selectMode(ScanningMode.AUTO)
            autoModeRadio.isChecked = true
            clearOtherRadioButtons(autoModeRadio)
        }
        
        customModeCard.setOnClickListener {
            selectMode(ScanningMode.CUSTOM)
            customModeRadio.isChecked = true
            clearOtherRadioButtons(customModeRadio)
            showCustomSettingsDialog()
        }
        
        // Radio button listeners
        quickScanRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectMode(ScanningMode.QUICK_SCAN)
                clearOtherRadioButtons(quickScanRadio)
            }
        }
        
        precisionScanRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectMode(ScanningMode.PRECISION_SCAN)
                clearOtherRadioButtons(precisionScanRadio)
            }
        }
        
        autoModeRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectMode(ScanningMode.AUTO)
                clearOtherRadioButtons(autoModeRadio)
            }
        }
        
        customModeRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectMode(ScanningMode.CUSTOM)
                clearOtherRadioButtons(customModeRadio)
            }
        }
        
        // Action buttons
        getRecommendationButton.setOnClickListener {
            getAIRecommendation()
        }
        
        acceptRecommendationButton.setOnClickListener {
            currentRecommendation?.let { recommendation ->
                selectMode(recommendation.recommendedMode)
                updateRadioButtonSelection(recommendation.recommendedMode)
                recommendationCard.visibility = android.view.View.GONE
            }
        }
        
        startScanningButton.setOnClickListener {
            startScanning()
        }
    }
    
    private fun selectMode(mode: ScanningMode) {
        selectedMode = mode
        updateModeSelection(mode)
    }
    
    private fun clearOtherRadioButtons(except: RadioButton) {
        val radioButtons = listOf(quickScanRadio, precisionScanRadio, autoModeRadio, customModeRadio)
        radioButtons.forEach { radio ->
            if (radio != except) {
                radio.isChecked = false
            }
        }
    }
    
    private fun updateRadioButtonSelection(mode: ScanningMode) {
        clearOtherRadioButtons(RadioButton(this)) // Clear all
        
        when (mode) {
            ScanningMode.QUICK_SCAN -> quickScanRadio.isChecked = true
            ScanningMode.PRECISION_SCAN -> precisionScanRadio.isChecked = true
            ScanningMode.AUTO -> autoModeRadio.isChecked = true
            ScanningMode.CUSTOM -> customModeRadio.isChecked = true
        }
    }
    
    private fun updateModeSelection(mode: ScanningMode) {
        // Visual feedback for selected mode
        val cards = listOf(quickScanCard, precisionScanCard, autoModeCard, customModeCard)
        val selectedCard = when (mode) {
            ScanningMode.QUICK_SCAN -> quickScanCard
            ScanningMode.PRECISION_SCAN -> precisionScanCard
            ScanningMode.AUTO -> autoModeCard
            ScanningMode.CUSTOM -> customModeCard
        }
        
        cards.forEach { card ->
            if (card == selectedCard) {
                card.strokeWidth = 4
                card.strokeColor = ContextCompat.getColor(this, R.color.primary)
            } else {
                card.strokeWidth = 0
            }
        }
    }
    
    private fun getAIRecommendation() {
        lifecycleScope.launch {
            getRecommendationButton.text = "Analyzing..."
            getRecommendationButton.isEnabled = false
            
            try {
                // Create environmental context (in a real app, this would be gathered from sensors/user input)
                val environmentalContext = EnvironmentalContext(
                    estimatedArea = 25f, // Default estimation
                    complexity = EnvironmentalComplexity.MEDIUM,
                    lightingConditions = LightingConditions.GOOD,
                    requiresHighAccuracy = false,
                    timeConstraints = TimeConstraints.MODERATE,
                    primaryUseCase = ScanUseCase.DETAILED_MAPPING
                )
                
                val recommendation = smartScanningManager.recommendScanningMode(environmentalContext)
                currentRecommendation = recommendation
                
                showRecommendation(recommendation)
                
            } catch (e: Exception) {
                Toast.makeText(this@ScanningModesActivity, "Failed to get AI recommendation", Toast.LENGTH_SHORT).show()
            } finally {
                getRecommendationButton.text = "Get AI Recommendation"
                getRecommendationButton.isEnabled = true
            }
        }
    }
    
    private fun showRecommendation(recommendation: ModeRecommendation) {
        recommendationCard.visibility = android.view.View.VISIBLE
        
        val modeTitle = when (recommendation.recommendedMode) {
            ScanningMode.QUICK_SCAN -> "Quick Scan Mode Recommended"
            ScanningMode.PRECISION_SCAN -> "Precision Scan Mode Recommended"
            ScanningMode.AUTO -> "Auto Mode Recommended"
            ScanningMode.CUSTOM -> "Custom Mode Recommended"
        }
        
        recommendationTitle.text = modeTitle
        recommendationReason.text = recommendation.reasoning
        recommendationDuration.text = "⏱ ${recommendation.estimatedDuration}"
        recommendationAccuracy.text = "🎯 ${recommendation.expectedAccuracy}"
        
        // Highlight recommended mode
        updateModeSelection(recommendation.recommendedMode)
        updateRadioButtonSelection(recommendation.recommendedMode)
        selectedMode = recommendation.recommendedMode
    }
    
    private fun showCustomSettingsDialog() {
        // Create a simple custom settings dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_scan_settings, null)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom Scan Settings")
            .setView(dialogView)
            .setPositiveButton("Apply") { _, _ ->
                // Get settings from dialog (simplified implementation)
                customSettings = CustomScanSettings(
                    name = "Custom Configuration",
                    description = "User-defined scanning parameters",
                    measurementFrequency = 10, // 10 Hz
                    accuracyTarget = 0.08f, // 8cm
                    maxDuration = 7, // 7 minutes
                    enabledSensors = listOf(SensorType.UWB, SensorType.WIFI_RTT, SensorType.IMU),
                    prioritizeBatteryLife = false,
                    prioritizeAccuracy = true
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                // If canceled, switch back to previous mode
                if (selectedMode == ScanningMode.CUSTOM) {
                    selectMode(ScanningMode.AUTO)
                    updateRadioButtonSelection(ScanningMode.AUTO)
                }
            }
            .show()
    }
    
    private fun startScanning() {
        // Return selected configuration to MainActivity
        val resultIntent = Intent().apply {
            putExtra(EXTRA_SELECTED_MODE, selectedMode)
            customSettings?.let {
                putExtra(EXTRA_CUSTOM_SETTINGS, it)
            }
        }
        
        setResult(RESULT_OK, resultIntent)
        finish()
    }
}