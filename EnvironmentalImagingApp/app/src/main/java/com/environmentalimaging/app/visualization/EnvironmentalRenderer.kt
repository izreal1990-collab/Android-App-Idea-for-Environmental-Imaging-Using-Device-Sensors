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

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.environmentalimaging.app.data.DevicePose
import com.environmentalimaging.app.data.Point3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * OpenGL ES renderer for 3D environmental visualization
 * Renders point clouds, device trajectory, and environmental landmarks
 */
class EnvironmentalRenderer : GLSurfaceView.Renderer {
    
    // Rendering objects
    private lateinit var pointCloudRenderer: PointCloudRenderer
    private lateinit var trajectoryRenderer: TrajectoryRenderer
    private lateinit var deviceRenderer: DeviceRenderer
    
    // View matrices
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    // Camera parameters
    private var cameraDistance = 10.0f
    private var cameraAngleX = 0.0f
    private var cameraAngleY = 0.0f
    private var cameraTargetX = 0.0f
    private var cameraTargetY = 0.0f
    private var cameraTargetZ = 0.0f
    
    // Data to render
    private var pointCloud = listOf<Point3D>()
    private var deviceTrajectory = listOf<DevicePose>()
    private var currentDevicePose: DevicePose? = null
    
    companion object {
        private const val TAG = "EnvironmentalRenderer"
        
        // Shader source code
        internal const val VERTEX_SHADER_CODE =
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "attribute vec4 vColor;" +
            "varying vec4 fColor;" +
            "void main() {" +
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  gl_PointSize = 8.0;" +
            "  fColor = vColor;" +
            "}"
        
        internal const val FRAGMENT_SHADER_CODE =
            "precision mediump float;" +
            "varying vec4 fColor;" +
            "void main() {" +
            "  gl_FragColor = fColor;" +
            "}"
    }
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "Surface created")
        
        // Set clear color (dark gray background)
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        
        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        // Enable blending for transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        
        // Initialize renderers
        pointCloudRenderer = PointCloudRenderer()
        trajectoryRenderer = TrajectoryRenderer()
        deviceRenderer = DeviceRenderer()
        
        // Load test data for immediate visualization
        generateTestData()
        
        Log.d(TAG, "OpenGL ES initialized successfully with test data")
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "Surface changed: ${width}x${height}")
        
        GLES20.glViewport(0, 0, width, height)
        
        // Calculate aspect ratio
        val ratio = width.toFloat() / height.toFloat()
        
        // Set up projection matrix
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        // Clear screen
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        
        // Update camera position
        updateCamera()
        
        // Calculate MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        
        // Render point cloud
        if (pointCloud.isNotEmpty()) {
            pointCloudRenderer.render(pointCloud, mvpMatrix)
        }
        
        // Render device trajectory
        if (deviceTrajectory.isNotEmpty()) {
            trajectoryRenderer.render(deviceTrajectory, mvpMatrix)
        }
        
        // Render current device position
        currentDevicePose?.let { pose ->
            deviceRenderer.render(pose, mvpMatrix)
        }
        
        // Check for OpenGL errors
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, "OpenGL error: $error")
        }
    }
    
    /**
     * Update camera view matrix
     */
    private fun updateCamera() {
        // Calculate camera position based on spherical coordinates
        val eyeX = cameraTargetX + cameraDistance * kotlin.math.cos(cameraAngleY) * kotlin.math.sin(cameraAngleX)
        val eyeY = cameraTargetY + cameraDistance * kotlin.math.sin(cameraAngleY)
        val eyeZ = cameraTargetZ + cameraDistance * kotlin.math.cos(cameraAngleY) * kotlin.math.cos(cameraAngleX)
        
        // Set up view matrix
        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,        // Eye position
            cameraTargetX, cameraTargetY, cameraTargetZ,  // Look at point
            0f, 1f, 0f               // Up vector
        )
    }
    
    /**
     * Update point cloud data
     */
    fun updatePointCloud(points: List<Point3D>) {
        pointCloud = points
        Log.d(TAG, "Updated point cloud with ${points.size} points")
    }
    
    /**
     * Update device trajectory
     */
    fun updateTrajectory(trajectory: List<DevicePose>) {
        deviceTrajectory = trajectory
    }
    
    /**
     * Update current device pose
     */
    fun updateDevicePose(pose: DevicePose) {
        currentDevicePose = pose
        // Center camera on device if it's the first pose
        if (deviceTrajectory.isEmpty() && pointCloud.isEmpty()) {
            cameraTargetX = pose.position.x
            cameraTargetY = pose.position.y
            cameraTargetZ = pose.position.z
        }
    }
    
    /**
     * Handle camera rotation
     */
    fun rotateCamera(deltaX: Float, deltaY: Float) {
        cameraAngleX += deltaX * 0.01f
        cameraAngleY += deltaY * 0.01f
        
        // Clamp vertical rotation
        cameraAngleY = cameraAngleY.coerceIn(-kotlin.math.PI.toFloat() / 2, kotlin.math.PI.toFloat() / 2)
    }
    
    /**
     * Handle camera zoom
     */
    fun zoomCamera(scaleFactor: Float) {
        cameraDistance *= scaleFactor
        cameraDistance = cameraDistance.coerceIn(1.0f, 50.0f)
    }
    
    /**
     * Handle camera pan
     */
    fun panCamera(deltaX: Float, deltaY: Float) {
        cameraTargetX += deltaX * 0.1f
        cameraTargetY -= deltaY * 0.1f
    }
    
    /**
     * Reset camera to default position
     */
    fun resetCamera() {
        cameraDistance = 10.0f
        cameraAngleX = 0.0f
        cameraAngleY = 0.3f
        cameraTargetX = 0.0f
        cameraTargetY = 0.0f
        cameraTargetZ = 0.0f
    }
    

    
    /**
     * Generate test data for immediate visualization
     */
    private fun generateTestData() {
        try {
            Log.d(TAG, "Generating test data for visualization")
            
            // Create a simple room point cloud (8x8x3 meters)
            val testPoints = mutableListOf<Point3D>()
            
            // Floor points (grid pattern)
            for (x in -40..40 step 4) {
                for (z in -40..40 step 4) {
                    testPoints.add(Point3D(
                        x = x * 0.1f,
                        y = 0f,
                        z = z * 0.1f
                    ))
                }
            }
            
            // Wall points (back wall)
            for (x in -40..40 step 3) {
                for (y in 0..30 step 3) {
                    testPoints.add(Point3D(
                        x = x * 0.1f,
                        y = y * 0.1f,
                        z = 4f
                    ))
                }
            }
            
            // Side wall points (left wall)
            for (z in -40..40 step 3) {
                for (y in 0..30 step 3) {
                    testPoints.add(Point3D(
                        x = -4f,
                        y = y * 0.1f,
                        z = z * 0.1f
                    ))
                }
            }
            
            // Add some furniture-like objects
            // Table
            for (x in 5..15) {
                for (z in 5..15) {
                    testPoints.add(Point3D(
                        x = x * 0.1f,
                        y = 0.8f,
                        z = z * 0.1f
                    ))
                }
            }
            
            // Chair
            for (x in -15..-5) {
                for (z in 5..15) {
                    testPoints.add(Point3D(
                        x = x * 0.1f,
                        y = 0.5f,
                        z = z * 0.1f
                    ))
                }
            }
            
            updatePointCloud(testPoints)
            
            // Create a test trajectory (user walking around the room)
            val testTrajectory = mutableListOf<DevicePose>()
            for (i in 0..20) {
                val angle = i * 0.3f
                val x = 2f * kotlin.math.cos(angle.toDouble()).toFloat()
                val z = 2f * kotlin.math.sin(angle.toDouble()).toFloat()
                testTrajectory.add(DevicePose(
                    position = Point3D(x, 1.7f, z),
                    orientation = floatArrayOf(0f, angle, 0f, 0f), // Quaternion [w, x, y, z]
                    timestamp = System.currentTimeMillis() + i * 1000L
                ))
            }
            
            updateTrajectory(testTrajectory)
            
            // Set current device position
            if (testTrajectory.isNotEmpty()) {
                updateDevicePose(testTrajectory.last())
            }
            
            Log.d(TAG, "Test data generated: ${testPoints.size} points, ${testTrajectory.size} trajectory points")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating test data", e)
        }
    }
}

/**
 * Point cloud renderer
 */
class PointCloudRenderer {
    
    private var shaderProgram = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    
    private var vertexBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    
    companion object {
        private const val TAG = "PointCloudRenderer"
        private val VERTEX_SHADER_CODE = EnvironmentalRenderer.VERTEX_SHADER_CODE
        private val FRAGMENT_SHADER_CODE = EnvironmentalRenderer.FRAGMENT_SHADER_CODE
    }
    
    init {
        try {
            // Load and compile shaders
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
            
            if (vertexShader != 0 && fragmentShader != 0) {
                // Create shader program
                shaderProgram = GLES20.glCreateProgram()
                GLES20.glAttachShader(shaderProgram, vertexShader)
                GLES20.glAttachShader(shaderProgram, fragmentShader)
                GLES20.glLinkProgram(shaderProgram)
                
                // Check program linking
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != 0) {
                    // Get shader handles
                    positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
                    colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
                    mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
                    Log.d(TAG, "PointCloudRenderer initialized successfully")
                } else {
                    Log.e(TAG, "Program linking failed: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
                    GLES20.glDeleteProgram(shaderProgram)
                    shaderProgram = 0
                }
            } else {
                Log.e(TAG, "Failed to compile shaders")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing PointCloudRenderer", e)
        }
    }
    
    fun render(points: List<Point3D>, mvpMatrix: FloatArray) {
        if (points.isEmpty()) return
        
        // Prepare vertex data
        val vertices = FloatArray(points.size * 3)
        val colors = FloatArray(points.size * 4)
        
        for (i in points.indices) {
            val point = points[i]
            vertices[i * 3] = point.x
            vertices[i * 3 + 1] = point.y
            vertices[i * 3 + 2] = point.z
            
            // Color based on height (z-coordinate)
            val normalizedZ = (point.z + 5f) / 10f // Assuming height range -5 to 5
            colors[i * 4] = normalizedZ.coerceIn(0f, 1f) // Red
            colors[i * 4 + 1] = (1f - normalizedZ).coerceIn(0f, 1f) // Green
            colors[i * 4 + 2] = 0.5f // Blue
            colors[i * 4 + 3] = 0.8f // Alpha
        }
        
        // Create buffers
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
        
        // Use shader program
        GLES20.glUseProgram(shaderProgram)
        
        // Set vertex positions
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        
        // Set vertex colors
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        
        // Set MVP matrix
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        // Draw points
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, points.size)
        
        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $type")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Check compilation status
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
}

/**
 * Device trajectory renderer
 */
class TrajectoryRenderer {
    
    private var shaderProgram = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    
    companion object {
        private const val TAG = "TrajectoryRenderer"
        private val VERTEX_SHADER_CODE = EnvironmentalRenderer.VERTEX_SHADER_CODE
        private val FRAGMENT_SHADER_CODE = EnvironmentalRenderer.FRAGMENT_SHADER_CODE
    }
    
    init {
        try {
            // Load and compile shaders (same as point cloud)
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
            
            if (vertexShader != 0 && fragmentShader != 0) {
                shaderProgram = GLES20.glCreateProgram()
                GLES20.glAttachShader(shaderProgram, vertexShader)
                GLES20.glAttachShader(shaderProgram, fragmentShader)
                GLES20.glLinkProgram(shaderProgram)
                
                // Check program linking
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != 0) {
                    positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
                    colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
                    mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
                    Log.d(TAG, "TrajectoryRenderer initialized successfully")
                } else {
                    Log.e(TAG, "Program linking failed: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
                    GLES20.glDeleteProgram(shaderProgram)
                    shaderProgram = 0
                }
            } else {
                Log.e(TAG, "Failed to compile shaders")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing TrajectoryRenderer", e)
        }
    }
    
    fun render(trajectory: List<DevicePose>, mvpMatrix: FloatArray) {
        if (trajectory.size < 2) return
        
        // Prepare line vertices
        val vertices = FloatArray(trajectory.size * 3)
        val colors = FloatArray(trajectory.size * 4)
        
        for (i in trajectory.indices) {
            val pose = trajectory[i]
            vertices[i * 3] = pose.position.x
            vertices[i * 3 + 1] = pose.position.y
            vertices[i * 3 + 2] = pose.position.z
            
            // Color gradient from blue (start) to green (end)
            val t = i.toFloat() / (trajectory.size - 1)
            colors[i * 4] = 0f // Red
            colors[i * 4 + 1] = t // Green
            colors[i * 4 + 2] = 1f - t // Blue
            colors[i * 4 + 3] = 1f // Alpha
        }
        
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        val colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
        
        // Render trajectory
        GLES20.glUseProgram(shaderProgram)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        GLES20.glLineWidth(3.0f)
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, trajectory.size)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader of type $type")
            return 0
        }
        
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Check compilation status
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
}

/**
 * Current device position renderer
 */
class DeviceRenderer {
    
    private var shaderProgram = 0
    private var positionHandle = 0
    private var colorHandle = 0
    private var mvpMatrixHandle = 0
    
    companion object {
        private const val TAG = "DeviceRenderer"
        private val VERTEX_SHADER_CODE = EnvironmentalRenderer.VERTEX_SHADER_CODE
        private val FRAGMENT_SHADER_CODE = EnvironmentalRenderer.FRAGMENT_SHADER_CODE
    }
    
    init {
        try {
            // Initialize shaders (same pattern as others)
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
            
            if (vertexShader != 0 && fragmentShader != 0) {
                shaderProgram = GLES20.glCreateProgram()
                GLES20.glAttachShader(shaderProgram, vertexShader)
                GLES20.glAttachShader(shaderProgram, fragmentShader)
                GLES20.glLinkProgram(shaderProgram)
                
                // Check program linking
                val linkStatus = IntArray(1)
                GLES20.glGetProgramiv(shaderProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
                if (linkStatus[0] != 0) {
                    positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
                    colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
                    mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
                    Log.d(TAG, "DeviceRenderer initialized successfully")
                } else {
                    Log.e(TAG, "Program linking failed: ${GLES20.glGetProgramInfoLog(shaderProgram)}")
                    GLES20.glDeleteProgram(shaderProgram)
                    shaderProgram = 0
                }
            } else {
                Log.e(TAG, "Failed to compile shaders")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing DeviceRenderer", e)
        }
    }
    
    fun render(pose: DevicePose, mvpMatrix: FloatArray) {
        // Render device as a colored cube/point
        val vertices = floatArrayOf(
            pose.position.x, pose.position.y, pose.position.z
        )
        
        val colors = floatArrayOf(
            1f, 0f, 0f, 1f // Red for current device position
        )
        
        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }
        
        val colorBuffer = ByteBuffer.allocateDirect(colors.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(colors)
                position(0)
            }
        
        GLES20.glUseProgram(shaderProgram)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1)
        
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }
    
    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        
        // Check compilation
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compilation failed: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        
        return shader
    }
}