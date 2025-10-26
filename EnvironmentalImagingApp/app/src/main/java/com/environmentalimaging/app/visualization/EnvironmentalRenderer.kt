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
        
        Log.d(TAG, "OpenGL ES initialized successfully")
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
        // Load and compile shaders
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        
        // Create shader program
        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)
        
        // Get shader handles
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
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
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
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
        // Load and compile shaders (same as point cloud)
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        
        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)
        
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
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
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
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
        // Initialize shaders (same pattern as others)
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_CODE)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_CODE)
        
        shaderProgram = GLES20.glCreateProgram()
        GLES20.glAttachShader(shaderProgram, vertexShader)
        GLES20.glAttachShader(shaderProgram, fragmentShader)
        GLES20.glLinkProgram(shaderProgram)
        
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "vPosition")
        colorHandle = GLES20.glGetAttribLocation(shaderProgram, "vColor")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix")
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
        return shader
    }
}