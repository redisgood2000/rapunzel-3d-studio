package com.rapunzel.studio

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class PrincessRenderer(private var appearance: CharacterAppearance) : GLSurfaceView.Renderer {
    @Volatile private var yaw = 0f
    @Volatile private var pitch = 0f
    @Volatile private var zoom = 5.6f
    @Volatile private var animating = true
    @Volatile private var expression = 0
    private var lastX = 0f
    private var lastY = 0f
    private var pinchDistance = 0f
    private var program = 0
    private var positionHandle = 0
    private var normalHandle = 0
    private var mvpHandle = 0
    private var modelHandle = 0
    private var modelViewHandle = 0
    private var colorHandle = 0
    private var lightHandle = 0
    private var sphere: FloatBuffer? = null
    private var sphereCount = 0
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private var startTime = System.currentTimeMillis()

    private val vertexShader = """
        attribute vec3 aPosition;
        attribute vec3 aNormal;
        uniform mat4 uMVP;
        uniform mat4 uModel;
        uniform mat4 uModelView;
        varying vec3 vNormal;
        varying vec3 vEyePosition;
        void main() {
            vec4 eyePosition = uModelView * vec4(aPosition, 1.0);
            gl_Position = uMVP * vec4(aPosition, 1.0);
            vEyePosition = eyePosition.xyz;
            vNormal = normalize(mat3(uModelView) * aNormal);
        }
    """.trimIndent()
    private val fragmentShader = """
        precision mediump float;
        uniform vec4 uColor;
        uniform vec3 uLight;
        varying vec3 vNormal;
        varying vec3 vEyePosition;
        void main() {
            vec3 N = normalize(vNormal);
            vec3 L = normalize(uLight);
            vec3 V = normalize(-vEyePosition);
            vec3 H = normalize(L + V);
            float diffuse = max(dot(N, L), 0.0);
            float specular = pow(max(dot(N, H), 0.0), 34.0) * 0.22;
            float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.10;
            vec3 base = uColor.rgb;
            vec3 lit = base * (0.34 + 0.72 * diffuse) + vec3(specular) + base * rim;
            gl_FragColor = vec4(lit, uColor.a);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.13f, 0.075f, 0.19f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs); GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        normalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        modelHandle = GLES20.glGetUniformLocation(program, "uModel")
        modelViewHandle = GLES20.glGetUniformLocation(program, "uModelView")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        lightHandle = GLES20.glGetUniformLocation(program, "uLight")
        createSphere()
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
        val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) throw RuntimeException("Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
        return shader
    }

    private fun createSphere() {
        val data = ArrayList<Float>()
        val lat = 36; val lon = 48
        for (i in 0 until lat) {
            val a0 = -Math.PI / 2 + Math.PI * i / lat
            val a1 = -Math.PI / 2 + Math.PI * (i + 1) / lat
            for (j in 0 until lon) {
                val b0 = 2 * Math.PI * j / lon
                val b1 = 2 * Math.PI * (j + 1) / lon
                val p00 = point(a0,b0); val p01 = point(a0,b1)
                val p10 = point(a1,b0); val p11 = point(a1,b1)
                addVertex(data,p00); addVertex(data,p10); addVertex(data,p11)
                addVertex(data,p00); addVertex(data,p11); addVertex(data,p01)
            }
        }
        val bytes = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder())
        sphere = bytes.asFloatBuffer().put(data.toFloatArray()).also { it.position(0) }
        sphereCount = data.size / 6
    }
    private fun point(a: Double,b: Double) = floatArrayOf((cos(a)*cos(b)).toFloat(), sin(a).toFloat(), (cos(a)*sin(b)).toFloat())
    private fun addVertex(out: MutableList<Float>, p: FloatArray) {
        out.add(p[0]); out.add(p[1]); out.add(p[2]); out.add(p[0]); out.add(p[1]); out.add(p[2])
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,38f,width.toFloat()/height.coerceAtLeast(1),0.1f,100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val t = (System.currentTimeMillis()-startTime)/1000f
        val bob = if (animating) sin(t*2.0f)*0.035f else 0f
        Matrix.setLookAtM(view,0,0f,0f,zoom,0f,1.85f,0f,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        Matrix.setIdentityM(model,0)
        Matrix.rotateM(model,0,pitch,1f,0f,0f)
        Matrix.rotateM(model,0,yaw,0f,1f,0f)
        Matrix.translateM(model,0,0f,bob,0f)

        // Skirt silhouette and layered fabric
        draw(0f,1.02f,0f,0.78f,0.50f,0.88f,0.49f,0.27f,0.68f)
        draw(0f,1.05f,-0.02f,0.72f,0.48f,0.82f,0.58f,0.39f,0.76f)
        // Legs and shoes peeking below hem
        draw(-0.22f,0.35f,0f,0.105f,0.11f,0.40f,0.94f,0.66f,0.51f)
        draw(0.22f,0.35f,0f,0.105f,0.11f,0.40f,0.94f,0.66f,0.51f)
        draw(-0.22f,0.12f,-0.08f,0.17f,0.27f,0.12f,0.24f,0.11f,0.06f)
        draw(0.22f,0.12f,-0.08f,0.17f,0.27f,0.12f,0.24f,0.11f,0.06f)
        // Torso and bodice
        draw(0f,2.15f,0f,0.37f,0.24f,0.58f,0.94f,0.66f,0.51f)
        draw(0f,2.25f,-0.03f,0.40f,0.25f,0.47f,0.39f,0.18f,0.61f)
        draw(-0.15f,2.30f,-0.255f,0.12f,0.035f,0.30f,0.65f,0.43f,0.81f)
        draw(0.15f,2.30f,-0.255f,0.12f,0.035f,0.30f,0.65f,0.43f,0.81f)
        // Puff sleeves, arms and hands
        for (s in listOf(-1f,1f)) {
            draw(s*0.40f,2.57f,0f,0.23f,0.23f,0.23f,1f,0.91f,0.82f)
            val swing = if(animating) sin(t*2f+s)*0.025f else 0f
            draw(s*0.59f,2.28f+swing,0f,0.105f,0.11f,0.28f,0.94f,0.66f,0.51f)
            draw(s*0.70f,1.99f+swing,-0.04f,0.08f,0.09f,0.20f,0.94f,0.66f,0.51f)
            draw(s*0.72f,1.83f+swing,-0.10f,0.10f,0.065f,0.12f,0.94f,0.66f,0.51f)
            for (i in 0..3) draw(s*(0.65f+i*0.045f),1.69f+swing,-0.12f,0.018f,0.025f,0.09f,0.94f,0.66f,0.51f)
            draw(s*0.59f,1.80f+swing,-0.12f,0.025f,0.025f,0.08f,0.94f,0.66f,0.51f)
        }
        // Neck and head
        draw(0f,2.76f,0f,0.13f,0.14f,0.22f,0.94f,0.66f,0.51f)
        draw(0f,3.25f,0f,0.39f,0.31f,0.47f,0.96f,0.73f,0.61f)
        // Hair cap and thick long side locks
        draw(0f,3.51f,0.06f,0.42f,0.34f,0.31f,0.96f,0.62f,0.08f)
        for (s in listOf(-1f,1f)) {
            draw(s*0.34f,3.12f,0.08f,0.15f,0.18f,0.44f,0.98f,0.70f,0.16f)
            draw(s*0.40f,2.62f,0.10f,0.13f,0.15f,0.48f,0.98f,0.70f,0.16f)
            draw(s*0.46f,2.12f,0.10f,0.12f,0.14f,0.46f,0.98f,0.70f,0.16f)
            draw(s*0.50f,1.62f,0.08f,0.12f,0.13f,0.43f,0.98f,0.70f,0.16f)
            draw(s*0.47f,1.18f,0.05f,0.11f,0.12f,0.34f,0.98f,0.70f,0.16f)
        }
        // Face features: layered eyelids, brows, iris highlights and soft facial shaping
        draw(-0.355f,3.22f,-0.245f,0.055f,0.105f,0.055f,0.88f,0.55f,0.43f)
        draw(0.355f,3.22f,-0.245f,0.055f,0.105f,0.055f,0.88f,0.55f,0.43f)
        val eyeY = 3.31f
        val eyeScale = if(expression==1) 1.20f else 1f
        for (s in listOf(-1f,1f)) {
            val wink = expression==2 && s>0
            if (!wink) {
                draw(s*0.145f,eyeY,-0.285f,0.075f,0.095f*eyeScale,0.045f,0.98f,0.96f,0.90f)
                draw(s*0.145f,eyeY,-0.326f,0.038f,0.052f*eyeScale,0.022f,0.12f,0.49f,0.28f)
                draw(s*0.145f,eyeY,-0.345f,0.019f,0.031f*eyeScale,0.012f,0.025f,0.02f,0.02f)
                draw(s*0.155f,eyeY+0.018f,-0.357f,0.009f,0.012f,0.008f,1f,1f,1f)
                draw(s*0.145f,eyeY-0.085f,-0.337f,0.068f,0.014f,0.012f,0.30f,0.12f,0.09f)
            } else {
                draw(s*0.145f,eyeY,-0.31f,0.075f,0.012f,0.018f,0.10f,0.035f,0.025f)
            }
            draw(s*0.15f,3.455f,-0.292f,0.105f,0.027f,0.026f,0.36f,0.18f,0.07f)
            draw(s*0.25f,3.13f,-0.26f,0.07f,0.035f,0.025f,0.88f,0.39f,0.40f)
        }
        draw(0f,3.205f,-0.316f,0.034f,0.080f,0.034f,0.94f,0.66f,0.53f)
        draw(0f,3.155f,-0.350f,0.042f,0.026f,0.026f,0.98f,0.71f,0.59f)
        draw(-0.026f,3.145f,-0.363f,0.012f,0.008f,0.009f,0.72f,0.36f,0.32f)
        draw(0.026f,3.145f,-0.363f,0.012f,0.008f,0.009f,0.72f,0.36f,0.32f)
        draw(0f,3.045f,-0.326f,0.078f,if(expression==1)0.068f else 0.024f,0.026f,0.68f,0.16f,0.24f)
        draw(0f,3.025f,-0.346f,0.052f,0.010f,0.012f,0.94f,0.39f,0.42f)
        draw(-0.245f,3.12f,-0.285f,0.070f,0.035f,0.018f,1f,0.62f,0.57f)
        draw(0.245f,3.12f,-0.285f,0.070f,0.035f,0.018f,1f,0.62f,0.57f)
        // Fringe and golden hair details
        for(i in -3..3) draw(i*0.095f,3.53f-abs(i)*0.012f,-0.25f,0.085f,0.12f,0.12f,1f,0.78f,0.25f)
        // Long braid behind shoulder, visible on right
        draw(0.62f,2.95f,0.25f,0.16f,0.18f,0.50f,0.95f,0.59f,0.06f)
        draw(0.78f,2.42f,0.24f,0.15f,0.16f,0.48f,1f,0.74f,0.18f)
        draw(0.88f,1.88f,0.20f,0.14f,0.15f,0.45f,0.95f,0.59f,0.06f)
        draw(0.95f,1.38f,0.16f,0.13f,0.14f,0.40f,1f,0.74f,0.18f)
    }

    fun updateAppearance(value: CharacterAppearance) { appearance = value }

    private fun draw(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        Matrix.setIdentityM(model,0)
        Matrix.rotateM(model,0,pitch,1f,0f,0f)
        Matrix.rotateM(model,0,yaw,0f,1f,0f)
        val t = (System.currentTimeMillis()-startTime)/1000f
        Matrix.translateM(model,0,0f,if(animating)sin(t*2f)*0.035f else 0f,0f)
        Matrix.translateM(model,0,x,y,z)
        Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniformMatrix4fv(modelHandle,1,false,model,0)
        val modelView = FloatArray(16)
        Matrix.multiplyMM(modelView, 0, view, 0, model, 0)
        GLES20.glUniformMatrix4fv(modelViewHandle,1,false,modelView,0)
        // Material colors come from the independently updateable character resource pack.
        var cr = r; var cg = g; var cb = b
        if (r > 0.85f && g > 0.45f && b < 0.35f) {
            val material = if (g > 0.73f) appearance.hairHighlight
                else if (g < 0.65f) appearance.hairShadow else appearance.hair
            cr = material[0]; cg = material[1]; cb = material[2]
        } else if (r > 0.90f && g > 0.60f && b >= 0.35f) {
            val material = if (g > 0.70f) appearance.skinLight else appearance.skin
            cr = material[0]; cg = material[1]; cb = material[2]
        } else if (b > 0.50f && r < 0.80f) {
            val material = if (g > 0.35f) appearance.dressLight else appearance.dressDark
            cr = material[0]; cg = material[1]; cb = material[2]
        }
        GLES20.glUniform4f(colorHandle,cr,cg,cb,1f)
        GLES20.glUniform3f(lightHandle,-0.45f,0.82f,0.72f)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        sphere!!.position(0)
        GLES20.glVertexAttribPointer(positionHandle,3,GLES20.GL_FLOAT,false,24,sphere)
        sphere!!.position(3)
        GLES20.glVertexAttribPointer(normalHandle,3,GLES20.GL_FLOAT,false,24,sphere)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,sphereCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
    }

    fun touchDown(x:Float,y:Float) { lastX=x; lastY=y }
    fun touchMove(x:Float,y:Float) {
        yaw += (x-lastX)*0.55f
        pitch = (pitch+(y-lastY)*0.35f).coerceIn(-24f,24f)
        lastX=x; lastY=y
    }
    fun touchUp() {}
    fun pinchStart(e:MotionEvent) { pinchDistance=distance(e) }
    fun pinchMove(e:MotionEvent) {
        val d=distance(e)
        if(pinchDistance>0f) zoom=(zoom+(pinchDistance-d)*0.012f).coerceIn(3.5f,8.5f)
        pinchDistance=d
    }
    private fun distance(e:MotionEvent):Float {
        if(e.pointerCount<2)return 0f
        val dx=e.getX(0)-e.getX(1); val dy=e.getY(0)-e.getY(1)
        return sqrt(dx*dx+dy*dy)
    }
    fun resetView() { yaw=0f; pitch=0f; zoom=5.6f }
    fun toggleAnimation() { animating=!animating }
    fun setExpression(value:Int) { expression=value }
}
