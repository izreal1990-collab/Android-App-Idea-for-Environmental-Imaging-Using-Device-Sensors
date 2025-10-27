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


package com.environmentalimaging.app.visualization

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.environmentalimaging.app.data.DevicePose
import com.environmentalimaging.app.data.Point3D
import kotlin.math.abs

/**
 * Custom GLSurfaceView for environmental 3D visualization
 * Handles touch interactions for camera control and displays SLAM data
 */
class EnvironmentalVisualizationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    
    private val renderer: EnvironmentalRenderer
    private val scaleGestureDetector: ScaleGestureDetector
    
    // Touch handling
    private var previousX = 0f
    private var previousY = 0f
    private var touchMode = TouchMode.NONE
    
    // Gesture detection
    private var initialSpan = 0f
    private var pointerCount = 0
    
    companion object {
        private const val TAG = "EnvironmentalVisualizationView"
        private const val TOUCH_SCALE_FACTOR = 180.0f / 320f
    }
    
    enum class TouchMode {
        NONE, ROTATE, PAN, ZOOM
    }
    
    init {
        Log.d(TAG, "Initializing 3D visualization view")
        
        // Set OpenGL ES 2.0 context
        setEGLContextClientVersion(2)
        
        // Initialize renderer
        renderer = EnvironmentalRenderer()
        setRenderer(renderer)
        
        // Set render mode to continuous (for real-time updates)
        renderMode = RENDERMODE_CONTINUOUSLY
        
        // Initialize gesture detector for pinch-to-zoom
        scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())
        
        Log.d(TAG, "3D visualization view initialized")
    }
    
    /**
     * Update point cloud data for visualization
     */
    fun updatePointCloud(points: List<Point3D>) {
        queueEvent {
            renderer.updatePointCloud(points)
        }
    }
    
    /**
     * Update device trajectory
     */
    fun updateTrajectory(trajectory: List<DevicePose>) {
        queueEvent {
            renderer.updateTrajectory(trajectory)
        }
    }
    
    /**
     * Update current device pose
     */
    fun updateDevicePose(pose: DevicePose) {
        queueEvent {
            renderer.updateDevicePose(pose)
        }
    }
    
    /**
     * Reset camera to default position
     */
    fun resetCamera() {
        queueEvent {
            renderer.resetCamera()
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle scale gestures first
        scaleGestureDetector.onTouchEvent(event)
        
        val x = event.x
        val y = event.y
        pointerCount = event.pointerCount
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                previousX = x
                previousY = y
                touchMode = TouchMode.ROTATE
                Log.d(TAG, "Touch down - rotate mode")
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    touchMode = TouchMode.PAN
                    // Calculate center point of two fingers
                    previousX = (event.getX(0) + event.getX(1)) / 2f
                    previousY = (event.getY(0) + event.getY(1)) / 2f
                    Log.d(TAG, "Two finger touch - pan mode")
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!scaleGestureDetector.isInProgress) {
                    when (touchMode) {
                        TouchMode.ROTATE -> {
                            val deltaX = x - previousX
                            val deltaY = y - previousY
                            
                            if (abs(deltaX) > 1f || abs(deltaY) > 1f) {
                                queueEvent {
                                    renderer.rotateCamera(deltaX * TOUCH_SCALE_FACTOR, deltaY * TOUCH_SCALE_FACTOR)
                                }
                            }
                        }
                        
                        TouchMode.PAN -> {
                            if (event.pointerCount == 2) {
                                val centerX = (event.getX(0) + event.getX(1)) / 2f
                                val centerY = (event.getY(0) + event.getY(1)) / 2f
                                val deltaX = centerX - previousX
                                val deltaY = centerY - previousY
                                
                                queueEvent {
                                    renderer.panCamera(deltaX, deltaY)
                                }
                                
                                previousX = centerX
                                previousY = centerY
                            }
                        }
                        
                        else -> { /* No action */ }
                    }
                    
                    if (touchMode == TouchMode.ROTATE) {
                        previousX = x
                        previousY = y
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                touchMode = if (event.pointerCount > 1) TouchMode.PAN else TouchMode.NONE
                Log.d(TAG, "Touch up - mode: $touchMode")
            }
        }
        
        return true
    }
    
    /**
     * Scale gesture listener for pinch-to-zoom
     */
    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            touchMode = TouchMode.ZOOM
            Log.d(TAG, "Scale gesture begin - zoom mode")
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            
            queueEvent {
                renderer.zoomCamera(1f / scaleFactor) // Invert for intuitive zoom
            }
            
            Log.d(TAG, "Scale gesture: factor = $scaleFactor")
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            touchMode = TouchMode.NONE
            Log.d(TAG, "Scale gesture end")
        }
    }
    
    /**
     * Pause rendering when view is not visible
     */
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Visualization paused")
    }
    
    /**
     * Resume rendering when view becomes visible
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "Visualization resumed")
    }
}