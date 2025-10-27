package com.environmentalimaging.app.export

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.environmentalimaging.app.R
import com.environmentalimaging.app.ai.AIAnalysisEngine
import com.environmentalimaging.app.data.ScanSession
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File

/**
 * Advanced Data Export Activity
 * Comprehensive UI for configuring and executing data exports
 */
class AdvancedDataExportActivity : AppCompatActivity(), AdvancedDataExportSystem.ExportProgressCallback {

    private lateinit var aiAnalysisEngine: AIAnalysisEngine
    private lateinit var exportSystem: AdvancedDataExportSystem
    
    // UI Components
    private lateinit var formatToggleGroup: MaterialButtonToggleGroup
    private lateinit var cloudToggleGroup: MaterialButtonToggleGroup
    private lateinit var includeMetadataCheckbox: MaterialCheckBox
    private lateinit var includeAIInsightsCheckbox: MaterialCheckBox
    private lateinit var compressDataCheckbox: MaterialCheckBox
    private lateinit var generateReportCheckbox: MaterialCheckBox
    private lateinit var qualityThresholdSlider: Slider
    private lateinit var batchSizeEditText: TextInputEditText
    private lateinit var exportButton: MaterialButton
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    
    // Current export state
    private var currentScanSession: ScanSession? = null
    private var isExporting = false
    
    // File sharing
    private val shareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_data_export)
        
        // Initialize AI and export systems
        aiAnalysisEngine = AIAnalysisEngine(this)
        exportSystem = AdvancedDataExportSystem(this, aiAnalysisEngine)
        
        // Get scan session from intent
        currentScanSession = intent.getParcelableExtra("scan_session")
        
        initializeViews()
        setupEventListeners()
        updateUI()
    }
    
    private fun initializeViews() {
        formatToggleGroup = findViewById(R.id.formatToggleGroup)
        cloudToggleGroup = findViewById(R.id.cloudToggleGroup)
        includeMetadataCheckbox = findViewById(R.id.includeMetadataCheckbox)
        includeAIInsightsCheckbox = findViewById(R.id.includeAIInsightsCheckbox)
        compressDataCheckbox = findViewById(R.id.compressDataCheckbox)
        generateReportCheckbox = findViewById(R.id.generateReportCheckbox)
        qualityThresholdSlider = findViewById(R.id.qualityThresholdSlider)
        batchSizeEditText = findViewById(R.id.batchSizeEditText)
        exportButton = findViewById(R.id.exportButton)
        progressIndicator = findViewById(R.id.progressIndicator)
        progressText = findViewById(R.id.progressText)
        statusText = findViewById(R.id.statusText)
        
        // Set default values
        qualityThresholdSlider.value = 0.5f
        batchSizeEditText.setText("1000")
        
        // Initially hide progress
        progressIndicator.visibility = View.GONE
        progressText.visibility = View.GONE
    }
    
    private fun setupEventListeners() {
        exportButton.setOnClickListener {
            if (!isExporting) {
                startExport()
            } else {
                // Cancel export (if supported)
                Toast.makeText(this, "Cannot cancel export in progress", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Format selection
        formatToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateFormatSpecificOptions(checkedId)
            }
        }
        
        // Quality threshold change
        qualityThresholdSlider.addOnChangeListener { _, value, _ ->
            updateQualityDisplay(value)
        }
    }
    
    private fun updateUI() {
        if (currentScanSession == null) {
            statusText.text = "No scan data available for export"
            exportButton.isEnabled = false
            return
        }
        
        val session = currentScanSession!!
        statusText.text = buildString {
            append("Ready to export scan session\n")
            append("Points: ${session.dataPoints.size}\n")
            append("Landmarks: ${session.landmarks.size}\n")
            append("Trajectory points: ${session.trajectory.size}")
        }
        
        exportButton.isEnabled = true
    }
    
    private fun updateFormatSpecificOptions(formatId: Int) {
        // Enable/disable options based on selected format
        when (formatId) {
            R.id.formatPly -> {
                compressDataCheckbox.isEnabled = true
                includeAIInsightsCheckbox.isEnabled = false
                includeAIInsightsCheckbox.isChecked = false
            }
            R.id.formatLas -> {
                compressDataCheckbox.isEnabled = true
                includeAIInsightsCheckbox.isEnabled = false
                includeAIInsightsCheckbox.isChecked = false
            }
            R.id.formatCsv -> {
                compressDataCheckbox.isEnabled = false
                compressDataCheckbox.isChecked = false
                includeAIInsightsCheckbox.isEnabled = true
            }
            R.id.formatJson -> {
                compressDataCheckbox.isEnabled = false
                compressDataCheckbox.isChecked = false
                includeAIInsightsCheckbox.isEnabled = true
            }
            R.id.formatXml -> {
                compressDataCheckbox.isEnabled = false
                compressDataCheckbox.isChecked = false
                includeAIInsightsCheckbox.isEnabled = true
            }
            R.id.formatPdf -> {
                compressDataCheckbox.isEnabled = false
                compressDataCheckbox.isChecked = false
                includeAIInsightsCheckbox.isEnabled = true
            }
        }
    }
    
    private fun updateQualityDisplay(value: Float) {
        val percentage = (value * 100).toInt()
        qualityThresholdSlider.setLabelFormatter { "$percentage%" }
    }
    
    private fun startExport() {
        if (currentScanSession == null) {
            Toast.makeText(this, "No scan data available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val configuration = createExportConfiguration()
        if (configuration == null) {
            return
        }
        
        isExporting = true
        exportButton.text = "Exporting..."
        exportButton.isEnabled = false
        
        progressIndicator.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        progressIndicator.progress = 0
        
        lifecycleScope.launch {
            try {
                val result = exportSystem.exportScanData(
                    currentScanSession!!.dataPoints,
                    currentScanSession!!.landmarks,
                    currentScanSession!!.trajectory,
                    configuration,
                    this@AdvancedDataExportActivity
                )
                
                handleExportResult(result)
            } catch (e: Exception) {
                handleExportError(e.message ?: "Unknown error")
            } finally {
                isExporting = false
                exportButton.text = "Export Data"
                exportButton.isEnabled = true
            }
        }
    }
    
    private fun createExportConfiguration(): AdvancedDataExportSystem.ExportConfiguration? {
        val selectedFormatId = formatToggleGroup.checkedButtonId
        val selectedCloudId = cloudToggleGroup.checkedButtonId
        
        if (selectedFormatId == View.NO_ID) {
            Toast.makeText(this, "Please select an export format", Toast.LENGTH_SHORT).show()
            return null
        }
        
        val format = when (selectedFormatId) {
            R.id.formatPly -> AdvancedDataExportSystem.ExportFormat.PLY
            R.id.formatLas -> AdvancedDataExportSystem.ExportFormat.LAS
            R.id.formatCsv -> AdvancedDataExportSystem.ExportFormat.CSV
            R.id.formatJson -> AdvancedDataExportSystem.ExportFormat.JSON
            R.id.formatXml -> AdvancedDataExportSystem.ExportFormat.XML
            R.id.formatPdf -> AdvancedDataExportSystem.ExportFormat.PDF_REPORT
            else -> AdvancedDataExportSystem.ExportFormat.JSON
        }
        
        val cloudProvider = when (selectedCloudId) {
            R.id.cloudGoogleDrive -> AdvancedDataExportSystem.CloudProvider.GOOGLE_DRIVE
            R.id.cloudDropbox -> AdvancedDataExportSystem.CloudProvider.DROPBOX
            R.id.cloudOneDrive -> AdvancedDataExportSystem.CloudProvider.ONEDRIVE
            else -> AdvancedDataExportSystem.CloudProvider.LOCAL_STORAGE
        }
        
        val batchSize = try {
            batchSizeEditText.text.toString().toInt()
        } catch (e: NumberFormatException) {
            1000
        }
        
        return AdvancedDataExportSystem.ExportConfiguration(
            format = format,
            includeMetadata = includeMetadataCheckbox.isChecked,
            includeAIInsights = includeAIInsightsCheckbox.isChecked,
            compressData = compressDataCheckbox.isChecked,
            cloudProvider = cloudProvider,
            generateReport = generateReportCheckbox.isChecked,
            batchSize = batchSize,
            qualityThreshold = qualityThresholdSlider.value
        )
    }
    
    override fun onProgress(progress: Float, message: String) {
        runOnUiThread {
            progressIndicator.progress = progress.toInt()
            progressText.text = message
        }
    }
    
    override fun onComplete(result: AdvancedDataExportSystem.ExportResult) {
        runOnUiThread {
            progressIndicator.visibility = View.GONE
            progressText.visibility = View.GONE
            
            if (result.success) {
                statusText.text = buildString {
                    append("Export completed successfully!\n")
                    append("File: ${result.fileUri?.lastPathSegment}\n")
                    append("Size: ${formatFileSize(result.fileSize)}\n")
                    append("Records: ${result.recordCount}\n")
                    append("Time: ${result.exportTime}ms")
                }
                
                result.fileUri?.let { shareFile(it) }
            } else {
                statusText.text = "Export failed: ${result.errorMessage}"
            }
        }
    }
    
    override fun onError(error: String) {
        runOnUiThread {
            progressIndicator.visibility = View.GONE
            progressText.visibility = View.GONE
            statusText.text = "Export error: $error"
        }
    }
    
    private fun handleExportResult(result: AdvancedDataExportSystem.ExportResult) {
        // Additional result handling if needed
    }
    
    private fun handleExportError(error: String) {
        Toast.makeText(this, "Export failed: $error", Toast.LENGTH_LONG).show()
    }
    
    private fun shareFile(fileUri: Uri) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Share exported file")
            shareLauncher.launch(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exportSystem.cleanup()
    }
    
    companion object {
        const val EXTRA_SCAN_SESSION = "scan_session"
    }
}