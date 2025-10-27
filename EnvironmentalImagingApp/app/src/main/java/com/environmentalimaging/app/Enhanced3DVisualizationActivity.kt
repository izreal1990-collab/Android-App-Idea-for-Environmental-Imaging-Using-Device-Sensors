package com.environmentalimaging.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.environmentalimaging.app.data.ScanSession

/**
 * Activity for enhanced 3D visualization with advanced rendering features
 * Simplified version - full implementation would integrate with Enhanced3DVisualizationEngine
 */
class Enhanced3DVisualizationActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enhanced_3d_visualization)
        
        // Load scan session data
        val session = intent.getParcelableExtra<ScanSession>("scan_session")
        
        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = session?.id ?: "3D Visualization"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
