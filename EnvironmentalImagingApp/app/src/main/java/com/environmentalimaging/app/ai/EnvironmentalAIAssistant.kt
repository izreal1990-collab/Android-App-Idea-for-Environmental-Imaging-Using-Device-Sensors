package com.environmentalimaging.app.ai

import android.content.Context
import android.util.Log
import com.environmentalimaging.app.data.*
import com.environmentalimaging.app.slam.SLAMState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Environmental AI Assistant - Conversational AI that helps users understand
 * sensor data, SLAM results, system behavior, and provides intelligent guidance
 */
class EnvironmentalAIAssistant(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // Conversation state
    private var currentConversation = mutableListOf<AIMessage>()
    private var conversationContext = ConversationContext(
        currentSensorData = emptyList(),
        currentSLAMState = null,
        currentEnvironment = emptyList(),
        recentInsights = emptyList(),
        systemHealth = null
    )
    
    // AI responses stream
    private val _aiResponses = MutableSharedFlow<AIResponse>()
    val aiResponses: SharedFlow<AIResponse> = _aiResponses.asSharedFlow()
    
    // Knowledge base for offline responses
    private val knowledgeBase = createKnowledgeBase()
    
    companion object {
        private const val TAG = "EnvironmentalAIAssistant"
        private const val MAX_CONVERSATION_LENGTH = 20
        
        // For production, you would use a proper AI API endpoint
        private const val AI_API_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        private const val AI_API_KEY = "your-api-key-here" // Replace with actual API key
    }
    
    /**
     * Initialize the AI assistant
     */
    fun initialize() {
        Log.d(TAG, "Initializing Environmental AI Assistant")
        
        // Send welcome message
        scope.launch {
            val welcomeMessage = createWelcomeMessage()
            _aiResponses.emit(welcomeMessage)
        }
    }
    
    /**
     * Update conversation context with current system state
     */
    fun updateContext(
        sensorData: List<RangingMeasurement> = conversationContext.currentSensorData,
        slamState: SLAMState? = conversationContext.currentSLAMState as? SLAMState,
        environment: List<Point3D> = conversationContext.currentEnvironment,
        insights: List<SensorInsight> = conversationContext.recentInsights,
        systemHealth: SystemHealthReport? = conversationContext.systemHealth
    ) {
        conversationContext = ConversationContext(
            currentSensorData = sensorData,
            currentSLAMState = slamState,
            currentEnvironment = environment,
            recentInsights = insights,
            systemHealth = systemHealth
        )
    }
    
    /**
     * Process user message and generate AI response
     */
    suspend fun processUserMessage(userMessage: String): AIResponse {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing user message: $userMessage")
                
                // Add user message to conversation
                val userAIMessage = AIMessage(
                    id = generateMessageId(),
                    content = userMessage,
                    isFromUser = true,
                    timestamp = System.currentTimeMillis()
                )
                currentConversation.add(userAIMessage)
                
                // Determine response type
                val responseType = classifyUserIntent(userMessage)
                
                // Generate response based on intent
                val response = when (responseType) {
                    UserIntent.EXPLAIN_SENSOR_DATA -> explainSensorData(userMessage)
                    UserIntent.EXPLAIN_SLAM_RESULTS -> explainSLAMResults(userMessage)
                    UserIntent.SYSTEM_STATUS -> provideSystemStatus(userMessage)
                    UserIntent.TROUBLESHOOTING -> provideTroubleshooting(userMessage)
                    UserIntent.ENVIRONMENTAL_ANALYSIS -> provideEnvironmentalAnalysis(userMessage)
                    UserIntent.GENERAL_HELP -> provideGeneralHelp(userMessage)
                    UserIntent.CONVERSATIONAL -> provideConversationalResponse(userMessage)
                }
                
                // Add AI response to conversation
                val aiMessage = AIMessage(
                    id = generateMessageId(),
                    content = response.message,
                    isFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    attachments = response.attachments
                )
                currentConversation.add(aiMessage)
                
                // Maintain conversation length
                if (currentConversation.size > MAX_CONVERSATION_LENGTH) {
                    currentConversation.removeFirst()
                }
                
                response
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing user message", e)
                createErrorResponse(e)
            }
        }
    }
    
    /**
     * Get intelligent suggestions based on current system state
     */
    suspend fun getIntelligentSuggestions(): List<AISuggestion> {
        return withContext(Dispatchers.Default) {
            try {
                val suggestions = mutableListOf<AISuggestion>()
                
                // Analyze current state and provide suggestions
                if (conversationContext.currentSensorData.isNotEmpty()) {
                    suggestions.addAll(generateSensorSuggestions())
                }
                
                if (conversationContext.currentEnvironment.isNotEmpty()) {
                    suggestions.addAll(generateEnvironmentSuggestions())
                }
                
                if (conversationContext.recentInsights.isNotEmpty()) {
                    suggestions.addAll(generateInsightSuggestions())
                }
                
                if (conversationContext.systemHealth != null) {
                    suggestions.addAll(generateHealthSuggestions())
                }
                
                // Default suggestions if no specific context
                if (suggestions.isEmpty()) {
                    suggestions.addAll(getDefaultSuggestions())
                }
                
                suggestions.take(5) // Limit to top 5 suggestions
                
            } catch (e: Exception) {
                Log.e(TAG, "Error generating suggestions", e)
                emptyList()
            }
        }
    }
    
    /**
     * Explain a specific sensor measurement
     */
    suspend fun explainMeasurement(measurement: RangingMeasurement): AIResponse {
        return withContext(Dispatchers.Default) {
            try {
                val explanation = buildString {
                    appendLine("📡 **Sensor Measurement Analysis**")
                    appendLine()
                    appendLine("**Sensor Type:** ${measurement.measurementType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}")
                    appendLine("**Distance:** ${measurement.distance.format(2)}m")
                    appendLine("**Accuracy:** ${(measurement.accuracy * 100).format(1)}%")
                    appendLine("**Timestamp:** ${formatTimestamp(measurement.timestamp)}")
                    appendLine()
                    
                    when (measurement.measurementType) {
                        RangingType.WIFI_RTT -> {
                            appendLine("**WiFi RTT (Round Trip Time):**")
                            appendLine("• Measures distance to WiFi access points")
                            appendLine("• Accuracy typically ±1-2 meters")
                            appendLine("• Works best in environments with multiple APs")
                            appendLine("• Can be affected by signal reflections and interference")
                        }
                        
                        RangingType.BLUETOOTH_CHANNEL_SOUNDING -> {
                            appendLine("**Bluetooth Channel Sounding:**")
                            appendLine("• High-precision ranging using Bluetooth 5.1+")
                            appendLine("• Can achieve centimeter-level accuracy")
                            appendLine("• Requires compatible Bluetooth devices")
                            appendLine("• Uses antenna arrays for precise angle detection")
                        }
                        
                        RangingType.ACOUSTIC_FMCW -> {
                            appendLine("**Acoustic FMCW (Frequency Modulated Continuous Wave):**")
                            appendLine("• Uses ultrasonic signals for ranging")
                            appendLine("• Good for detecting surfaces and obstacles")
                            appendLine("• Can measure distance to any reflecting surface")
                            appendLine("• May be affected by ambient noise and temperature")
                        }
                    }
                    
                    appendLine()
                    val qualityAssessment = assessMeasurementQuality(measurement)
                    appendLine("**Quality Assessment:** $qualityAssessment")
                    
                    if (measurement.accuracy < 0.7f) {
                        appendLine()
                        appendLine("⚠️ **Recommendations:**")
                        appendLine("• This measurement has lower accuracy")
                        appendLine("• Try moving to a location with better signal conditions")
                        appendLine("• Ensure no obstructions between device and target")
                    }
                }
                
                AIResponse(
                    message = explanation,
                    responseType = AIResponseType.EXPLANATION,
                    confidence = 0.9f,
                    attachments = listOf(
                        MessageAttachment(
                            type = AttachmentType.SENSOR_DATA,
                            data = measurement,
                            description = "Raw sensor measurement data"
                        )
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error explaining measurement", e)
                createErrorResponse(e)
            }
        }
    }
    
    /**
     * Explain current 3D environment reconstruction
     */
    suspend fun explainEnvironment(pointCloud: List<Point3D>): AIResponse {
        return withContext(Dispatchers.Default) {
            try {
                val analysis = analyzePointCloud(pointCloud)
                
                val explanation = buildString {
                    appendLine("🏠 **Environmental Analysis**")
                    appendLine()
                    appendLine("**Point Cloud Statistics:**")
                    appendLine("• Total points: ${pointCloud.size}")
                    appendLine("• Spatial coverage: ${analysis.coverage}%")
                    appendLine("• Point density: ${analysis.density.format(2)} points/m²")
                    appendLine()
                    
                    if (analysis.estimatedRoomSize != null) {
                        appendLine("**Estimated Room Dimensions:**")
                        appendLine("• Width: ${analysis.estimatedRoomSize.width.format(1)}m")
                        appendLine("• Height: ${analysis.estimatedRoomSize.height.format(1)}m")  
                        appendLine("• Depth: ${analysis.estimatedRoomSize.depth.format(1)}m")
                        appendLine("• Volume: ${analysis.estimatedRoomSize.volume.format(1)}m³")
                        appendLine()
                    }
                    
                    if (analysis.detectedFeatures.isNotEmpty()) {
                        appendLine("**Detected Features:**")
                        analysis.detectedFeatures.forEach { feature ->
                            appendLine("• $feature")
                        }
                        appendLine()
                    }
                    
                    appendLine("**Mapping Quality:** ${analysis.quality}")
                    
                    if (analysis.recommendations.isNotEmpty()) {
                        appendLine()
                        appendLine("💡 **Recommendations:**")
                        analysis.recommendations.forEach { recommendation ->
                            appendLine("• $recommendation")
                        }
                    }
                }
                
                AIResponse(
                    message = explanation,
                    responseType = AIResponseType.ANALYSIS,
                    confidence = 0.85f,
                    attachments = listOf(
                        MessageAttachment(
                            type = AttachmentType.POINT_CLOUD,
                            data = pointCloud,
                            description = "3D point cloud data"
                        )
                    )
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error explaining environment", e)
                createErrorResponse(e)
            }
        }
    }
    
    private fun classifyUserIntent(message: String): UserIntent {
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.contains("sensor") || lowerMessage.contains("measurement") || 
            lowerMessage.contains("distance") || lowerMessage.contains("accuracy") -> UserIntent.EXPLAIN_SENSOR_DATA
            
            lowerMessage.contains("slam") || lowerMessage.contains("mapping") || 
            lowerMessage.contains("tracking") || lowerMessage.contains("position") -> UserIntent.EXPLAIN_SLAM_RESULTS
            
            lowerMessage.contains("status") || lowerMessage.contains("health") || 
            lowerMessage.contains("performance") -> UserIntent.SYSTEM_STATUS
            
            lowerMessage.contains("error") || lowerMessage.contains("problem") || 
            lowerMessage.contains("issue") || lowerMessage.contains("troubleshoot") || 
            lowerMessage.contains("fix") -> UserIntent.TROUBLESHOOTING
            
            lowerMessage.contains("environment") || lowerMessage.contains("room") || 
            lowerMessage.contains("3d") || lowerMessage.contains("visualization") -> UserIntent.ENVIRONMENTAL_ANALYSIS
            
            lowerMessage.contains("help") || lowerMessage.contains("how") || 
            lowerMessage.contains("what") || lowerMessage.contains("explain") -> UserIntent.GENERAL_HELP
            
            else -> UserIntent.CONVERSATIONAL
        }
    }
    
    private suspend fun explainSensorData(userMessage: String): AIResponse {
        val recentSensorData = conversationContext.currentSensorData.takeLast(5)
        
        if (recentSensorData.isEmpty()) {
            return AIResponse(
                message = "I don't have any recent sensor data to analyze. Start a mapping session to begin collecting environmental data, and I'll help you understand what the sensors are detecting.",
                responseType = AIResponseType.INFORMATIONAL,
                confidence = 0.9f
            )
        }
        
        val explanation = buildString {
            appendLine("📊 **Current Sensor Data Analysis**")
            appendLine()
            appendLine("I'm currently monitoring ${recentSensorData.size} recent sensor measurements:")
            appendLine()
            
            recentSensorData.forEach { measurement ->
                val quality = assessMeasurementQuality(measurement)
                val emoji = when (measurement.measurementType) {
                    RangingType.WIFI_RTT -> "📶"
                    RangingType.BLUETOOTH_CHANNEL_SOUNDING -> "🔵"
                    RangingType.ACOUSTIC_FMCW -> "🔊"
                }
                
                appendLine("$emoji **${measurement.measurementType.name.replace('_', ' ')}:**")
                appendLine("   Distance: ${measurement.distance.format(2)}m | Accuracy: ${(measurement.accuracy * 100).format(1)}% | Quality: $quality")
            }
            
            appendLine()
            val avgAccuracy = recentSensorData.map { it.accuracy }.average()
            when {
                avgAccuracy > 0.8 -> appendLine("✅ **Overall Assessment:** Excellent sensor performance! Your measurements are highly reliable.")
                avgAccuracy > 0.6 -> appendLine("✔️ **Overall Assessment:** Good sensor performance. Most measurements are reliable.")
                avgAccuracy > 0.4 -> appendLine("⚠️ **Overall Assessment:** Fair sensor performance. Some measurements may be less reliable.")
                else -> appendLine("❌ **Overall Assessment:** Poor sensor performance. Consider improving environmental conditions.")
            }
        }
        
        return AIResponse(
            message = explanation,
            responseType = AIResponseType.ANALYSIS,
            confidence = 0.8f,
            attachments = recentSensorData.map { 
                MessageAttachment(AttachmentType.SENSOR_DATA, it, "Sensor measurement") 
            }
        )
    }
    
    private suspend fun explainSLAMResults(userMessage: String): AIResponse {
        val slamState = conversationContext.currentSLAMState as? SLAMState
        
        if (slamState == null) {
            return AIResponse(
                message = "The SLAM (Simultaneous Localization and Mapping) system isn't currently active. Start a mapping session to begin tracking your position and building a 3D map of your environment. SLAM combines sensor data to understand both where you are and what's around you.",
                responseType = AIResponseType.INFORMATIONAL,
                confidence = 0.9f
            )
        }
        
        val explanation = buildString {
            appendLine("🗺️ **SLAM System Analysis**")
            appendLine()
            appendLine("**Current Position:**")
            appendLine("• X: ${slamState.devicePose.position.x.format(2)}m")
            appendLine("• Y: ${slamState.devicePose.position.y.format(2)}m")
            appendLine("• Z: ${slamState.devicePose.position.z.format(2)}m")
            appendLine()
            
            appendLine("**Tracking Status:**")
            val trackingQuality = assessTrackingQuality(slamState)
            appendLine("• Quality: $trackingQuality")
            appendLine("• Landmarks detected: ${slamState.landmarks.size}")
            appendLine("• Confidence: ${(slamState.confidence * 100).format(1)}%")
            appendLine()
            
            appendLine("**What this means:**")
            when (trackingQuality) {
                "Excellent" -> appendLine("🟢 Your position is being tracked very accurately. The system has a clear understanding of your location and the environment.")
                "Good" -> appendLine("🟡 Position tracking is working well. You might see occasional minor adjustments as the system refines its understanding.")
                "Fair" -> appendLine("🟠 Position tracking is functional but could be improved. Try moving slowly and pointing at distinctive features.")
                else -> appendLine("🔴 Position tracking is struggling. Consider moving to an area with more features or checking sensor functionality.")
            }
        }
        
        return AIResponse(
            message = explanation,
            responseType = AIResponseType.ANALYSIS,
            confidence = 0.85f,
            attachments = listOf(
                MessageAttachment(AttachmentType.SLAM_STATE, slamState, "Current SLAM state")
            )
        )
    }
    
    private suspend fun provideSystemStatus(userMessage: String): AIResponse {
        val systemHealth = conversationContext.systemHealth
        
        val status = buildString {
            appendLine("⚡ **System Status Report**")
            appendLine()
            
            if (systemHealth != null) {
                val healthPercentage = (systemHealth.overallHealth * 100).toInt()
                val healthEmoji = when {
                    healthPercentage >= 90 -> "🟢"
                    healthPercentage >= 70 -> "🟡"
                    healthPercentage >= 50 -> "🟠"
                    else -> "🔴"
                }
                
                appendLine("$healthEmoji **Overall Health: ${healthPercentage}%**")
                appendLine()
                
                if (systemHealth.issues.isNotEmpty()) {
                    appendLine("**Issues Detected:**")
                    systemHealth.issues.forEach { issue ->
                        appendLine("• $issue")
                    }
                    appendLine()
                }
                
                if (systemHealth.recommendations.isNotEmpty()) {
                    appendLine("**Recommendations:**")
                    systemHealth.recommendations.forEach { recommendation ->
                        appendLine("• $recommendation")
                    }
                    appendLine()
                }
                
                if (systemHealth.performanceMetrics.isNotEmpty()) {
                    appendLine("**Performance Metrics:**")
                    systemHealth.performanceMetrics.forEach { (key, value) ->
                        val displayKey = key.replace('_', ' ').replaceFirstChar { it.uppercase() }
                        when {
                            key.contains("rate") -> appendLine("• $displayKey: ${(value * 100).format(1)}%")
                            key.contains("usage") -> appendLine("• $displayKey: ${(value * 100).format(1)}%")
                            else -> appendLine("• $displayKey: ${value.format(2)}")
                        }
                    }
                }
            } else {
                appendLine("🔄 **Status:** System monitoring is initializing...")
                appendLine()
                appendLine("I'm currently setting up system monitoring. Give me a moment to analyze your device's performance and I'll provide detailed insights.")
            }
        }
        
        return AIResponse(
            message = status,
            responseType = AIResponseType.STATUS,
            confidence = 0.9f,
            attachments = systemHealth?.let { 
                listOf(MessageAttachment(AttachmentType.SYSTEM_LOGS, it, "System health report"))
            } ?: emptyList()
        )
    }
    
    private suspend fun provideTroubleshooting(userMessage: String): AIResponse {
        val recentInsights = conversationContext.recentInsights.takeLast(3)
        
        val troubleshooting = buildString {
            appendLine("🔧 **Troubleshooting Assistant**")
            appendLine()
            
            if (recentInsights.isNotEmpty()) {
                appendLine("**Recent Issues Detected:**")
                recentInsights.forEach { insight ->
                    val severityEmoji = when (insight.severity) {
                        SeverityLevel.CRITICAL -> "🚨"
                        SeverityLevel.ERROR -> "❌"
                        SeverityLevel.WARNING -> "⚠️"
                        SeverityLevel.INFO -> "ℹ️"
                    }
                    
                    appendLine("$severityEmoji ${insight.message}")
                    if (insight.data.containsKey("recommendation")) {
                        appendLine("   💡 ${insight.data["recommendation"]}")
                    }
                }
                appendLine()
            }
            
            appendLine("**Common Solutions:**")
            appendLine("🔹 **Sensor Issues:** Check app permissions, ensure WiFi/Bluetooth are enabled")
            appendLine("🔹 **3D Visualization Problems:** Verify OpenGL ES support, try restarting the app")
            appendLine("🔹 **Poor Mapping Quality:** Move slowly, point at walls and objects, ensure good lighting")
            appendLine("🔹 **Performance Issues:** Close background apps, reduce 3D quality settings")
            appendLine("🔹 **Connection Problems:** Check network connectivity, restart WiFi/Bluetooth")
            appendLine()
            
            appendLine("Tell me more about the specific issue you're experiencing, and I can provide targeted help!")
        }
        
        return AIResponse(
            message = troubleshooting,
            responseType = AIResponseType.TROUBLESHOOTING,
            confidence = 0.8f
        )
    }
    
    private suspend fun provideEnvironmentalAnalysis(userMessage: String): AIResponse {
        val environment = conversationContext.currentEnvironment
        
        if (environment.isEmpty()) {
            return AIResponse(
                message = "I haven't detected any environmental data yet. Start mapping your surroundings and I'll analyze the 3D structure, identify objects, and provide insights about your environment. The more you map, the better I can understand your space!",
                responseType = AIResponseType.INFORMATIONAL,
                confidence = 0.9f
            )
        }
        
        return explainEnvironment(environment)
    }
    
    private suspend fun provideGeneralHelp(userMessage: String): AIResponse {
        val help = buildString {
            appendLine("🤖 **Environmental AI Assistant Help**")
            appendLine()
            appendLine("I'm here to help you understand your environmental imaging app! Here's what I can do:")
            appendLine()
            appendLine("📊 **Sensor Analysis:**")
            appendLine("• Explain sensor measurements and accuracy")
            appendLine("• Identify measurement anomalies")
            appendLine("• Suggest improvements for better data quality")
            appendLine()
            appendLine("🗺️ **SLAM & Mapping:**")
            appendLine("• Explain your current position and tracking")
            appendLine("• Analyze mapping quality and progress")
            appendLine("• Provide tips for better environmental mapping")
            appendLine()
            appendLine("🏠 **Environmental Analysis:**")
            appendLine("• Analyze your 3D reconstructed environment")
            appendLine("• Estimate room dimensions and detect objects")
            appendLine("• Explain point cloud data and spatial coverage")
            appendLine()
            appendLine("⚡ **System Monitoring:**")
            appendLine("• Check system health and performance")
            appendLine("• Identify and troubleshoot issues")
            appendLine("• Provide optimization recommendations")
            appendLine()
            appendLine("**Try asking me:**")
            appendLine("• \"How accurate are my sensors?\"")
            appendLine("• \"What does my current environment look like?\"")
            appendLine("• \"Why is my mapping quality poor?\"")
            appendLine("• \"What issues does my system have?\"")
        }
        
        return AIResponse(
            message = help,
            responseType = AIResponseType.HELP,
            confidence = 0.95f
        )
    }
    
    private suspend fun provideConversationalResponse(userMessage: String): AIResponse {
        // Simple conversational responses for general chat
        val responses = listOf(
            "I'm here to help you understand your environmental imaging data! What would you like to know about your sensors, mapping, or 3D environment?",
            "That's interesting! Is there anything specific about your environmental mapping or sensor data you'd like me to analyze?",
            "I'm always ready to help with technical questions about your environmental imaging system. What can I explain for you?",
            "Feel free to ask me about sensor readings, SLAM performance, 3D visualization, or any issues you're experiencing!"
        )
        
        return AIResponse(
            message = responses.random(),
            responseType = AIResponseType.CONVERSATIONAL,
            confidence = 0.7f
        )
    }
    
    // Helper functions
    private fun createWelcomeMessage(): AIResponse {
        return AIResponse(
            message = "👋 Hello! I'm your Environmental AI Assistant. I'm here to help you understand sensor data, analyze your 3D environment, troubleshoot issues, and optimize your environmental imaging experience. What would you like to know?",
            responseType = AIResponseType.WELCOME,
            confidence = 1.0f
        )
    }
    
    private fun createErrorResponse(error: Exception): AIResponse {
        return AIResponse(
            message = "I apologize, but I encountered an error while processing your request. Please try again, or ask me something else about your environmental imaging system.",
            responseType = AIResponseType.ERROR,
            confidence = 0.5f
        )
    }
    
    private fun assessMeasurementQuality(measurement: RangingMeasurement): String {
        return when {
            measurement.accuracy > 0.8f -> "Excellent"
            measurement.accuracy > 0.6f -> "Good"
            measurement.accuracy > 0.4f -> "Fair"
            else -> "Poor"
        }
    }
    
    private fun assessTrackingQuality(slamState: SLAMState): String {
        return when {
            slamState.confidence > 0.8f && slamState.landmarks.size > 10 -> "Excellent"
            slamState.confidence > 0.6f && slamState.landmarks.size > 5 -> "Good"
            slamState.confidence > 0.4f && slamState.landmarks.size > 2 -> "Fair"
            else -> "Poor"
        }
    }
    
    private fun analyzePointCloud(pointCloud: List<Point3D>): PointCloudAnalysis {
        // Simple analysis - in a real implementation, this would be more sophisticated
        val xValues = pointCloud.map { it.x }
        val yValues = pointCloud.map { it.y }
        val zValues = pointCloud.map { it.z }
        
        val roomSize = if (pointCloud.size > 10) {
            RoomDimensions(
                width = (xValues.maxOrNull() ?: 0f) - (xValues.minOrNull() ?: 0f),
                height = (yValues.maxOrNull() ?: 0f) - (yValues.minOrNull() ?: 0f),
                depth = (zValues.maxOrNull() ?: 0f) - (zValues.minOrNull() ?: 0f),
                volume = 0f // Would calculate properly in real implementation
            )
        } else null
        
        val density = pointCloud.size / 100f // Simplified density calculation
        val coverage = (pointCloud.size / 1000f * 100f).coerceAtMost(100f)
        
        val quality = when {
            coverage > 80f -> "Excellent"
            coverage > 60f -> "Good"
            coverage > 40f -> "Fair"
            else -> "Poor"
        }
        
        val recommendations = mutableListOf<String>()
        if (coverage < 60f) {
            recommendations.add("Continue mapping to improve coverage")
        }
        if (pointCloud.size < 100) {
            recommendations.add("Move around more to collect additional data points")
        }
        
        return PointCloudAnalysis(
            estimatedRoomSize = roomSize,
            density = density,
            coverage = coverage,
            quality = quality,
            detectedFeatures = listOf("Floor surface", "Wall structures"), // Simplified
            recommendations = recommendations
        )
    }
    
    private fun generateSensorSuggestions(): List<AISuggestion> {
        return listOf(
            AISuggestion(
                text = "Explain my current sensor readings",
                priority = SuggestionPriority.HIGH,
                category = "Sensor Analysis"
            ),
            AISuggestion(
                text = "Check sensor accuracy and quality",
                priority = SuggestionPriority.MEDIUM,
                category = "Sensor Analysis"
            )
        )
    }
    
    private fun generateEnvironmentSuggestions(): List<AISuggestion> {
        return listOf(
            AISuggestion(
                text = "Analyze my 3D environment",
                priority = SuggestionPriority.HIGH,
                category = "Environmental Analysis"
            ),
            AISuggestion(
                text = "What objects have been detected?",
                priority = SuggestionPriority.MEDIUM,
                category = "Environmental Analysis"
            )
        )
    }
    
    private fun generateInsightSuggestions(): List<AISuggestion> {
        return listOf(
            AISuggestion(
                text = "Explain recent system insights",
                priority = SuggestionPriority.HIGH,
                category = "System Analysis"
            )
        )
    }
    
    private fun generateHealthSuggestions(): List<AISuggestion> {
        return listOf(
            AISuggestion(
                text = "Show system health status",
                priority = SuggestionPriority.MEDIUM,
                category = "System Status"
            )
        )
    }
    
    private fun getDefaultSuggestions(): List<AISuggestion> {
        return listOf(
            AISuggestion("How does environmental imaging work?", SuggestionPriority.MEDIUM, "General Help"),
            AISuggestion("What sensors are being used?", SuggestionPriority.MEDIUM, "Sensor Analysis"),
            AISuggestion("Help me troubleshoot issues", SuggestionPriority.LOW, "Troubleshooting")
        )
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val seconds = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }
    
    private fun Float.format(decimals: Int) = "%.${decimals}f".format(this)
    
    private fun generateMessageId(): String = System.currentTimeMillis().toString()
    
    private fun createKnowledgeBase(): Map<String, String> {
        return mapOf(
            "wifi_rtt" to "WiFi RTT (Round Trip Time) measures the time it takes for a signal to travel to a WiFi access point and back, allowing distance calculation.",
            "bluetooth_ranging" to "Bluetooth Channel Sounding uses advanced antenna techniques in Bluetooth 5.1+ for precise distance and direction finding.",
            "acoustic_fmcw" to "Acoustic FMCW uses frequency-modulated sound waves to measure distance to surfaces and objects.",
            "slam" to "SLAM (Simultaneous Localization and Mapping) tracks your position while building a 3D map of your environment.",
            "point_cloud" to "A point cloud is a collection of 3D points that represent the surfaces and objects in your environment."
        )
    }
    
    fun shutdown() {
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

// Additional data classes
enum class UserIntent {
    EXPLAIN_SENSOR_DATA,
    EXPLAIN_SLAM_RESULTS,
    SYSTEM_STATUS,
    TROUBLESHOOTING,
    ENVIRONMENTAL_ANALYSIS,
    GENERAL_HELP,
    CONVERSATIONAL
}

data class AIResponse(
    val message: String,
    val responseType: AIResponseType,
    val confidence: Float,
    val attachments: List<MessageAttachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class AIResponseType {
    WELCOME,
    EXPLANATION,
    ANALYSIS,
    STATUS,
    TROUBLESHOOTING,
    HELP,
    CONVERSATIONAL,
    ERROR,
    INFORMATIONAL
}

data class AISuggestion(
    val text: String,
    val priority: SuggestionPriority,
    val category: String
)

enum class SuggestionPriority {
    LOW, MEDIUM, HIGH
}

data class PointCloudAnalysis(
    val estimatedRoomSize: RoomDimensions?,
    val density: Float,
    val coverage: Float,
    val quality: String,
    val detectedFeatures: List<String>,
    val recommendations: List<String>
)