package ai.jarvis

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class Hologram(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile var mode = 0 // idle, listening, thinking, speaking, error
    @Volatile var level = 0f
    private var program = 0
    private val matrix = FloatArray(16)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val rotation = FloatArray(16)
    private val temp = FloatArray(16)
    private val count = 2600
    private val points = ByteBuffer.allocateDirect(count * 3 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        for (i in 0 until count) {
            val y = 1f - 2f * i / (count - 1)
            val r = sqrt(1f - y*y)
            val angle = i * 2.3999632f
            put(cos(angle)*r); put(y); put(sin(angle)*r)
        }
        position(0)
    }
    init { setEGLContextClientVersion(2); setRenderer(this); preserveEGLContextOnPause = true }
    private fun shader(type: Int, source: String): Int {
        val shader = glCreateShader(type)
        glShaderSource(shader, source); glCompileShader(shader)
        val ok = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { glGetShaderInfoLog(shader) }
        return shader
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertex = shader(GL_VERTEX_SHADER, """
            attribute vec3 point; uniform mat4 transform; uniform float time; uniform float energy;
            varying float depth;
            void main() {
                vec3 p = point * (1.0 + sin(point.y * 12.0 + time * 3.0) * (0.015 + energy * 0.06));
                gl_Position = transform * vec4(p, 1.0);
                depth = (point.z + 1.0) * 0.5;
                gl_PointSize = (2.0 + energy * 2.0) * (1.0 + depth);
            }
        """.trimIndent())
        val fragment = shader(GL_FRAGMENT_SHADER, """
            precision mediump float; uniform vec3 tint; varying float depth;
            void main() {
                float d = length(gl_PointCoord - vec2(0.5));
                if (d > 0.5) discard;
                gl_FragColor = vec4(tint, (1.0 - d * 2.0) * (0.35 + depth * 0.65));
            }
        """.trimIndent())
        program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program)
        val ok = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, ok, 0); check(ok[0] != 0)
        glDeleteShader(vertex); glDeleteShader(fragment)
        glClearColor(0.012f, 0.025f, 0.055f, 1f)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 44f, width.toFloat()/height.coerceAtLeast(1), 0.1f, 20f)
        Matrix.setLookAtM(view, 0, 0f, 0f, 3.5f, 0f, 0f, 0f, 0f, 1f, 0f)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT); glUseProgram(program)
        val time = (SystemClock.uptimeMillis() % 360000L) / 1000f
        Matrix.setRotateM(rotation, 0, time * if(mode == 2) 30f else 9f, 0.25f, 1f, 0.1f)
        Matrix.multiplyMM(temp, 0, view, 0, rotation, 0); Matrix.multiplyMM(matrix, 0, projection, 0, temp, 0)
        glUniformMatrix4fv(glGetUniformLocation(program, "transform"), 1, false, matrix, 0)
        glUniform1f(glGetUniformLocation(program, "time"), time)
        val energy = when(mode) { 1 -> 0.3f + level; 2 -> 0.7f; 3 -> 0.6f + 0.3f*sin(time*8f); else -> 0f }
        glUniform1f(glGetUniformLocation(program, "energy"), energy)
        val color = when(mode) { 1 -> floatArrayOf(0.2f,1f,0.65f); 2 -> floatArrayOf(0.65f,0.3f,1f); 3 -> floatArrayOf(0.2f,0.65f,1f); 4 -> floatArrayOf(1f,0.2f,0.15f); else -> floatArrayOf(0f,0.85f,1f) }
        glUniform3fv(glGetUniformLocation(program,"tint"),1,color,0)
        val point = glGetAttribLocation(program,"point")
        glEnableVertexAttribArray(point); glVertexAttribPointer(point,3,GL_FLOAT,false,0,points)
        glDrawArrays(GL_POINTS,0,count); glDisableVertexAttribArray(point)
    }
}
