package com.environmentalimaging.app.analytics

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.environmentalimaging.app.R
import com.environmentalimaging.app.data.ScanSession
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Session Analytics Dashboard - Provides insights into scanning history
 * Features:
 * - Scanning patterns analysis
 * - Performance trends over time
 * - Quality metrics
 * - Usage statistics
 */
class SessionAnalyticsDashboardActivity : AppCompatActivity() {
    
    private lateinit var totalScansText: TextView
    private lateinit var totalPointsText: TextView
    private lateinit var avgDurationText: TextView
    private lateinit var avgAccuracyText: TextView
    private lateinit var sessionsRecyclerView: RecyclerView
    
    private data class AnalyticsData(
        val totalSessions: Int,
        val totalPoints: Long,
        val avgDuration: Int, // seconds
        val avgAccuracy: Float, // meters
        val recentSessions: List<SessionSummary>
    )
    
    private data class SessionSummary(
        val id: String,
        val date: String,
        val duration: Int,
        val points: Int,
        val quality: String
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_session_analytics_dashboard)
        
        setupViews()
        loadAnalytics()
    }
    
    private fun setupViews() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Session Analytics"
        
        totalScansText = findViewById(R.id.totalScansText)
        totalPointsText = findViewById(R.id.totalPointsText)
        avgDurationText = findViewById(R.id.avgDurationText)
        avgAccuracyText = findViewById(R.id.avgAccuracyText)
        sessionsRecyclerView = findViewById(R.id.sessionsRecyclerView)
        
        sessionsRecyclerView.layoutManager = LinearLayoutManager(this)
    }
    
    private fun loadAnalytics() {
        lifecycleScope.launch {
            // Simulate loading analytics data
            delay(500)
            
            val analytics = generateMockAnalytics()
            updateUI(analytics)
        }
    }
    
    private fun generateMockAnalytics(): AnalyticsData {
        // In a real app, this would load from a database
        val sessions = (1..15).map { i ->
            SessionSummary(
                id = "Session_$i",
                date = "2025-10-${27 - (i / 2)}",
                duration = (30..600).random(),
                points = (1000..50000).random(),
                quality = when ((1..5).random()) {
                    5 -> "Excellent"
                    4 -> "Good"
                    3 -> "Fair"
                    else -> "Poor"
                }
            )
        }
        
        return AnalyticsData(
            totalSessions = sessions.size,
            totalPoints = sessions.sumOf { it.points.toLong() },
            avgDuration = sessions.map { it.duration }.average().roundToInt(),
            avgAccuracy = 0.08f, // 8cm average
            recentSessions = sessions.take(10)
        )
    }
    
    private fun updateUI(analytics: AnalyticsData) {
        totalScansText.text = analytics.totalSessions.toString()
        totalPointsText.text = formatNumber(analytics.totalPoints)
        avgDurationText.text = formatDuration(analytics.avgDuration)
        avgAccuracyText.text = "${(analytics.avgAccuracy * 100).roundToInt()}cm"
        
        // Setup recycler view with session list
        sessionsRecyclerView.adapter = SessionAdapter(analytics.recentSessions)
    }
    
    private fun formatNumber(number: Long): String {
        return when {
            number >= 1_000_000 -> "${(number / 1_000_000.0).format(1)}M"
            number >= 1_000 -> "${(number / 1_000.0).format(1)}K"
            else -> number.toString()
        }
    }
    
    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return if (minutes > 0) {
            "${minutes}m ${secs}s"
        } else {
            "${secs}s"
        }
    }
    
    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    // RecyclerView Adapter for session list
    private inner class SessionAdapter(
        private val sessions: List<SessionSummary>
    ) : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val cardView: MaterialCardView = view.findViewById(R.id.sessionCard)
            val sessionIdText: TextView = view.findViewById(R.id.sessionIdText)
            val sessionDateText: TextView = view.findViewById(R.id.sessionDateText)
            val sessionStatsText: TextView = view.findViewById(R.id.sessionStatsText)
            val sessionQualityText: TextView = view.findViewById(R.id.sessionQualityText)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session_summary, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            
            holder.sessionIdText.text = session.id
            holder.sessionDateText.text = session.date
            holder.sessionStatsText.text = "${formatNumber(session.points.toLong())} points • ${formatDuration(session.duration)}"
            holder.sessionQualityText.text = session.quality
            
            // Set quality color
            val qualityColor = when (session.quality) {
                "Excellent" -> android.R.color.holo_green_dark
                "Good" -> android.R.color.holo_blue_dark
                "Fair" -> android.R.color.holo_orange_dark
                else -> android.R.color.holo_red_dark
            }
            holder.sessionQualityText.setTextColor(
                holder.itemView.context.getColor(qualityColor)
            )
        }
        
        override fun getItemCount() = sessions.size
    }
}
