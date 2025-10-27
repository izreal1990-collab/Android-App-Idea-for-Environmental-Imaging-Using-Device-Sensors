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


package com.environmentalimaging.app.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.environmentalimaging.app.R

/**
 * Foreground service for background environmental scanning
 * Handles continuous sensor data acquisition when app is not in foreground
 */
class EnvironmentalScanService : Service() {
    
    companion object {
        private const val TAG = "EnvironmentalScanService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "environmental_scan_channel"
        private const val CHANNEL_NAME = "Environmental Scanning"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Environmental scan service created")
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Environmental scan service started")
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // TODO: Start background scanning operations
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Environmental scan service destroyed")
        
        // TODO: Stop scanning operations
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for environmental scanning operations"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Environmental Scanning")
            .setContentText("Collecting environmental data in background")
            .setSmallIcon(R.drawable.ic_compass)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}