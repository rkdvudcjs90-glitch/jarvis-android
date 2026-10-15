package ai.jarvis

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Original real-time particle mesh inspired by the selected reference; no GIF playback. */
class Hologram(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    @Volatile var mode = 0
    @Volatile var level = 0f
    @Volatile var renderedFrames = 0; private set
    @Volatile var drawError = GL_NO_ERROR; private set
    private var program = 0
    private val transform = FloatArray(16)
    private val projection = FloatArray(16)
    private val camera = FloatArray(16)
    private val rotation = FloatArray(16)
    private val started = SystemClock.elapsedRealtime()
    private val count = 1200
    private val shellCount = 1100
    private val idle = floatArrayOf(0.62f,0.89f,1f)
    private val listening = floatArrayOf(0.75f,0.96f,1f)
    private val thinking = floatArrayOf(1f,0.66f,0.26f)
    private val speaking = floatArrayOf(0.3f,1f,0.76f)
    private val error = floatArrayOf(1f,0.28f,0.23f)
    private var pointLocation=0
    private var transformLocation=0
    private var rotationLocation=0
    private var timeLocation=0
    private var energyLocation=0
    private var tintLocation=0
    private var kindLocation=0
    private var sizeLocation=0
    private var gainLocation=0
    // Geometry is created once on the GL thread, never during Activity creation or each frame.
    private val coordinates by lazy {
        val random=Random(731L)
        FloatArray(count*3).apply {
            for(i in 0 until count) {
                val y=random.nextFloat()*2f-1f
                val angle=random.nextFloat()*(2*PI).toFloat()
                val radius=if(i<shellCount) 0.97f+random.nextFloat()*0.06f else 1.08f+random.nextFloat()*0.2f
                val horizontal=sqrt(1f-y*y)*radius
                this[i*3]=cos(angle)*horizontal; this[i*3+1]=y*radius; this[i*3+2]=sin(angle)*horizontal
            }
        }
    }
    private val points by lazy { ByteBuffer.allocateDirect(count*3*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(coordinates); position(0) } }
    private val indices by lazy {
        val edges=LinkedHashSet<Int>()
        for(i in 0 until shellCount) {
            val nearest=IntArray(4) { -1 }
            val distances=FloatArray(4) { Float.MAX_VALUE }
            for(j in 0 until shellCount) {
                if(i==j) continue
                val dx=coordinates[i*3]-coordinates[j*3]
                val dy=coordinates[i*3+1]-coordinates[j*3+1]
                val dz=coordinates[i*3+2]-coordinates[j*3+2]
                val distance=dx*dx+dy*dy+dz*dz
                for(k in 0..3) if(distance<distances[k]) {
                    for(n in 3 downTo k+1) { distances[n]=distances[n-1]; nearest[n]=nearest[n-1] }
                    distances[k]=distance; nearest[k]=j; break
                }
            }
            for(k in 0 until if(i%3==0) 4 else 3) {
                val j=nearest[k]
                if(j>=0 && distances[k]<0.055f) edges.add(minOf(i,j)*count+maxOf(i,j))
            }
        }
        ByteBuffer.allocateDirect(edges.size*2*2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            edges.forEach { put((it/count).toShort()); put((it%count).toShort()) }; position(0)
        }
    }
    init { setEGLContextClientVersion(2); setRenderer(this); preserveEGLContextOnPause=true }
    private fun shader(type: Int, source: String): Int {
        val id=glCreateShader(type); glShaderSource(id,source); glCompileShader(id)
        val ok=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { glGetShaderInfoLog(id) }; return id
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vertex=shader(GL_VERTEX_SHADER,"""
            attribute vec3 point;
            uniform mat4 transform; uniform mat4 rotation;
            uniform float time; uniform float energy; uniform float size;
            varying float light; varying float dust;
            void main(){
                float seed=dot(point,vec3(11.7,7.3,5.9));
                float wave=0.026*sin(seed+time*0.9)+0.017*cos(point.y*9.0-time*0.65);
                vec3 p=point*(1.0+wave*(1.0+energy));
                p+=vec3(sin(seed+time),cos(seed-time*0.8),sin(seed*0.6+time*0.7))*0.012;
                vec3 rotated=(rotation*vec4(p,1.0)).xyz;
                gl_Position=transform*vec4(rotated,1.0);
                float rim=pow(1.0-abs(rotated.z)/max(length(rotated),0.01),1.4);
                float nearSide=smoothstep(-0.9,0.9,rotated.z);
                light=(0.05+0.2*nearSide+0.75*rim)*(0.8+0.2*sin(seed*5.0+time*1.7));
                dust=step(1.06,length(point));
                light*=mix(1.0,0.24,dust);
                gl_PointSize=size*(0.65+0.6*nearSide)*(1.0+0.14*energy);
            }
        """.trimIndent())
        val fragment=shader(GL_FRAGMENT_SHADER,"""
            precision mediump float;
            uniform vec3 tint; uniform float kind; uniform float gain;
            varying float light; varying float dust;
            void main(){
                float alpha=light*gain;
                if(kind>0.5){
                    float radius=length(gl_PointCoord-vec2(0.5))*2.0;
                    if(radius>1.0) discard;
                    alpha*=pow(1.0-radius,1.25);
                }
                gl_FragColor=vec4(tint,alpha);
            }
        """.trimIndent())
        program=glCreateProgram(); glAttachShader(program,vertex); glAttachShader(program,fragment); glLinkProgram(program)
        val ok=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,ok,0); check(ok[0]!=0) { glGetProgramInfoLog(program) }
        glDeleteShader(vertex); glDeleteShader(fragment)
        pointLocation=glGetAttribLocation(program,"point")
        transformLocation=glGetUniformLocation(program,"transform"); rotationLocation=glGetUniformLocation(program,"rotation")
        timeLocation=glGetUniformLocation(program,"time"); energyLocation=glGetUniformLocation(program,"energy")
        tintLocation=glGetUniformLocation(program,"tint"); kindLocation=glGetUniformLocation(program,"kind")
        sizeLocation=glGetUniformLocation(program,"size"); gainLocation=glGetUniformLocation(program,"gain")
        // Force the one-time mesh work here, before the first frame.
        points.position(0); indices.position(0)
        glClearColor(0f,0f,0f,1f); glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA,GL_ONE)
        glDisable(GL_DEPTH_TEST)
    }
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0,0,width,height)
        val aspect=width.toFloat()/height.coerceAtLeast(1)
        val halfWidth=0.94f*maxOf(1f,aspect); val halfHeight=halfWidth/aspect
        Matrix.frustumM(projection,0,-halfWidth,halfWidth,-halfHeight,halfHeight,3f,12f)
        Matrix.setLookAtM(camera,0,0f,0f,4.4f,0f,0f,0f,0f,1f,0f)
        Matrix.multiplyMM(transform,0,projection,0,camera,0)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT); glUseProgram(program)
        val time=(SystemClock.elapsedRealtime()-started)/1000f
        val state=mode
        val energy=when(state) { 1 -> 0.25f+level; 2 -> 0.5f; 3 -> 0.5f+0.2f*sin(time*7f); else -> 0f }
        Matrix.setRotateM(rotation,0,time*(if(state==2) 13f else 5f),0.2f,1f,0.13f)
        glUniformMatrix4fv(transformLocation,1,false,transform,0); glUniformMatrix4fv(rotationLocation,1,false,rotation,0)
        glUniform1f(timeLocation,time); glUniform1f(energyLocation,energy)
        glUniform3fv(tintLocation,1,when(state) { 1 -> listening; 2 -> thinking; 3 -> speaking; 4 -> error; else -> idle },0)
        glEnableVertexAttribArray(pointLocation); glVertexAttribPointer(pointLocation,3,GL_FLOAT,false,0,points)
        glUniform1f(kindLocation,0f); glUniform1f(gainLocation,0.55f); glUniform1f(sizeLocation,1f)
        glLineWidth(1f); glDrawElements(GL_LINES,indices.limit(),GL_UNSIGNED_SHORT,indices)
        // A broad low-opacity pass produces a local halo around each node; the next pass stays sharp.
        glUniform1f(kindLocation,1f); glUniform1f(gainLocation,0.12f); glUniform1f(sizeLocation,11f)
        glDrawArrays(GL_POINTS,0,count)
        glUniform1f(gainLocation,1.7f); glUniform1f(sizeLocation,4.4f)
        glDrawArrays(GL_POINTS,0,count); glDisableVertexAttribArray(pointLocation)
        if(renderedFrames<3 || renderedFrames%30==0) {
            val observed=glGetError(); if(observed!=GL_NO_ERROR) drawError=observed
        }
        renderedFrames++
    }
}
