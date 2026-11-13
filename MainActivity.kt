package com.brightnessbooster.pro

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : Activity(), GLSurfaceView.Renderer {
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: MainActivity
    private var player: MediaPlayer? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null
    private var oesTexture = 0
    @Volatile private var brightness = 0.12f
    @Volatile private var gamma = 1.25f
    @Volatile private var shadows = 0.20f
    private var position = 0
    private var ready = false
    private lateinit var status: TextView

    private val vertexShader = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
    """.trimIndent()

    private val fragmentShader = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTexture;
        uniform float uBrightness;
        uniform float uGamma;
        uniform float uShadows;
        varying vec2 vTexCoord;
        void main() {
            vec4 src = texture2D(uTexture, vTexCoord);
            vec3 c = max(src.rgb, vec3(0.0));
            c = c + vec3(uBrightness);
            c = pow(clamp(c, 0.0, 1.0), vec3(1.0 / max(uGamma, 0.25)));
            float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
            float shadowMask = 1.0 - smoothstep(0.05, 0.65, l);
            c = clamp(c + shadowMask * uShadows, 0.0, 1.0);
            gl_FragColor = vec4(c, src.a);
        }
    """.trimIndent()

    private val vertices = floatArrayOf(
        -1f,-1f, 0f,1f,  1f,-1f, 1f,1f,
        -1f, 1f, 0f,0f,  1f, 1f, 1f,0f
    )
    private lateinit var vertexBuffer: FloatBuffer
    private var program = 0
    private var positionHandle = 0
    private var texHandle = 0
    private var matrixHandle = 0
    private var brightnessHandle = 0
    private var gammaHandle = 0
    private var shadowsHandle = 0
    private val matrix = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = this
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xff101318.toInt())
            gravity = Gravity.CENTER
        }
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(glView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 12)
            setBackgroundColor(0xff202630.toInt())
        }
        status = TextView(this).apply {
            text = "Choose a video to begin"
            setTextColor(0xffffffff.toInt())
            textSize = 16f
        }
        controls.addView(status)
        controls.addView(makeSlider("Brightness", 0, 60, 12) { brightness = it / 100f; glView.requestRender() })
        controls.addView(makeSlider("Gamma", 50, 200, 125) { gamma = it / 100f; glView.requestRender() })
        controls.addView(makeSlider("Shadow recovery", 0, 60, 20) { shadows = it / 100f; glView.requestRender() })
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply { text = "Open video"; setOnClickListener { chooseVideo() } })
        row.addView(Button(this).apply { text = "Reset"; setOnClickListener {
            brightness = 0.12f; gamma = 1.25f; shadows = 0.20f
            Toast.makeText(this@MainActivity, "Picture controls reset", Toast.LENGTH_SHORT).show()
        } })
        controls.addView(row)
        root.addView(controls, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        root.requestFocus()
    }

    private fun makeSlider(label: String, min: Int, max: Int, initial: Int, changed: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = label; setTextColor(0xffffffff.toInt()); textSize = 14f; minWidth = 150 }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = initial - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { changed(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        box.addView(title)
        box.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return box
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, 41)
    }

    @Deprecated("Legacy callback retained for broad Fire OS compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
                play(uri)
            }
        }
    }

    private fun play(uri: Uri) {
        try {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { mp ->
                    ready = true
                    mp.start()
                    runOnUiThread { status.text = "Playing • Use the sliders to tune the picture" }
                }
                setOnCompletionListener { runOnUiThread { status.text = "Playback finished" } }
                setOnErrorListener { _, _, _ ->
                    runOnUiThread { status.text = "This video format could not be played" }
                    true
                }
                val st = surfaceTexture
                if (st != null) setSurface(Surface(st))
                prepareAsync()
            }
            status.text = "Loading video…"
        } catch (e: Exception) {
            status.text = "Could not open this video"
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        oesTexture = names[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        surfaceTexture = SurfaceTexture(oesTexture).also { st ->
            st.setOnFrameAvailableListener { glView.requestRender() }
            videoSurface = Surface(st)
            player?.setSurface(videoSurface)
        }
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs); GLES20.glAttachShader(it, fs); GLES20.glLinkProgram(it)
        }
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        matrixHandle = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        brightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
        gammaHandle = GLES20.glGetUniformLocation(program, "uGamma")
        shadowsHandle = GLES20.glGetUniformLocation(program, "uShadows")
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)
        GLES20.glClearColor(0f,0f,0f,1f)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { GLES20.glViewport(0, 0, width, height) }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val st = surfaceTexture ?: return
        try { st.updateTexImage() } catch (_: Exception) { return }
        st.getTransformMatrix(matrix)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniform1f(brightnessHandle, brightness)
        GLES20.glUniform1f(gammaHandle, gamma)
        GLES20.glUniform1f(shadowsHandle, shadows)
        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    override fun onDestroy() {
        player?.release()
        videoSurface?.release()
        surfaceTexture?.release()
        super.onDestroy()
    }
}
