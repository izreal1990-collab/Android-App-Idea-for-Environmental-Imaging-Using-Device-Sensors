package com.environmentalimaging.app.sensors

import android.content.Context
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.environmentalimaging.app.data.RangingMeasurement
import com.environmentalimaging.app.data.RangingType
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WiFi RTT (Round-Trip Time) sensor implementation for distance measurement
 * Based on IEEE 802.11mc standard for indoor positioning
 * Requires Android 9+ (API level 28)
 */
@RequiresApi(Build.VERSION_CODES.P)
class WiFiRttSensor(private val context: Context) {
    
    private val wifiRttManager: WifiRttManager? by lazy {
        context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
    }
    
    private val wifiManager: WifiManager by lazy {
        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    
    companion object {
        private const val TAG = "WiFiRttSensor"
        private const val MAX_RANGING_REQUESTS = 10 // Limit concurrent requests
        private const val MIN_ACCURACY_METERS = 10.0f // Filter out inaccurate measurements
    }
    
    /**
     * Check if WiFi RTT is available on this device
     */
    fun isAvailable(): Boolean {
        return wifiRttManager?.isAvailable == true && wifiManager.isWifiEnabled
    }
    
    /**
     * Scan for RTT-capable access points
     */
    suspend fun scanForRttCapableAPs(): List<ScanResult> = suspendCancellableCoroutine { continuation ->
        try {
            if (!isAvailable()) {
                Log.w(TAG, "WiFi RTT not available")
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            
            val scanResults = wifiManager.scanResults
            val rttCapableAPs = scanResults.filter { scanResult ->
                // Check if AP supports RTT (802.11mc)
                scanResult.is80211mcResponder
            }
            
            Log.d(TAG, "Found ${rttCapableAPs.size} RTT-capable access points")
            continuation.resume(rttCapableAPs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for RTT APs", e)
            continuation.resume(emptyList())
        }
    }
    
    /**
     * Perform ranging measurements to specified access points
     */
    suspend fun performRanging(accessPoints: List<ScanResult>): List<RangingMeasurement> = 
        suspendCancellableCoroutine { continuation ->
            try {
                if (!isAvailable() || accessPoints.isEmpty()) {
                    continuation.resume(emptyList())
                    return@suspendCancellableCoroutine
                }
                
                // Limit the number of APs to avoid overwhelming the system
                val limitedAPs = accessPoints.take(MAX_RANGING_REQUESTS)
                
                val rangingRequest = RangingRequest.Builder()
                    .addAccessPoints(limitedAPs)
                    .build()
                
                wifiRttManager?.startRanging(
                    rangingRequest,
                    context.mainExecutor,
                    object : RangingResultCallback() {
                        override fun onRangingResults(results: List<RangingResult>) {
                            val measurements = processRangingResults(results)
                            Log.d(TAG, "Ranging completed: ${measurements.size} successful measurements")
                            continuation.resume(measurements)
                        }
                        
                        override fun onRangingFailure(code: Int) {
                            Log.w(TAG, "Ranging failed with code: $code")
                            continuation.resume(emptyList())
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Error performing ranging", e)
                continuation.resume(emptyList())
            }
        }
    
    /**
     * Process ranging results and convert to our data model
     */
    private fun processRangingResults(results: List<RangingResult>): List<RangingMeasurement> {
        val measurements = mutableListOf<RangingMeasurement>()
        val currentTime = System.currentTimeMillis()
        
        for (result in results) {
            if (result.status == RangingResult.STATUS_SUCCESS) {
                val distance = result.distanceMm / 1000.0f // Convert mm to meters
                val accuracy = result.distanceStdDevMm / 1000.0f // Convert mm to meters
                
                // Filter out inaccurate measurements
                if (accuracy <= MIN_ACCURACY_METERS) {
                    val measurement = RangingMeasurement(
                        sourceId = result.macAddress.toString(),
                        distance = distance,
                        accuracy = accuracy,
                        timestamp = currentTime,
                        measurementType = RangingType.WIFI_RTT
                    )
                    measurements.add(measurement)
                    
                    Log.d(TAG, "RTT measurement: ${result.macAddress} -> ${distance}m ±${accuracy}m")
                }
            } else {
                Log.w(TAG, "RTT measurement failed for ${result.macAddress}: status ${result.status}")
            }
        }
        
        return measurements
    }
    
    /**
     * Start continuous ranging with specified interval
     */
    fun startContinuousRanging(
        intervalMs: Long = 1000,
        onMeasurement: (List<RangingMeasurement>) -> Unit
    ) {
        // Implementation for continuous ranging would go here
        // This would involve periodic scanning and ranging operations
        Log.d(TAG, "Continuous ranging started with interval ${intervalMs}ms")
    }
    
    /**
     * Stop continuous ranging
     */
    fun stopContinuousRanging() {
        Log.d(TAG, "Continuous ranging stopped")
    }
}