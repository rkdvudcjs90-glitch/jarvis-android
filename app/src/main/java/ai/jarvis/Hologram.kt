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
    private var hudProgram = 0
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private val quad = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f)); position(0)
    }
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
                gl_FragColor = vec4(tint, (1.0 - d * 2.0) * (0.08 + depth * 0.12));
            }
        """.trimIndent())
        program = glCreateProgram(); glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program)
        val ok = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, ok, 0); check(ok[0] != 0)
        glDeleteShader(vertex); glDeleteShader(fragment)
        val hudVertex=shader(GL_VERTEX_SHADER,"attribute vec2 xy; void main(){gl_Position=vec4(xy,0.0,1.0);}")
        val hudFragment=shader(GL_FRAGMENT_SHADER,"""
            precision highp float;
            uniform vec2 resolution; uniform float time; uniform float energy; uniform vec3 tint;
            const float PI=3.14159265;
            float line(float d,float width){return exp(-abs(d)/width);}
            float ring(float r,float radius,float width){return line(r-radius,width);}
            float arc(float a,float speed,float width){return 1.0-smoothstep(width-0.08,width,abs(sin(a+time*speed)));}
            void main(){
                vec2 p=(gl_FragCoord.xy-resolution*0.5)/min(resolution.x,resolution.y)*2.0;
                float r=length(p); float a=atan(p.y,p.x);
                float pulse=1.0+0.07*sin(time*2.2)+0.12*energy;
                vec3 c=vec3(0.0);
                // Deep central light and sharp concentric machining rings.
                float core=0.24*exp(-r*8.0)+0.8*exp(-r*r/0.0028)*pulse;
                float rings=ring(r,0.105,0.004)+0.55*ring(r,0.14,0.003);
                rings+=0.65*ring(r,0.27,0.004)+ring(r,0.32,0.006)+0.5*ring(r,0.36,0.003);
                rings+=0.6*ring(r,0.49,0.005)+0.35*ring(r,0.54,0.002);
                float ticks=pow(max(0.0,cos(a*72.0+time*0.15)),22.0);
                rings+=ticks*(step(0.37,r)-step(0.415,r))*0.85;
                rings+=pow(max(0.0,cos(a*36.0-time*0.12)),18.0)*(step(0.55,r)-step(0.595,r))*0.5;
                rings+=ring(r,0.445,0.008)*arc(a,0.35,0.62)*1.4;
                rings+=ring(r,0.575,0.004)*arc(a,-0.22,0.75)*0.8;
                rings+=ring(r,0.22,0.005)*arc(a,-0.6,0.48);
                // Soft bloom is calculated separately from the narrow luminous filaments.
                float glow=0.22*ring(r,0.32,0.027)+0.14*ring(r,0.445,0.025);
                float waves=0.0; float bloom=0.0;
                for(int i=0;i<9;i++){
                    float f=float(i);
                    float radius=0.76+0.025*sin(a*6.0+time*0.8+f*0.18)+0.025*cos(a*3.0-time*0.55)+f*0.009;
                    radius+=0.028*sin(a*4.0-time*1.4+f*0.12)*(0.5+energy);
                    waves+=ring(r,radius,0.0018)*(0.15+f*0.065);
                    bloom+=ring(r,radius,0.012)*0.022;
                }
                c+=tint*(rings*0.73+glow+waves*0.82+bloom+core);
                c+=vec3(0.5,0.94,1.0)*exp(-r*r/0.0012)*pulse;
                c*=1.0-smoothstep(0.91,1.05,r);
                gl_FragColor=vec4(c,1.0);
            }
        """.trimIndent())
        hudProgram=glCreateProgram(); glAttachShader(hudProgram,hudVertex); glAttachShader(hudProgram,hudFragment); glLinkProgram(hudProgram)
        glGetProgramiv(hudProgram,GL_LINK_STATUS,ok,0); check(ok[0]!=0) { glGetProgramInfoLog(hudProgram) }
        glDeleteShader(hudVertex); glDeleteShader(hudFragment)
        glClearColor(0.005f, 0.012f, 0.025f, 1f)
        glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth=width; surfaceHeight=height; glViewport(0, 0, width, height)
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
        val color = when(mode) { 1 -> floatArrayOf(0.2f,1f,0.65f); 2 -> floatArrayOf(0.65f,0.3f,1f); 3 -> floatArrayOf(0.2f,0.65f,1f); 4 -> floatArrayOf(1f,0.2f,0.15f); else -> floatArrayOf(0f,0.92f,1f) }
        glUniform3fv(glGetUniformLocation(program,"tint"),1,color,0)
        val point = glGetAttribLocation(program,"point")
        glEnableVertexAttribArray(point); glVertexAttribPointer(point,3,GL_FLOAT,false,0,points)
        glDrawArrays(GL_POINTS,0,count); glDisableVertexAttribArray(point)
        glUseProgram(hudProgram)
        glUniform2f(glGetUniformLocation(hudProgram,"resolution"),surfaceWidth.toFloat(),surfaceHeight.toFloat())
        glUniform1f(glGetUniformLocation(hudProgram,"time"),time)
        glUniform1f(glGetUniformLocation(hudProgram,"energy"),energy)
        glUniform3fv(glGetUniformLocation(hudProgram,"tint"),1,color,0)
        val xy=glGetAttribLocation(hudProgram,"xy")
        glEnableVertexAttribArray(xy); glVertexAttribPointer(xy,2,GL_FLOAT,false,0,quad)
        glDrawArrays(GL_TRIANGLE_STRIP,0,4); glDisableVertexAttribArray(xy)
    }
}
