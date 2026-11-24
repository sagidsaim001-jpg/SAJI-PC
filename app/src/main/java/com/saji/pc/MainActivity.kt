package com.saji.pc

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private external fun nativeEngineStatus(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(15, 19, 29))
        }
        val title = TextView(this).apply {
            text = "SAJI PC"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ARM32 Windows compatibility project"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        val status = TextView(this).apply {
            text = nativeEngineStatus()
            textSize = 14f
            setTextColor(Color.rgb(130, 210, 170))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        val selectExe = Button(this).apply {
            text = "Select Windows EXE (coming soon)"
            isEnabled = false
        }
        val runtime = Button(this).apply {
            text = "Runtime Manager (coming soon)"
            isEnabled = false
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(selectExe)
        root.addView(runtime)
        setContentView(root)
    }

    companion object {
        init { System.loadLibrary("sajipc") }
    }
}
