package com.rapunzel.studio

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.Toast
import android.opengl.GLSurfaceView

class MainActivity : Activity() {
    private lateinit var renderer: PrincessRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(35, 19, 54)
        window.navigationBarColor = Color.rgb(35, 19, 54)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(35, 19, 54)) }
        val gl = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            renderer = PrincessRenderer(ResourceManager.load(this@MainActivity))
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(gl, FrameLayout.LayoutParams(-1, -1))

        val title = TextView(this).apply {
            text = "RAPUNZEL 3D STUDIO\nCHARACTER WORKSHOP"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(12, 18, 12, 14)
            setBackgroundColor(Color.argb(175, 35, 19, 54))
        }
        root.addView(title, FrameLayout.LayoutParams(-1, dp(72), Gravity.TOP))

        val topActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(8, 6, 8, 6)
            setBackgroundColor(Color.argb(165, 35, 19, 54))
        }
        addButton(topActions, "⟲ RESET") { renderer.resetView() }
        addButton(topActions, "▶ IDLE") { renderer.toggleAnimation() }
        addButton(topActions, "⬇ UPDATE") { checkResources(true) }
        val topLp = FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP).apply { topMargin = dp(78) }
        root.addView(topActions, topLp)

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(8, 10, 8, 12)
            setBackgroundColor(Color.argb(220, 35, 19, 54))
        }
        val caption = TextView(this).apply {
            text = "EXPRESSIONS"
            setTextColor(Color.rgb(245, 213, 157))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        bottom.addView(caption)
        val expressions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        addButton(expressions, "😊 SMILE") { renderer.setExpression(0) }
        addButton(expressions, "😮 WOW") { renderer.setExpression(1) }
        addButton(expressions, "😉 WINK") { renderer.setExpression(2) }
        bottom.addView(expressions)
        val hint = TextView(this).apply {
            text = "Drag to rotate  •  Pinch to zoom  •  Rotate the character to inspect"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 2)
        }
        bottom.addView(hint)
        root.addView(bottom, FrameLayout.LayoutParams(-1, dp(116), Gravity.BOTTOM))

        gl.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { renderer.touchDown(event.x, event.y); true }
                MotionEvent.ACTION_POINTER_DOWN -> { renderer.pinchStart(event); true }
                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount >= 2) renderer.pinchMove(event)
                    else renderer.touchMove(event.x, event.y)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { renderer.touchUp(); true }
                else -> true
            }
        }
        setContentView(root)
        checkResources(false)
    }

    private fun checkResources(showCurrent: Boolean) {
        ResourceManager.checkForUpdates(this) { updated, appearance, message ->
            runOnUiThread {
                if (updated && appearance != null) renderer.updateAppearance(appearance)
                if (updated || showCurrent || message.startsWith("Resource update failed")) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun addButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label
            textSize = 10f
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(112, 70, 145)))
            setOnClickListener { action() }
        }
        parent.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            setMargins(dp(3), dp(4), dp(3), dp(4))
        })
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
