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


package com.environmentalimaging.app.export

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.R
import com.environmentalimaging.app.data.ScanSession
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch

/**
 * Activity for advanced data export with multiple format options
 */
class AdvancedDataExportActivity : AppCompatActivity() {
    
    private lateinit var exportSystem: AdvancedDataExportSystem
    private var scanSession: ScanSession? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_data_export)
        
        exportSystem = AdvancedDataExportSystem(this)
        
        // Get scan session from intent
        scanSession = intent.getParcelableExtra("scan_session")
        
        setupToolbar()
        setupExportButton()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Export Data"
    }
    
    private fun setupExportButton() {
        findViewById<Button>(R.id.exportButton)?.setOnClickListener {
            exportData()
        }
    }
    
    private fun exportData() {
        val session = scanSession
        if (session == null) {
            Toast.makeText(this, "No scan session data available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Get selected format - simplified without RadioGroup for now
        val format = AdvancedDataExportSystem.ExportFormat.JSON
        
        // Get export options - simplified without checkboxes for now
        val includeMetadata = true
        val compress = true
        val cloudSync = false
        
        val options = AdvancedDataExportSystem.ExportOptions(
            format = format,
            includeMetadata = includeMetadata,
            compress = compress,
            cloudSync = cloudSync
        )
        
        // Create output file URI
        val fileName = "${session.id}_${System.currentTimeMillis()}.${format.name.lowercase()}"
        val outputUri = Uri.parse("file://${getExternalFilesDir(null)}/$fileName")
        
        // Show progress - simplified without progress indicator for now
        Toast.makeText(this, "Exporting...", Toast.LENGTH_SHORT).show()
        
        // Export in background
        lifecycleScope.launch {
            try {
                val result = exportSystem.exportSession(
                    session = session,
                    outputUri = outputUri,
                    options = options,
                    progressCallback = { progress ->
                        // Progress updates
                    }
                )
                
                runOnUiThread {
                    if (result.success) {
                        Toast.makeText(
                            this@AdvancedDataExportActivity,
                            "Export successful: ${result.filePath}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@AdvancedDataExportActivity,
                            "Export failed: ${result.errorMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@AdvancedDataExportActivity,
                        "Export error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
