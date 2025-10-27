package com.environmentalimaging.app.ai

import android.app.*
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

// Import shared data classes from AIDataModels
import com.environmentalimaging.app.ai.SeverityLevel
import com.environmentalimaging.app.ai.IssueType
import com.environmentalimaging.app.ai.SystemIssue
import com.environmentalimaging.app.ai.SuggestionPriority

/**
 * AI-Powered System Health Monitor
 * Continuously monitors system performance, predicts issues, and provides optimization recommendations
 */
class AISystemHealthMonitor(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    
    // Monitoring streams
    private val _systemHealth = MutableSharedFlow<SystemHealthUpdate>()
    val systemHealth: SharedFlow<SystemHealthUpdate> = _systemHealth.asSharedFlow()
    
    private val _performanceAlerts = MutableSharedFlow<PerformanceAlert>()
    val performanceAlerts: SharedFlow<PerformanceAlert> = _performanceAlerts.asSharedFlow()
    
    private val _optimizationSuggestions = MutableSharedFlow<OptimizationSuggestion>()
    val optimizationSuggestions: SharedFlow<OptimizationSuggestion> = _optimizationSuggestions.asSharedFlow()
    
    // Historical data for trend analysis
    private val performanceHistory = mutableListOf<PerformanceSnapshot>()
    private val maxHistorySize = 1000
    
    // Monitoring configuration
    private val monitoringIntervalMs = 2000L
    private val alertThresholds = AlertThresholds()
    
    // Performance baselines (learned over time)
    private var performanceBaselines = PerformanceBaselines()
    private var isLearningBaselines = true
    private var baselineSamples = 0
    private val maxBaselineSamples = 100
    
    companion object {
        private const val TAG = "AISystemHealthMonitor"
    }
    
    /**
     * Initialize system health monitoring
     */
    fun initialize() {
        Log.d(TAG, "Initializing AI System Health Monitor")
        startContinuousMonitoring()
        startTrendAnalysis()
        startPredictiveAnalysis()
    }
    
    /**
     * Get current system health snapshot
     */
    suspend fun getCurrentSystemHealth(): SystemHealthSnapshot {
        return withContext(Dispatchers.Default) {
            try {
                val snapshot = capturePerformanceSnapshot()
                val analysis = analyzeCurrentPerformance(snapshot)
                
                SystemHealthSnapshot(
                    performanceSnapshot = snapshot,
                    healthScore = calculateHealthScore(snapshot),
                    status = determineSystemStatus(snapshot),
                    issues = identifyCurrentIssues(snapshot),
                    recommendations = generateCurrentRecommendations(snapshot),
                    trends = analyzeTrends(),
                    predictions = generatePredictions(),
                    timestamp = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing system health", e)
                SystemHealthSnapshot.error()
            }
        }
    }
    
    /**
     * Predict system issues before they occur
     */
    suspend fun predictSystemIssues(): List<PredictedIssue> {
        return withContext(Dispatchers.Default) {
            try {
                val predictions = mutableListOf<PredictedIssue>()
                
                if (performanceHistory.size < 10) {
                    return@withContext emptyList()
                }
                
                // Analyze memory trend
                val memoryTrend = analyzeMemoryTrend()
                if (memoryTrend.isIncreasing && memoryTrend.projectedValue > 0.9f) {
                    predictions.add(
                        PredictedIssue(
                            type = IssueType.MEMORY_EXHAUSTION,
                            description = "Memory usage trending upward - potential OutOfMemory in ${memoryTrend.timeToThreshold} minutes",
                            probability = memoryTrend.confidence,
                            timeToOccurrence = (memoryTrend.timeToThreshold * 60 * 1000).toLong(),
                            severity = SeverityLevel.ERROR,
                            preventionActions = listOf(
                                "Clear old sensor data",
                                "Reduce 3D visualization quality",
                                "Restart the app if usage exceeds 85%"
                            )
                        )
                    )
                }
                
                // Analyze CPU trend
                val cpuTrend = analyzeCPUTrend()
                if (cpuTrend.isIncreasing && cpuTrend.projectedValue > 0.8f) {
                    predictions.add(
                        PredictedIssue(
                            type = IssueType.CPU_OVERLOAD,
                            description = "CPU usage increasing - potential performance degradation in ${cpuTrend.timeToThreshold} minutes",
                            probability = cpuTrend.confidence,
                            timeToOccurrence = (cpuTrend.timeToThreshold * 60 * 1000).toLong(),
                            severity = SeverityLevel.WARNING,
                            preventionActions = listOf(
                                "Reduce sensor sampling rates",
                                "Lower frame rate for 3D visualization",
                                "Close background applications"
                            )
                        )
                    )
                }
                
                // Analyze battery trend
                val batteryTrend = analyzeBatteryTrend()
                if (batteryTrend.timeToThreshold < 30) { // Less than 30 minutes to low battery
                    predictions.add(
                        PredictedIssue(
                            type = IssueType.BATTERY_DEPLETION,
                            description = "Battery will reach critical level in ${batteryTrend.timeToThreshold} minutes",
                            probability = batteryTrend.confidence,
                            timeToOccurrence = (batteryTrend.timeToThreshold * 60 * 1000).toLong(),
                            severity = SeverityLevel.WARNING,
                            preventionActions = listOf(
                                "Enable battery saver mode",
                                "Reduce screen brightness",
                                "Disable unnecessary sensors"
                            )
                        )
                    )
                }
                
                // Analyze thermal trend
                val thermalTrend = analyzeThermalTrend()
                if (thermalTrend.isIncreasing && thermalTrend.projectedValue > 0.7f) {
                    predictions.add(
                        PredictedIssue(
                            type = IssueType.THERMAL_THROTTLING,
                            description = "Device temperature rising - potential throttling in ${thermalTrend.timeToThreshold} minutes",
                            probability = thermalTrend.confidence,
                            timeToOccurrence = (thermalTrend.timeToThreshold * 60 * 1000).toLong(),
                            severity = SeverityLevel.ERROR,
                            preventionActions = listOf(
                                "Reduce processing intensity",
                                "Allow device to cool down",
                                "Lower 3D rendering quality"
                            )
                        )
                    )
                }
                
                predictions.sortedByDescending { it.probability }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error predicting system issues", e)
                emptyList()
            }
        }
    }
    
    /**
     * Optimize system performance based on current conditions
     */
    suspend fun optimizePerformance(): OptimizationResult {
        return withContext(Dispatchers.Default) {
            try {
                val currentSnapshot = capturePerformanceSnapshot()
                val optimizations = mutableListOf<AppliedOptimization>()
                
                // Memory optimization
                if (currentSnapshot.memoryUsage > 0.8f) {
                    val memoryFreed = optimizeMemoryUsage()
                    optimizations.add(
                        AppliedOptimization(
                            type = OptimizationType.MEMORY_CLEANUP,
                            description = "Freed ${memoryFreed}MB of memory",
                            impact = memoryFreed / 100f, // Normalize to 0-1
                            success = memoryFreed > 0
                        )
                    )
                }
                
                // CPU optimization
                if (currentSnapshot.cpuUsage > 0.7f) {
                    val cpuReduction = optimizeCPUUsage()
                    optimizations.add(
                        AppliedOptimization(
                            type = OptimizationType.CPU_OPTIMIZATION,
                            description = "Reduced CPU usage by ${(cpuReduction * 100).toInt()}%",
                            impact = cpuReduction,
                            success = cpuReduction > 0.05f
                        )
                    )
                }
                
                // Battery optimization
                if (currentSnapshot.batteryLevel < 0.3f && currentSnapshot.batteryDrainRate > 0.1f) {
                    val batteryOptimization = optimizeBatteryUsage()
                    optimizations.add(
                        AppliedOptimization(
                            type = OptimizationType.BATTERY_OPTIMIZATION,
                            description = "Applied battery saving measures",
                            impact = batteryOptimization,
                            success = batteryOptimization > 0.1f
                        )
                    )
                }
                
                // Network optimization
                if (currentSnapshot.networkLatency > 1000f) {
                    val networkOptimization = optimizeNetworkUsage()
                    optimizations.add(
                        AppliedOptimization(
                            type = OptimizationType.NETWORK_OPTIMIZATION,
                            description = "Optimized network connections",
                            impact = networkOptimization,
                            success = networkOptimization > 0.1f
                        )
                    )
                }
                
                val overallImprovement = optimizations.map { it.impact }.average().toFloat()
                
                OptimizationResult(
                    optimizations = optimizations,
                    overallImprovement = overallImprovement,
                    newHealthScore = calculateHealthScore(capturePerformanceSnapshot()),
                    timestamp = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error optimizing performance", e)
                OptimizationResult.failed()
            }
        }
    }
    
    private fun startContinuousMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    val snapshot = capturePerformanceSnapshot()
                    
                    // Add to history
                    performanceHistory.add(snapshot)
                    if (performanceHistory.size > maxHistorySize) {
                        performanceHistory.removeFirst()
                    }
                    
                    // Update baselines during learning phase
                    if (isLearningBaselines && baselineSamples < maxBaselineSamples) {
                        updatePerformanceBaselines(snapshot)
                        baselineSamples++
                        
                        if (baselineSamples >= maxBaselineSamples) {
                            isLearningBaselines = false
                            Log.d(TAG, "Baseline learning completed")
                        }
                    }
                    
                    // Check for immediate alerts
                    checkForImmediateAlerts(snapshot)
                    
                    // Emit health update
                    val healthUpdate = SystemHealthUpdate(
                        snapshot = snapshot,
                        healthScore = calculateHealthScore(snapshot),
                        alerts = generateCurrentAlerts(snapshot),
                        timestamp = System.currentTimeMillis()
                    )
                    _systemHealth.emit(healthUpdate)
                    
                    delay(monitoringIntervalMs)
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in continuous monitoring", e)
                    delay(monitoringIntervalMs * 2) // Back off on error
                }
            }
        }
    }
    
    private fun startTrendAnalysis() {
        scope.launch {
            while (isActive) {
                delay(30000) // Every 30 seconds
                
                try {
                    if (performanceHistory.size >= 10) {
                        analyzeTrendsAndAlert()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in trend analysis", e)
                }
            }
        }
    }
    
    private fun startPredictiveAnalysis() {
        scope.launch {
            while (isActive) {
                delay(60000) // Every minute
                
                try {
                    val predictions = predictSystemIssues()
                    predictions.forEach { prediction ->
                        if (prediction.probability > 0.7f) {
                            _performanceAlerts.emit(
                                PerformanceAlert(
                                    type = AlertType.PREDICTIVE,
                                    message = prediction.description,
                                    severity = prediction.severity,
                                    data = prediction,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in predictive analysis", e)
                }
            }
        }
    }
    
    private fun capturePerformanceSnapshot(): PerformanceSnapshot {
        return try {
            PerformanceSnapshot(
                timestamp = System.currentTimeMillis(),
                cpuUsage = getCPUUsage(),
                memoryUsage = getMemoryUsage(),
                batteryLevel = getBatteryLevel(),
                batteryDrainRate = getBatteryDrainRate(),
                networkLatency = getNetworkLatency(),
                diskUsage = getDiskUsage(),
                temperature = getDeviceTemperature(),
                framerate = getCurrentFramerate(),
                appMemoryUsage = getAppMemoryUsage(),
                heapSize = getHeapSize(),
                gcFrequency = getGCFrequency()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing performance snapshot", e)
            PerformanceSnapshot.default()
        }
    }
    
    private fun getCPUUsage(): Float {
        return try {
            val file = RandomAccessFile("/proc/stat", "r")
            val line = file.readLine()
            file.close()
            
            val cpuTimes = line.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = cpuTimes[3]
            val total = cpuTimes.sum()
            
            val usage = 1.0f - (idle.toFloat() / total.toFloat())
            usage.coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read CPU usage", e)
            0.5f // Default assumption
        }
    }
    
    private fun getMemoryUsage(): Float {
        return try {
            val memInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memInfo)
            
            val usedMemory = memInfo.totalMem - memInfo.availMem
            val usage = usedMemory.toFloat() / memInfo.totalMem.toFloat()
            usage.coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read memory usage", e)
            0.5f
        }
    }
    
    private fun getBatteryLevel(): Float {
        return try {
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            level / 100f
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read battery level", e)
            0.5f
        }
    }
    
    private fun getBatteryDrainRate(): Float {
        return try {
            // Calculate drain rate from recent history
            if (performanceHistory.size >= 5) {
                val recent = performanceHistory.takeLast(5)
                val timeSpan = recent.last().timestamp - recent.first().timestamp
                val batteryDrop = recent.first().batteryLevel - recent.last().batteryLevel
                
                if (timeSpan > 0) {
                    (batteryDrop / (timeSpan / 1000f / 3600f)).coerceAtLeast(0f) // % per hour
                } else 0f
            } else 0f
        } catch (e: Exception) {
            Log.w(TAG, "Unable to calculate battery drain rate", e)
            0f
        }
    }
    
    private fun getNetworkLatency(): Float {
        // This would typically ping a known server
        // For now, return a simulated value
        return 50f + (0..100).random()
    }
    
    private fun getDiskUsage(): Float {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            
            (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read disk usage", e)
            0.3f
        }
    }
    
    private fun getDeviceTemperature(): Float {
        // Device temperature is difficult to access directly
        // This would require root access or thermal API
        return 0.4f // Normalized temperature (0-1 scale)
    }
    
    private fun getCurrentFramerate(): Float {
        // This would be updated by the rendering system
        return 30f // Default assumption
    }
    
    private fun getAppMemoryUsage(): Float {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            
            (usedMemory.toFloat() / maxMemory.toFloat()).coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read app memory usage", e)
            0.3f
        }
    }
    
    private fun getHeapSize(): Float {
        return try {
            val runtime = Runtime.getRuntime()
            runtime.totalMemory().toFloat() / (1024 * 1024) // MB
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read heap size", e)
            64f
        }
    }
    
    private fun getGCFrequency(): Float {
        // This would track GC events over time
        return 2f // GC events per second (placeholder)
    }
    
    private fun calculateHealthScore(snapshot: PerformanceSnapshot): Float {
        val cpuScore = 1f - snapshot.cpuUsage
        val memoryScore = 1f - snapshot.memoryUsage
        val batteryScore = snapshot.batteryLevel
        val temperatureScore = 1f - snapshot.temperature
        
        val weights = floatArrayOf(0.3f, 0.3f, 0.2f, 0.2f)
        val scores = floatArrayOf(cpuScore, memoryScore, batteryScore, temperatureScore)
        
        return scores.zip(weights).sumOf { (score, weight) -> 
            (score * weight).toDouble() 
        }.toFloat().coerceIn(0f, 1f)
    }
    
    private fun determineSystemStatus(snapshot: PerformanceSnapshot): SystemStatus {
        val healthScore = calculateHealthScore(snapshot)
        
        return when {
            healthScore >= 0.8f -> SystemStatus.EXCELLENT
            healthScore >= 0.6f -> SystemStatus.GOOD
            healthScore >= 0.4f -> SystemStatus.FAIR
            healthScore >= 0.2f -> SystemStatus.POOR
            else -> SystemStatus.CRITICAL
        }
    }
    
    private fun identifyCurrentIssues(snapshot: PerformanceSnapshot): List<SystemIssue> {
        val issues = mutableListOf<SystemIssue>()
        
        if (snapshot.cpuUsage > alertThresholds.cpuUsageHigh) {
            issues.add(
                SystemIssue(
                    type = IssueType.HIGH_CPU_USAGE.name,
                    description = "CPU usage is ${(snapshot.cpuUsage * 100).toInt()}% (threshold: ${(alertThresholds.cpuUsageHigh * 100).toInt()}%)",
                    severity = if (snapshot.cpuUsage > 0.9f) SeverityLevel.ERROR else SeverityLevel.WARNING,
                    timestamp = System.currentTimeMillis(),
                    recommendations = listOf("Reduce sensor sampling frequency", "Lower 3D visualization frame rate"),
                    value = snapshot.cpuUsage
                )
            )
        }
        
        if (snapshot.memoryUsage > alertThresholds.memoryUsageHigh) {
            issues.add(
                SystemIssue(
                    type = IssueType.HIGH_MEMORY_USAGE.name,
                    description = "Memory usage is ${(snapshot.memoryUsage * 100).toInt()}% (threshold: ${(alertThresholds.memoryUsageHigh * 100).toInt()}%)",
                    severity = if (snapshot.memoryUsage > 0.95f) SeverityLevel.ERROR else SeverityLevel.WARNING,
                    timestamp = System.currentTimeMillis(),
                    recommendations = listOf("Clear old point cloud data", "Reduce 3D visualization detail level"),
                    value = snapshot.memoryUsage
                )
            )
        }
        
        if (snapshot.batteryLevel < alertThresholds.batteryLevelLow) {
            issues.add(
                SystemIssue(
                    type = IssueType.LOW_BATTERY.name,
                    description = "Battery level is ${(snapshot.batteryLevel * 100).toInt()}% (threshold: ${(alertThresholds.batteryLevelLow * 100).toInt()}%)",
                    severity = if (snapshot.batteryLevel < 0.1f) SeverityLevel.ERROR else SeverityLevel.WARNING,
                    timestamp = System.currentTimeMillis(),
                    recommendations = listOf("Enable battery saver mode", "Reduce screen brightness"),
                    value = snapshot.batteryLevel
                )
            )
        }
        
        if (snapshot.temperature > alertThresholds.temperatureHigh) {
            issues.add(
                SystemIssue(
                    type = IssueType.HIGH_TEMPERATURE.name,
                    description = "Device temperature is elevated (${(snapshot.temperature * 100).toInt()}%)",
                    severity = if (snapshot.temperature > 0.8f) SeverityLevel.ERROR else SeverityLevel.WARNING,
                    timestamp = System.currentTimeMillis(),
                    recommendations = listOf("Allow device to cool down", "Reduce processing intensity"),
                    value = snapshot.temperature
                )
            )
        }
        
        return issues
    }
    
    private fun generateCurrentRecommendations(snapshot: PerformanceSnapshot): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (snapshot.cpuUsage > 0.7f) {
            recommendations.add("Reduce sensor sampling frequency to lower CPU usage")
            recommendations.add("Lower 3D visualization frame rate")
        }
        
        if (snapshot.memoryUsage > 0.8f) {
            recommendations.add("Clear old point cloud data")
            recommendations.add("Reduce 3D visualization detail level")
        }
        
        if (snapshot.batteryLevel < 0.3f) {
            recommendations.add("Enable battery saver mode")
            recommendations.add("Reduce screen brightness")
        }
        
        if (snapshot.temperature > 0.6f) {
            recommendations.add("Allow device to cool down")
            recommendations.add("Reduce processing intensity")
        }
        
        return recommendations
    }
    
    private fun analyzeTrends(): TrendAnalysis {
        if (performanceHistory.size < 10) {
            return TrendAnalysis.insufficient()
        }
        
        val recent = performanceHistory.takeLast(10)
        
        return TrendAnalysis(
            cpuTrend = calculateTrend(recent.map { it.cpuUsage }),
            memoryTrend = calculateTrend(recent.map { it.memoryUsage }),
            batteryTrend = calculateTrend(recent.map { it.batteryLevel }),
            temperatureTrend = calculateTrend(recent.map { it.temperature })
        )
    }
    
    private fun calculateTrend(values: List<Float>): TrendDirection {
        if (values.size < 3) return TrendDirection.STABLE
        
        val first = values.take(values.size / 2).average()
        val second = values.drop(values.size / 2).average()
        
        val change = (second - first) / first
        
        return when {
            change > 0.1 -> TrendDirection.INCREASING
            change < -0.1 -> TrendDirection.DECREASING
            else -> TrendDirection.STABLE
        }
    }
    
    private fun generatePredictions(): List<String> {
        // Simplified predictions based on trends
        val predictions = mutableListOf<String>()
        
        if (performanceHistory.size >= 10) {
            val trends = analyzeTrends()
            
            if (trends.memoryTrend == TrendDirection.INCREASING) {
                predictions.add("Memory usage trending upward - monitor for potential issues")
            }
            
            if (trends.batteryTrend == TrendDirection.DECREASING) {
                predictions.add("Battery draining faster than usual")
            }
            
            if (trends.cpuTrend == TrendDirection.INCREASING) {
                predictions.add("CPU usage increasing - consider optimization")
            }
        }
        
        return predictions
    }
    
    // Optimization methods
    private fun optimizeMemoryUsage(): Float {
        val beforeMemory = getAppMemoryUsage()
        
        try {
            System.gc() // Request garbage collection
            Thread.sleep(100) // Give GC time to run
            
            val afterMemory = getAppMemoryUsage()
            return maxOf(0f, beforeMemory - afterMemory) * 100f // MB freed
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing memory", e)
            return 0f
        }
    }
    
    private fun optimizeCPUUsage(): Float {
        // This would implement CPU optimization strategies
        // For now, return a simulated improvement
        return 0.1f // 10% improvement
    }
    
    private fun optimizeBatteryUsage(): Float {
        // This would implement battery optimization strategies
        return 0.15f // 15% improvement
    }
    
    private fun optimizeNetworkUsage(): Float {
        // This would implement network optimization strategies
        return 0.2f // 20% improvement
    }
    
    // Additional helper methods
    private fun updatePerformanceBaselines(snapshot: PerformanceSnapshot) {
        performanceBaselines = performanceBaselines.copy(
            avgCpuUsage = (performanceBaselines.avgCpuUsage * baselineSamples + snapshot.cpuUsage) / (baselineSamples + 1),
            avgMemoryUsage = (performanceBaselines.avgMemoryUsage * baselineSamples + snapshot.memoryUsage) / (baselineSamples + 1),
            avgBatteryDrain = (performanceBaselines.avgBatteryDrain * baselineSamples + snapshot.batteryDrainRate) / (baselineSamples + 1),
            avgTemperature = (performanceBaselines.avgTemperature * baselineSamples + snapshot.temperature) / (baselineSamples + 1)
        )
    }
    
    private fun checkForImmediateAlerts(snapshot: PerformanceSnapshot) {
        scope.launch {
            try {
                if (snapshot.memoryUsage > 0.95f) {
                    _performanceAlerts.emit(
                        PerformanceAlert(
                            type = AlertType.CRITICAL,
                            message = "Critical memory usage: ${(snapshot.memoryUsage * 100).toInt()}%",
                            severity = SeverityLevel.ERROR,
                            data = snapshot,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
                if (snapshot.batteryLevel < 0.05f) {
                    _performanceAlerts.emit(
                        PerformanceAlert(
                            type = AlertType.CRITICAL,
                            message = "Critical battery level: ${(snapshot.batteryLevel * 100).toInt()}%",
                            severity = SeverityLevel.ERROR,
                            data = snapshot,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking immediate alerts", e)
            }
        }
    }
    
    private fun generateCurrentAlerts(snapshot: PerformanceSnapshot): List<String> {
        val alerts = mutableListOf<String>()
        
        if (snapshot.cpuUsage > 0.8f) {
            alerts.add("High CPU usage detected")
        }
        
        if (snapshot.memoryUsage > 0.8f) {
            alerts.add("High memory usage detected")
        }
        
        if (snapshot.batteryLevel < 0.2f) {
            alerts.add("Low battery warning")
        }
        
        return alerts
    }
    
    private fun analyzeCurrentPerformance(snapshot: PerformanceSnapshot): PerformanceAnalysis {
        return PerformanceAnalysis(
            overallScore = calculateHealthScore(snapshot),
            bottlenecks = identifyBottlenecks(snapshot),
            recommendations = generateCurrentRecommendations(snapshot)
        )
    }
    
    private fun identifyBottlenecks(snapshot: PerformanceSnapshot): List<String> {
        val bottlenecks = mutableListOf<String>()
        
        val maxUsage = maxOf(snapshot.cpuUsage, snapshot.memoryUsage, 1f - snapshot.batteryLevel, snapshot.temperature)
        
        when (maxUsage) {
            snapshot.cpuUsage -> bottlenecks.add("CPU is the primary bottleneck")
            snapshot.memoryUsage -> bottlenecks.add("Memory is the primary bottleneck")
            1f - snapshot.batteryLevel -> bottlenecks.add("Battery level is concerning")
            snapshot.temperature -> bottlenecks.add("Temperature is limiting performance")
        }
        
        return bottlenecks
    }
    
    private fun analyzeTrendsAndAlert() {
        scope.launch {
            try {
                val trends = analyzeTrends()
                
                if (trends.memoryTrend == TrendDirection.INCREASING) {
                    val memoryTrend = analyzeMemoryTrend()
                    if (memoryTrend.projectedValue > 0.9f) {
                        _optimizationSuggestions.emit(
                            OptimizationSuggestion(
                                type = OptimizationType.MEMORY_CLEANUP,
                                description = "Memory usage trending up - cleanup recommended",
                                priority = SuggestionPriority.HIGH,
                                expectedImprovement = 0.3f,
                                actions = listOf("Clear sensor data buffers", "Force garbage collection")
                            )
                        )
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in trend alert analysis", e)
            }
        }
    }
    
    private fun analyzeMemoryTrend(): TrendProjection {
        val recent = performanceHistory.takeLast(10).map { it.memoryUsage }
        return projectTrend(recent, 0.9f)
    }
    
    private fun analyzeCPUTrend(): TrendProjection {
        val recent = performanceHistory.takeLast(10).map { it.cpuUsage }
        return projectTrend(recent, 0.8f)
    }
    
    private fun analyzeBatteryTrend(): TrendProjection {
        val recent = performanceHistory.takeLast(10).map { it.batteryLevel }
        return projectTrend(recent.map { 1f - it }, 0.8f) // Invert so increasing = bad
    }
    
    private fun analyzeThermalTrend(): TrendProjection {
        val recent = performanceHistory.takeLast(10).map { it.temperature }
        return projectTrend(recent, 0.7f)
    }
    
    private fun projectTrend(values: List<Float>, threshold: Float): TrendProjection {
        if (values.size < 5) {
            return TrendProjection(false, 0f, 0f, 0f)
        }
        
        // Simple linear regression
        val n = values.size
        val x = (0 until n).map { it.toFloat() }
        val y = values
        
        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { (xi, yi) -> (xi * yi).toDouble() }.toFloat()
        val sumX2 = x.sumOf { (it * it).toDouble() }.toFloat()
        
        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)
        val intercept = (sumY - slope * sumX) / n
        
        // Project future value
        val futureSteps = 10 // 10 monitoring intervals ahead
        val projectedValue = intercept + slope * (n + futureSteps)
        
        // Calculate time to threshold
        val timeToThreshold = if (slope > 0 && projectedValue < threshold) {
            ((threshold - (intercept + slope * n)) / slope * monitoringIntervalMs / 60000).toFloat() // minutes
        } else {
            Float.MAX_VALUE
        }
        
        val confidence = minOf(1f, abs(slope) * 10f) // Simple confidence based on slope magnitude
        
        return TrendProjection(
            isIncreasing = slope > 0.01f,
            projectedValue = projectedValue.coerceIn(0f, 1f),
            timeToThreshold = timeToThreshold,
            confidence = confidence.coerceIn(0f, 1f)
        )
    }
    
    fun shutdown() {
        scope.cancel()
    }
}

// Data classes for system monitoring
data class PerformanceSnapshot(
    val timestamp: Long,
    val cpuUsage: Float,
    val memoryUsage: Float,
    val batteryLevel: Float,
    val batteryDrainRate: Float,
    val networkLatency: Float,
    val diskUsage: Float,
    val temperature: Float,
    val framerate: Float,
    val appMemoryUsage: Float,
    val heapSize: Float,
    val gcFrequency: Float
) {
    companion object {
        fun default() = PerformanceSnapshot(
            System.currentTimeMillis(), 0.5f, 0.5f, 0.5f, 0f, 100f, 0.3f, 0.4f, 30f, 0.3f, 64f, 2f
        )
    }
}

data class SystemHealthSnapshot(
    val performanceSnapshot: PerformanceSnapshot,
    val healthScore: Float,
    val status: SystemStatus,
    val issues: List<SystemIssue>,
    val recommendations: List<String>,
    val trends: TrendAnalysis,
    val predictions: List<String>,
    val timestamp: Long
) {
    companion object {
        fun error() = SystemHealthSnapshot(
            PerformanceSnapshot.default(), 0.5f, SystemStatus.UNKNOWN, emptyList(), 
            listOf("Error capturing system health"), TrendAnalysis.insufficient(), 
            emptyList(), System.currentTimeMillis()
        )
    }
}

data class SystemHealthUpdate(
    val snapshot: PerformanceSnapshot,
    val healthScore: Float,
    val alerts: List<String>,
    val timestamp: Long
)

enum class SystemStatus {
    EXCELLENT, GOOD, FAIR, POOR, CRITICAL, UNKNOWN
}

// SystemIssue is defined in AIDataModels.kt

// IssueType enum is defined in AIDataModels.kt

// Severity enum is replaced by SeverityLevel in AIDataModels.kt

data class TrendAnalysis(
    val cpuTrend: TrendDirection,
    val memoryTrend: TrendDirection,
    val batteryTrend: TrendDirection,
    val temperatureTrend: TrendDirection
) {
    companion object {
        fun insufficient() = TrendAnalysis(
            TrendDirection.STABLE, TrendDirection.STABLE, TrendDirection.STABLE, TrendDirection.STABLE
        )
    }
}

enum class TrendDirection {
    INCREASING, DECREASING, STABLE
}

data class TrendProjection(
    val isIncreasing: Boolean,
    val projectedValue: Float,
    val timeToThreshold: Float, // minutes
    val confidence: Float
)

data class PredictedIssue(
    val type: IssueType,
    val description: String,
    val probability: Float,
    val timeToOccurrence: Long, // milliseconds
    val severity: SeverityLevel,
    val preventionActions: List<String>
)

data class PerformanceAlert(
    val type: AlertType,
    val message: String,
    val severity: SeverityLevel,
    val data: Any,
    val timestamp: Long
)

enum class AlertType {
    IMMEDIATE, PREDICTIVE, CRITICAL
}

data class OptimizationSuggestion(
    val type: OptimizationType,
    val description: String,
    val priority: SuggestionPriority,
    val expectedImprovement: Float,
    val actions: List<String>
)

enum class OptimizationType {
    MEMORY_CLEANUP, CPU_OPTIMIZATION, BATTERY_OPTIMIZATION, NETWORK_OPTIMIZATION
}

// SuggestionPriority is defined in AIDataModels.kt

data class OptimizationResult(
    val optimizations: List<AppliedOptimization>,
    val overallImprovement: Float,
    val newHealthScore: Float,
    val timestamp: Long
) {
    companion object {
        fun failed() = OptimizationResult(emptyList(), 0f, 0f, System.currentTimeMillis())
    }
}

data class AppliedOptimization(
    val type: OptimizationType,
    val description: String,
    val impact: Float,
    val success: Boolean
)

data class AlertThresholds(
    val cpuUsageHigh: Float = 0.8f,
    val memoryUsageHigh: Float = 0.85f,
    val batteryLevelLow: Float = 0.2f,
    val temperatureHigh: Float = 0.7f
)

data class PerformanceBaselines(
    val avgCpuUsage: Float = 0.3f,
    val avgMemoryUsage: Float = 0.4f,
    val avgBatteryDrain: Float = 5f, // % per hour
    val avgTemperature: Float = 0.4f
)

data class PerformanceAnalysis(
    val overallScore: Float,
    val bottlenecks: List<String>,
    val recommendations: List<String>
)