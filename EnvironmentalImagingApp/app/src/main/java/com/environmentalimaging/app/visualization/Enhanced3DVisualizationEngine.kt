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
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.environmentalimaging.app.data.Point3D
import com.environmentalimaging.app.data.ScanSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * Enhanced 3D visualization engine with advanced rendering features
 */
class Enhanced3DVisualizationEngine(private val context: Context) : GLSurfaceView.Renderer {
    
    // View matrices
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    
    // Camera parameters
    private var cameraDistance = 5.0f
    private var cameraRotationX = 0f
    private var cameraRotationY = 0f
    private var cameraTargetX = 0f
    private var cameraTargetY = 0f
    private var cameraTargetZ = 0f
    
    // Point cloud data
    private var pointBuffer: FloatBuffer? = null
    private var colorBuffer: FloatBuffer? = null
    private var pointCount = 0
    
    // Rendering options
    data class RenderOptions(
        val pointSize: Float = 5.0f,
        val colorMode: ColorMode = ColorMode.HEIGHT,
        val showGrid: Boolean = true,
        val showAxes: Boolean = true,
        val enableLighting: Boolean = true,
        val backgroundColor: FloatArray = floatArrayOf(0.1f, 0.1f, 0.1f, 1.0f)
    )
    
    enum class ColorMode {
        HEIGHT,      // Color by Z coordinate
        INTENSITY,   // Color by intensity
        CUSTOM,      // Custom coloring
        GRADIENT     // Rainbow gradient
    }
    
    private var renderOptions = RenderOptions()
    
    // Shader programs
    private var pointShaderProgram = 0
    private var gridShaderProgram = 0
    
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(
            renderOptions.backgroundColor[0],
            renderOptions.backgroundColor[1],
            renderOptions.backgroundColor[2],
            renderOptions.backgroundColor[3]
        )
        
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        
        // Initialize shaders
        initShaders()
    }
    
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        
        val ratio = width.toFloat() / height
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 1f, 100f)
    }
    
    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        
        // Update view matrix based on camera parameters
        updateViewMatrix()
        
        // Draw coordinate axes
        if (renderOptions.showAxes) {
            drawAxes()
        }
        
        // Draw grid
        if (renderOptions.showGrid) {
            drawGrid()
        }
        
        // Draw point cloud
        drawPointCloud()
    }
    
    /**
     * Load scan session data for rendering
     */
    fun loadScanSession(session: ScanSession) {
        val points = session.dataPoints
        pointCount = points.size
        
        if (pointCount == 0) return
        
        // Create point buffer
        val pointData = FloatArray(pointCount * 3)
        points.forEachIndexed { index, point ->
            pointData[index * 3] = point.x
            pointData[index * 3 + 1] = point.y
            pointData[index * 3 + 2] = point.z
        }
        
        pointBuffer = ByteBuffer.allocateDirect(pointData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(pointData)
        pointBuffer?.position(0)
        
        // Generate colors based on height
        val colorData = generateColors(points)
        colorBuffer = ByteBuffer.allocateDirect(colorData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(colorData)
        colorBuffer?.position(0)
        
        // Center camera on point cloud
        centerCameraOnPoints(points)
    }
    
    /**
     * Generate colors for points based on color mode
     */
    private fun generateColors(points: List<Point3D>): FloatArray {
        val colors = FloatArray(points.size * 4)
        
        when (renderOptions.colorMode) {
            ColorMode.HEIGHT -> {
                val minZ = points.minOfOrNull { it.z } ?: 0f
                val maxZ = points.maxOfOrNull { it.z } ?: 1f
                val range = maxZ - minZ
                
                points.forEachIndexed { index, point ->
                    val normalized = if (range > 0) (point.z - minZ) / range else 0.5f
                    val color = heightToColor(normalized)
                    colors[index * 4] = color[0]
                    colors[index * 4 + 1] = color[1]
                    colors[index * 4 + 2] = color[2]
                    colors[index * 4 + 3] = 1.0f
                }
            }
            ColorMode.GRADIENT -> {
                points.forEachIndexed { index, _ ->
                    val t = index.toFloat() / points.size
                    val color = rainbowColor(t)
                    colors[index * 4] = color[0]
                    colors[index * 4 + 1] = color[1]
                    colors[index * 4 + 2] = color[2]
                    colors[index * 4 + 3] = 1.0f
                }
            }
            else -> {
                // Default white
                for (i in points.indices) {
                    colors[i * 4] = 1.0f
                    colors[i * 4 + 1] = 1.0f
                    colors[i * 4 + 2] = 1.0f
                    colors[i * 4 + 3] = 1.0f
                }
            }
        }
        
        return colors
    }
    
    /**
     * Convert height value to color (blue -> green -> red)
     */
    private fun heightToColor(value: Float): FloatArray {
        val r = max(0f, min(1f, 2 * value - 1))
        val g = max(0f, min(1f, 2 * (1 - abs(2 * value - 1))))
        val b = max(0f, min(1f, 1 - 2 * value))
        return floatArrayOf(r, g, b)
    }
    
    /**
     * Generate rainbow color from 0-1 value
     */
    private fun rainbowColor(t: Float): FloatArray {
        val r = sin(PI * t).toFloat()
        val g = sin(PI * (t + 0.33f)).toFloat()
        val b = sin(PI * (t + 0.67f)).toFloat()
        return floatArrayOf(abs(r), abs(g), abs(b))
    }
    
    /**
     * Center camera on point cloud
     */
    private fun centerCameraOnPoints(points: List<Point3D>) {
        if (points.isEmpty()) return
        
        val centerX = points.map { it.x }.average().toFloat()
        val centerY = points.map { it.y }.average().toFloat()
        val centerZ = points.map { it.z }.average().toFloat()
        
        cameraTargetX = centerX
        cameraTargetY = centerY
        cameraTargetZ = centerZ
        
        // Calculate appropriate camera distance
        val maxDist = points.maxOfOrNull { 
            sqrt((it.x - centerX).pow(2) + (it.y - centerY).pow(2) + (it.z - centerZ).pow(2))
        } ?: 1f
        
        cameraDistance = maxDist * 2.5f
    }
    
    /**
     * Update view matrix based on camera parameters
     */
    private fun updateViewMatrix() {
        val eyeX = cameraTargetX + cameraDistance * cos(cameraRotationY) * cos(cameraRotationX)
        val eyeY = cameraTargetY + cameraDistance * sin(cameraRotationX)
        val eyeZ = cameraTargetZ + cameraDistance * sin(cameraRotationY) * cos(cameraRotationX)
        
        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            cameraTargetX, cameraTargetY, cameraTargetZ,
            0f, 1f, 0f
        )
    }
    
    /**
     * Draw point cloud
     */
    private fun drawPointCloud() {
        if (pointBuffer == null || colorBuffer == null || pointCount == 0) return
        
        GLES30.glUseProgram(pointShaderProgram)
        
        // Note: Point size is set in vertex shader with gl_PointSize
        // In GLES 3.0, glPointSize is not available - use shader instead
        
        // Calculate MVP matrix
        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvpMatrix, 0)
        
        // Note: In a full implementation, bind vertex buffers and draw
        // This is a simplified version
    }
    
    /**
     * Draw coordinate axes
     */
    private fun drawAxes() {
        // Draw X (red), Y (green), Z (blue) axes
        // Simplified - in full implementation, use line drawing
    }
    
    /**
     * Draw grid
     */
    private fun drawGrid() {
        // Draw grid on XZ plane
        // Simplified - in full implementation, use line drawing
    }
    
    /**
     * Initialize shader programs
     */
    private fun initShaders() {
        val vertexShaderCode = """
            #version 300 es
            uniform mat4 uMVPMatrix;
            in vec4 vPosition;
            in vec4 vColor;
            out vec4 fColor;
            void main() {
                gl_Position = uMVPMatrix * vPosition;
                gl_PointSize = 5.0;
                fColor = vColor;
            }
        """.trimIndent()
        
        val fragmentShaderCode = """
            #version 300 es
            precision mediump float;
            in vec4 fColor;
            out vec4 fragColor;
            void main() {
                fragColor = fColor;
            }
        """.trimIndent()
        
        pointShaderProgram = createShaderProgram(vertexShaderCode, fragmentShaderCode)
        gridShaderProgram = createShaderProgram(vertexShaderCode, fragmentShaderCode)
    }
    
    /**
     * Create shader program from source
     */
    private fun createShaderProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        
        return GLES30.glCreateProgram().also { program ->
            GLES30.glAttachShader(program, vertexShader)
            GLES30.glAttachShader(program, fragmentShader)
            GLES30.glLinkProgram(program)
        }
    }
    
    /**
     * Load and compile shader
     */
    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES30.glCreateShader(type).also { shader ->
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
        }
    }
    
    /**
     * Update camera rotation
     */
    fun rotateCamera(deltaX: Float, deltaY: Float) {
        cameraRotationY += deltaX * 0.01f
        cameraRotationX += deltaY * 0.01f
        cameraRotationX = cameraRotationX.coerceIn(-PI.toFloat() / 2, PI.toFloat() / 2)
    }
    
    /**
     * Zoom camera
     */
    fun zoomCamera(delta: Float) {
        cameraDistance *= (1 - delta * 0.01f)
        cameraDistance = cameraDistance.coerceIn(0.1f, 100f)
    }
    
    /**
     * Pan camera
     */
    fun panCamera(deltaX: Float, deltaY: Float) {
        val scale = cameraDistance * 0.001f
        cameraTargetX += deltaX * scale
        cameraTargetY -= deltaY * scale
    }
    
    /**
     * Update render options
     */
    fun updateRenderOptions(options: RenderOptions) {
        this.renderOptions = options
        GLES30.glClearColor(
            options.backgroundColor[0],
            options.backgroundColor[1],
            options.backgroundColor[2],
            options.backgroundColor[3]
        )
    }
    
    /**
     * Reset camera to default position
     */
    fun resetCamera() {
        cameraDistance = 5.0f
        cameraRotationX = 0f
        cameraRotationY = 0f
        cameraTargetX = 0f
        cameraTargetY = 0f
        cameraTargetZ = 0f
    }
}
