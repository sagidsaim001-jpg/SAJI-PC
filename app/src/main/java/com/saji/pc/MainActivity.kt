package com.saji.pc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.database.Cursor
import android.provider.OpenableColumns

class MainActivity : Activity() {
    private external fun nativeEngineStatus(): String

    private lateinit var selectedExeLabel: TextView
    private lateinit var runtimeStatus: TextView
    private var selectedExe: Uri? = null

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
            text = "ARM32 Windows compatibility environment"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        runtimeStatus = TextView(this).apply {
            text = nativeEngineStatus()
            textSize = 14f
            setTextColor(Color.rgb(130, 210, 170))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        selectedExeLabel = TextView(this).apply {
            text = "No Windows EXE selected"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        val selectExe = Button(this).apply {
            text = "Select Windows EXE"
            setOnClickListener { openExePicker() }
        }
        val launchExe = Button(this).apply {
            text = "Launch EXE"
            isEnabled = false
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Windows runtime is not installed yet.", Toast.LENGTH_LONG).show()
            }
        }
        val runtime = Button(this).apply {
            text = "Runtime Manager"
            setOnClickListener { showRuntimeStatus() }
        }
        val note = TextView(this).apply {
            text = "File selection is available. EXE execution will be enabled after Box86, ARMHF Linux and 32-bit Wine are integrated."
            textSize = 12f
            setTextColor(Color.rgb(180, 185, 198))
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(runtimeStatus)
        root.addView(selectExe)
        root.addView(selectedExeLabel)
        root.addView(launchExe)
        root.addView(runtime)
        root.addView(note)
        setContentView(root)
    }

    private fun openExePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_EXE)
    }

    @Deprecated("Deprecated in Android API, retained for broad platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers grant only temporary access.
        }
        selectedExe = uri
        val name = queryDisplayName(uri) ?: "Selected Windows file"
        val isPe = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.read() == 0x4D && stream.read() == 0x5A
            } ?: false
        } catch (_: Exception) {
            false
        }
        selectedExeLabel.text = if (isPe) "$name\nWindows EXE signature detected (MZ)" else "$name\nWarning: MZ/EXE signature not detected"
        Toast.makeText(this, if (isPe) "EXE file selected. Runtime integration is still pending." else "File selected, but it may not be a Windows EXE.", Toast.LENGTH_LONG).show()
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun showRuntimeStatus() {
        runtimeStatus.text = nativeEngineStatus() +
            "\n\nBox86: not integrated" +
            "\nARMHF root filesystem: not integrated" +
            "\n32-bit Wine: not integrated" +
            "\nGraphics/audio/input bridge: pending"
        Toast.makeText(this, "Runtime diagnostics updated.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_EXE = 4102
        init { System.loadLibrary("sajipc") }
    }
}
