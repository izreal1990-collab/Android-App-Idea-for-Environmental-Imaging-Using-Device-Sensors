package com.environmentalimaging.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.environmentalimaging.app.ai.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Conversational AI Activity
 * Natural language interface for environmental imaging
 */
class ConversationalAIActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ConversationalAI"
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
        
        fun createIntent(context: Context): Intent {
            return Intent(context, ConversationalAIActivity::class.java)
        }
    }
    
    // UI Components
    private lateinit var conversationRecyclerView: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private lateinit var inputLayout: TextInputLayout
    private lateinit var inputEditText: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var voiceButton: FloatingActionButton
    private lateinit var suggestionsChipGroup: ChipGroup
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var contextCard: MaterialCardView
    private lateinit var contextText: TextView
    
    // Voice Recognition
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    
    // AI Components
    private lateinit var conversationalAI: ConversationalAISystem
    private lateinit var aiAnalysisEngine: AIAnalysisEngine
    private lateinit var environmentalAssistant: EnvironmentalAIAssistant
    
    // Conversation State
    private val conversationEntries = mutableListOf<ConversationEntry>()
    private var isProcessing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_conversational_ai)
        
        initializeUI()
        initializeAISystem()
        setupEventListeners()
        checkAudioPermission()
        startConversation()
    }
    
    private fun initializeUI() {
        // Conversation display
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView)
        conversationAdapter = ConversationAdapter(conversationEntries) { suggestion ->
            processUserInput(suggestion)
        }
        conversationRecyclerView.adapter = conversationAdapter
        conversationRecyclerView.layoutManager = LinearLayoutManager(this)
        
        // Input components
        inputLayout = findViewById(R.id.inputLayout)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        
        // Suggestions
        suggestionsChipGroup = findViewById(R.id.suggestionsChipGroup)
        
        // Status and context
        statusCard = findViewById(R.id.statusCard)
        statusText = findViewById(R.id.statusText)
        contextCard = findViewById(R.id.contextCard)
        contextText = findViewById(R.id.contextText)
        
        // Setup toolbar
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
            title = "AI Assistant"
        }
        
        updateStatus("Initializing AI Assistant...")
    }
    
    private fun initializeAISystem() {
        aiAnalysisEngine = AIAnalysisEngine(this)
        environmentalAssistant = EnvironmentalAIAssistant(this)
        conversationalAI = ConversationalAISystem(this, aiAnalysisEngine, environmentalAssistant)
        conversationalAI.initialize()
        
        // Initialize speech recognizer
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
        }
    }
    
    private fun setupEventListeners() {
        // Send button
        sendButton.setOnClickListener {
            val input = inputEditText.text?.toString()?.trim()
            if (!input.isNullOrEmpty()) {
                processUserInput(input)
                inputEditText.text?.clear()
            }
        }
        
        // Voice button
        voiceButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
        
        // Text input watcher
        inputEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.isNullOrEmpty() && !isProcessing
            }
        })
    }
    
    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        }
    }
    
    private fun startConversation() {
        lifecycleScope.launch {
            updateStatus("AI Assistant ready")
            updateContextDisplay()
            updateSuggestions()
            
            // Add welcome message
            addAIMessage("Hello! I'm your Environmental Imaging AI Assistant. I can help you with scanning, analysis, visualization, and more. What would you like to do today?")
        }
    }
    
    private fun processUserInput(input: String) {
        if (isProcessing) return
        
        lifecycleScope.launch {
            isProcessing = true
            updateStatus("Processing...")
            updateUI()
            
            // Add user message to conversation
            addUserMessage(input)
            
            try {
                // Process with AI
                val response = conversationalAI.processUserInput(input)
                
                // Add AI response to conversation
                addAIMessage(response.text)
                
                // Speak response if enabled
                conversationalAI.speakResponse(response.text)
                
                // Update suggestions
                updateSuggestions(response.suggestions)
                
                // Handle actions
                handleAIActions(response.actions)
                
                // Show follow-up questions if any
                if (response.followUpQuestions.isNotEmpty()) {
                    showFollowUpQuestions(response.followUpQuestions)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing input", e)
                addAIMessage("I'm sorry, I encountered an error. Please try again.")
            } finally {
                isProcessing = false
                updateStatus("Ready")
                updateUI()
                updateContextDisplay()
            }
        }
    }
    
    private fun addUserMessage(message: String) {
        val entry = ConversationEntry(
            type = ConversationEntry.Type.USER,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        conversationEntries.add(entry)
        conversationAdapter.notifyItemInserted(conversationEntries.size - 1)
        scrollToBottom()
    }
    
    private fun addAIMessage(message: String) {
        val entry = ConversationEntry(
            type = ConversationEntry.Type.AI,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        conversationEntries.add(entry)
        conversationAdapter.notifyItemInserted(conversationEntries.size - 1)
        scrollToBottom()
    }
    
    private fun updateSuggestions(suggestions: List<String>? = null) {
        val suggestionsToShow = suggestions ?: conversationalAI.getContextualSuggestions()
        
        suggestionsChipGroup.removeAllViews()
        
        suggestionsToShow.take(6).forEach { suggestion ->
            val chip = Chip(this).apply {
                text = suggestion
                isClickable = true
                setOnClickListener {
                    processUserInput(suggestion)
                }
            }
            suggestionsChipGroup.addView(chip)
        }
    }
    
    private fun updateStatus(status: String) {
        statusText.text = status
    }
    
    private fun updateContextDisplay() {
        lifecycleScope.launch {
            // Get current environmental state for context
            // This would typically come from MainActivity or a shared state manager
            val contextInfo = """
                Session: Active
                Scan Status: Ready
                Features: All enabled
                Voice: ${if (conversationalAI.getVoiceSettings().second) "On" else "Off"}
            """.trimIndent()
            
            contextText.text = contextInfo
        }
    }
    
    private fun updateUI() {
        sendButton.isEnabled = !inputEditText.text.isNullOrEmpty() && !isProcessing
        voiceButton.isEnabled = !isProcessing
    }
    
    private fun scrollToBottom() {
        if (conversationEntries.isNotEmpty()) {
            conversationRecyclerView.smoothScrollToPosition(conversationEntries.size - 1)
        }
    }
    
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            checkAudioPermission()
            return
        }
        
        isListening = true
        voiceButton.setImageResource(R.drawable.ic_mic_off)
        updateStatus("Listening...")
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask me anything about environmental imaging...")
        }
        
        speechRecognizer?.startListening(intent)
    }
    
    private fun stopListening() {
        isListening = false
        voiceButton.setImageResource(R.drawable.ic_mic)
        updateStatus("Ready")
        speechRecognizer?.stopListening()
    }
    
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                updateStatus("Listening... Speak now")
            }
            
            override fun onBeginningOfSpeech() {
                updateStatus("Processing speech...")
            }
            
            override fun onRmsChanged(rmsdB: Float) {}
            
            override fun onBufferReceived(buffer: ByteArray?) {}
            
            override fun onEndOfSpeech() {
                updateStatus("Processing...")
            }
            
            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech input"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                    SpeechRecognizer.ERROR_SERVER -> "Error from server"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }
                
                Log.e(TAG, "Speech recognition error: $errorMessage")
                stopListening()
                Toast.makeText(this@ConversationalAIActivity, "Speech recognition error: $errorMessage", Toast.LENGTH_SHORT).show()
            }
            
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spokenText = matches[0]
                    processUserInput(spokenText)
                }
                stopListening()
            }
            
            override fun onPartialResults(partialResults: Bundle?) {}
            
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }
    
    private fun handleAIActions(actions: List<ConversationalAISystem.ConversationAction>) {
        actions.forEach { action ->
            when (action.type) {
                ConversationalAISystem.ActionType.START_SCAN -> {
                    // Trigger scan start - would communicate with MainActivity
                    showActionConfirmation("Start scanning with ${action.description}?") {
                        Toast.makeText(this, "Starting scan...", Toast.LENGTH_SHORT).show()
                    }
                }
                ConversationalAISystem.ActionType.STOP_SCAN -> {
                    showActionConfirmation("Stop current scan?") {
                        Toast.makeText(this, "Stopping scan...", Toast.LENGTH_SHORT).show()
                    }
                }
                ConversationalAISystem.ActionType.SHOW_STATISTICS -> {
                    // Open performance dashboard
                    val intent = Intent(this, PerformanceDashboardActivity::class.java)
                    startActivity(intent)
                }
                ConversationalAISystem.ActionType.ADJUST_VISUALIZATION -> {
                    // Open enhanced 3D visualization
                    val intent = Intent(this, Enhanced3DVisualizationActivity::class.java)
                    startActivity(intent)
                }
                ConversationalAISystem.ActionType.EXPORT_DATA -> {
                    Toast.makeText(this, "Export functionality will be available soon", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Log.d(TAG, "Unhandled action: ${action.type}")
                }
            }
        }
    }
    
    private fun showActionConfirmation(message: String, onConfirm: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onConfirm() }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun showFollowUpQuestions(questions: List<String>) {
        if (questions.isNotEmpty()) {
            val followUpMessage = "Follow-up questions:\n" + questions.mapIndexed { index, question ->
                "${index + 1}. $question"
            }.joinToString("\n")
            
            addAIMessage(followUpMessage)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    Toast.makeText(this, "Voice input enabled", Toast.LENGTH_SHORT).show()
                } else {
                    // Permission denied
                    Toast.makeText(this, "Voice input requires microphone permission", Toast.LENGTH_SHORT).show()
                    voiceButton.visibility = View.GONE
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        conversationalAI.cleanup()
    }
    
    /**
     * Conversation Entry Data Class
     */
    data class ConversationEntry(
        val type: Type,
        val message: String,
        val timestamp: Long,
        val suggestions: List<String> = emptyList()
    ) {
        enum class Type {
            USER, AI
        }
        
        fun getFormattedTime(): String {
            val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
            return formatter.format(Date(timestamp))
        }
    }
}