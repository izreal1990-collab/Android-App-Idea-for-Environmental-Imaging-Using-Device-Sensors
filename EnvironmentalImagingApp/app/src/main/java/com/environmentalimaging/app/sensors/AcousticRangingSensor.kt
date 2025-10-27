package com.environmentalimaging.app.sensors

import android.content.Context
import android.media.*
import android.util.Log
import com.environmentalimaging.app.data.RangingMeasurement
import com.environmentalimaging.app.data.RangingType
import kotlinx.coroutines.*
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import kotlin.math.*

/**
 * Acoustic ranging sensor using FMCW (Frequency Modulated Continuous Wave) technique
 * Based on SAMS (Smartphone Acoustic Mapping System) research
 * Uses smartphone speakers and microphones for distance measurement to walls and objects
 */
class AcousticRangingSensor(private val context: Context) {
    
    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    companion object {
        private const val TAG = "AcousticRangingSensor"
        
        // Audio configuration
        private const val SAMPLE_RATE = 48000 // Hz
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // FMCW parameters
        private const val CHIRP_DURATION_MS = 100 // Duration of each chirp
        private const val FREQUENCY_START = 18000f // Start frequency in Hz
        private const val FREQUENCY_END = 22000f // End frequency in Hz
        private const val SPEED_OF_SOUND = 343.0f // m/s at room temperature
        
        // Processing parameters
        private const val FFT_SIZE = 4096
        private const val MIN_PEAK_HEIGHT = 0.1f
        private const val MAX_DETECTION_DISTANCE = 10.0f // meters
        
        // Multipath rejection parameters
        private const val MIN_PEAK_SEPARATION_SAMPLES = 100 // Minimum samples between valid peaks
        private const val SECONDARY_PEAK_RATIO = 0.3f // Reject secondary peaks below this ratio of primary
    }
    
    /**
     * Check if acoustic ranging is available
     */
    fun isAvailable(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking audio availability", e)
            false
        }
    }
    
    /**
     * Initialize audio components
     */
    private fun initializeAudio(): Boolean {
        try {
            // Calculate buffer sizes
            val playBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG_OUT, AUDIO_FORMAT
            )
            val recordBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT
            )
            
            if (playBufferSize == AudioTrack.ERROR || recordBufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Error calculating audio buffer sizes")
                return false
            }
            
            // Initialize AudioTrack for playback
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_OUT)
                        .build()
                )
                .setBufferSizeInBytes(playBufferSize * 2)
                .build()
            
            // Initialize AudioRecord for recording
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED) // Raw audio for better analysis
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG_IN)
                        .build()
                )
                .setBufferSizeInBytes(recordBufferSize * 2)
                .build()
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing audio", e)
            return false
        }
    }
    
    /**
     * Generate FMCW chirp signal
     */
    private fun generateChirp(): ShortArray {
        val samples = (SAMPLE_RATE * CHIRP_DURATION_MS / 1000).toInt()
        val chirp = ShortArray(samples)
        val frequencySlope = (FREQUENCY_END - FREQUENCY_START) / (CHIRP_DURATION_MS / 1000.0)
        
        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val frequency = FREQUENCY_START + frequencySlope * t
            val amplitude = 0.8 * Short.MAX_VALUE // 80% of max amplitude
            chirp[i] = (amplitude * sin(2.0 * PI * frequency * t)).toInt().toShort()
        }
        
        return chirp
    }
    
    /**
     * Perform single acoustic ranging measurement
     */
    suspend fun performSingleRanging(): List<RangingMeasurement> = withContext(Dispatchers.IO) {
        try {
            if (!initializeAudio()) {
                Log.e(TAG, "Failed to initialize audio")
                return@withContext emptyList()
            }
            
            val chirp = generateChirp()
            val recordBuffer = ShortArray(SAMPLE_RATE * 2) // 2 seconds of recording
            
            // Start recording
            audioRecord?.startRecording()
            isRecording = true
            
            // Start recording in background
            val recordingDeferred = async {
                var totalRead = 0
                while (isRecording && totalRead < recordBuffer.size) {
                    val read = audioRecord?.read(
                        recordBuffer, 
                        totalRead, 
                        recordBuffer.size - totalRead
                    ) ?: 0
                    if (read > 0) {
                        totalRead += read
                    }
                }
                recordBuffer
            }
            
            // Play chirp after short delay to ensure recording started
            delay(100)
            audioTrack?.play()
            audioTrack?.write(chirp, 0, chirp.size)
            
            // Wait for recording to complete
            delay(CHIRP_DURATION_MS.toLong() + 500) // Chirp duration + echo time
            isRecording = false
            
            val recordedData = recordingDeferred.await()
            
            // Process recorded data to find echoes
            val measurements = processEchoes(recordedData, chirp)
            
            // Cleanup
            audioTrack?.stop()
            audioRecord?.stop()
            
            Log.d(TAG, "Acoustic ranging completed: ${measurements.size} measurements")
            return@withContext measurements
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing acoustic ranging", e)
            return@withContext emptyList()
        } finally {
            isRecording = false
            audioTrack?.release()
            audioRecord?.release()
            audioTrack = null
            audioRecord = null
        }
    }
    
    /**
     * Process recorded audio to detect echoes and calculate distances
     */
    private fun processEchoes(recordedData: ShortArray, chirp: ShortArray): List<RangingMeasurement> {
        try {
            // Cross-correlate recorded signal with transmitted chirp
            val correlation = crossCorrelate(recordedData, chirp)
            
            // Find peaks in correlation (potential echoes)
            val rawPeaks = findPeaks(correlation, MIN_PEAK_HEIGHT)
            
            // Apply multipath rejection - filter out secondary reflections
            val filteredPeaks = rejectMultipathPeaks(rawPeaks)
            
            val measurements = mutableListOf<RangingMeasurement>()
            val currentTime = System.currentTimeMillis()
            
            for ((peakIndex, amplitude) in filteredPeaks) {
                // Convert sample index to time delay
                val timeDelay = peakIndex.toDouble() / SAMPLE_RATE
                
                // Calculate distance (divide by 2 for round-trip)
                val distance = (timeDelay * SPEED_OF_SOUND / 2.0).toFloat()
                
                // Filter reasonable distances
                if (distance > 0.1f && distance < MAX_DETECTION_DISTANCE) {
                    // Estimate accuracy based on peak amplitude and frequency resolution
                    val accuracy = estimateAccuracy(amplitude, distance)
                    
                    val measurement = RangingMeasurement(
                        sourceId = "acoustic_reflector_$peakIndex",
                        distance = distance,
                        accuracy = accuracy,
                        timestamp = currentTime,
                        measurementType = RangingType.ACOUSTIC_FMCW
                    )
                    measurements.add(measurement)
                    
                    Log.d(TAG, "Echo detected: distance=${distance}m, accuracy=${accuracy}m, amplitude=$amplitude")
                }
            }
            
            return measurements
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing echoes", e)
            return emptyList()
        }
    }
    
    /**
     * Reject multipath peaks - keep only direct reflections
     */
    private fun rejectMultipathPeaks(peaks: List<Pair<Int, Float>>): List<Pair<Int, Float>> {
        if (peaks.isEmpty()) return emptyList()
        
        val filtered = mutableListOf<Pair<Int, Float>>()
        val primaryPeak = peaks.first() // Strongest peak (sorted by amplitude)
        filtered.add(primaryPeak)
        
        // Add secondary peaks only if they meet criteria
        for (i in 1 until peaks.size) {
            val (peakIndex, amplitude) = peaks[i]
            
            // Check if peak is sufficiently separated from previous peaks
            val isSeparated = filtered.all { (existingIndex, _) ->
                abs(peakIndex - existingIndex) >= MIN_PEAK_SEPARATION_SAMPLES
            }
            
            // Check if amplitude is significant (not just a multipath ghost)
            val isSignificant = amplitude >= primaryPeak.second * SECONDARY_PEAK_RATIO
            
            if (isSeparated && isSignificant) {
                filtered.add(peaks[i])
                Log.d(TAG, "Accepted secondary peak at index $peakIndex (amplitude=$amplitude)")
            } else {
                Log.d(TAG, "Rejected multipath peak at index $peakIndex (amplitude=$amplitude)")
            }
        }
        
        return filtered
    }
    
    /**
     * Cross-correlate two signals
     */
    private fun crossCorrelate(signal1: ShortArray, signal2: ShortArray): FloatArray {
        val fft = FastFourierTransformer(DftNormalization.STANDARD)
        val size = Integer.highestOneBit(signal1.size + signal2.size - 1) * 2
        
        // Pad signals to FFT size
        val padded1 = Array(size) { i -> 
            if (i < signal1.size) Complex(signal1[i].toDouble(), 0.0) else Complex.ZERO 
        }
        val padded2 = Array(size) { i -> 
            if (i < signal2.size) Complex(signal2[i].toDouble(), 0.0) else Complex.ZERO 
        }
        
        // FFT both signals
        val fft1 = fft.transform(padded1, TransformType.FORWARD)
        val fft2 = fft.transform(padded2, TransformType.FORWARD)
        
        // Multiply first by conjugate of second
        val product = Array(size) { i -> fft1[i].multiply(fft2[i].conjugate()) }
        
        // IFFT to get correlation
        val correlation = fft.transform(product, TransformType.INVERSE)
        
        return FloatArray(size) { i -> correlation[i].abs().toFloat() }
    }
    
    /**
     * Find peaks in correlation function
     */
    private fun findPeaks(data: FloatArray, minHeight: Float): List<Pair<Int, Float>> {
        val peaks = mutableListOf<Pair<Int, Float>>()
        
        for (i in 1 until data.size - 1) {
            if (data[i] > minHeight && data[i] > data[i-1] && data[i] > data[i+1]) {
                peaks.add(Pair(i, data[i]))
            }
        }
        
        // Sort by amplitude (strongest peaks first)
        return peaks.sortedByDescending { it.second }
    }
    
    /**
     * Estimate measurement accuracy based on peak characteristics
     */
    private fun estimateAccuracy(amplitude: Float, distance: Float): Float {
        // Accuracy decreases with distance and lower amplitude
        val baseAccuracy = 0.015f // 1.5 cm base accuracy from research
        val distanceFactor = distance / 5.0f // Accuracy degrades with distance
        val amplitudeFactor = (1.0f - amplitude) * 2.0f // Lower amplitude = less accuracy
        
        return (baseAccuracy * (1.0f + distanceFactor + amplitudeFactor)).coerceAtMost(0.5f)
    }
    
    /**
     * Start continuous acoustic ranging
     */
    fun startContinuousRanging(
        intervalMs: Long = 3000, // Longer interval to avoid audio interference
        onMeasurement: (List<RangingMeasurement>) -> Unit
    ) {
        recordingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val measurements = performSingleRanging()
                    onMeasurement(measurements)
                    delay(intervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in continuous ranging", e)
                    delay(5000) // Wait longer on error
                }
            }
        }
        Log.d(TAG, "Continuous acoustic ranging started")
    }
    
    /**
     * Stop continuous ranging
     */
    fun stopContinuousRanging() {
        recordingJob?.cancel()
        recordingJob = null
        isRecording = false
        Log.d(TAG, "Continuous acoustic ranging stopped")
    }
}