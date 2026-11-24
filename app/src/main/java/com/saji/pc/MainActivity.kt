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
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class MainActivity : Activity() {
    private external fun nativeEngineStatus(): String
    private lateinit var selectedExeLabel: TextView
    private lateinit var runtimeStatus: TextView
    private var selectedExeFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 36, 28, 36)
            setBackgroundColor(Color.rgb(15, 19, 29))
        }
        val title = TextView(this).apply {
            text = "SAJI PC"; textSize = 32f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "ARM32 Windows compatibility environment"; textSize = 15f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
        }
        runtimeStatus = TextView(this).apply {
            text = nativeEngineStatus(); textSize = 14f
            setTextColor(Color.rgb(130, 210, 170)); gravity = Gravity.CENTER
            setPadding(0, 24, 0, 16)
        }
        selectedExeLabel = TextView(this).apply {
            text = "No Windows EXE selected"; textSize = 13f
            setTextColor(Color.LTGRAY); gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }
        val selectExe = Button(this).apply {
            text = "Select Windows EXE"; setOnClickListener { openExePicker() }
        }
        val launchExe = Button(this).apply {
            text = "Launch EXE"; isEnabled = false
            setOnClickListener {
                Toast.makeText(this@MainActivity, "Windows runtime is not ready. EXE is safely stored in app storage.", Toast.LENGTH_LONG).show()
            }
        }
        val runtime = Button(this).apply {
            text = "Runtime Manager"; setOnClickListener { showRuntimeStatus() }
        }
        val note = TextView(this).apply {
            text = "Selected files are copied to private app storage and checked for Windows PE architecture. Execution stays disabled until the runtime is verified."
            textSize = 12f; setTextColor(Color.rgb(180, 185, 198))
            gravity = Gravity.CENTER; setPadding(0, 20, 0, 0)
        }
        root.addView(title); root.addView(subtitle); root.addView(runtimeStatus)
        root.addView(selectExe); root.addView(selectedExeLabel); root.addView(launchExe)
        root.addView(runtime); root.addView(note)
        setContentView(root)
    }

    private fun openExePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
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
        } catch (_: SecurityException) { }
        try {
            val name = safeFileName(queryDisplayName(uri) ?: "selected.exe")
            val target = File(File(filesDir, "windows"), name)
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open selected file" }
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            val pe = inspectPe(target)
            selectedExeFile = if (pe.isWindowsPe) target else null
            selectedExeLabel.text = when {
                !pe.isWindowsPe -> "$name\nNot a valid Windows PE executable"
                pe.machine == 0x014c -> "$name\nWindows PE detected: x86 (32-bit)\nSaved in app storage"
                pe.machine == 0x8664 -> "$name\nWindows PE detected: x64 (not supported by ARM32 target)\nSaved in app storage"
                else -> "$name\nWindows PE detected: machine 0x" + pe.machine.toString(16) + "\nSaved in app storage"
            }
            Toast.makeText(this, if (pe.isWindowsPe) "Windows PE file copied successfully." else "File copied, but Windows PE header was not found.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            selectedExeFile = null
            selectedExeLabel.text = "Could not import file: " + (e.message ?: "unknown error")
            Toast.makeText(this, "Could not import selected file.", Toast.LENGTH_LONG).show()
        }
    }

    private data class PeInfo(val isWindowsPe: Boolean, val machine: Int = 0)

    private fun inspectPe(file: File): PeInfo {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 64) return PeInfo(false)
            if (raf.readUnsignedByte() != 0x4D || raf.readUnsignedByte() != 0x5A) return PeInfo(false)
            raf.seek(0x3C)
            val peOffset = Integer.reverseBytes(raf.readInt()).toLong() and 0xffffffffL
            if (peOffset < 64 || peOffset + 6 > raf.length()) return PeInfo(false)
            raf.seek(peOffset)
            if (raf.readUnsignedByte() != 0x50 || raf.readUnsignedByte() != 0x45 ||
                raf.readUnsignedByte() != 0 || raf.readUnsignedByte() != 0) return PeInfo(false)
            val machine = raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8)
            return PeInfo(true, machine)
        }
    }

    private fun safeFileName(value: String): String {
        val cleaned = value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return cleaned.take(100).ifBlank { "selected.exe" }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else null
        } catch (_: Exception) { null } finally { cursor?.close() }
    }

    private fun showRuntimeStatus() {
        runtimeStatus.text = nativeEngineStatus() +
            "\n\nBox86: Android ARM32 build pipeline running" +
            "\nARMHF root filesystem: not integrated" +
            "\n32-bit Wine: not integrated" +
            "\nGraphics/audio/input bridge: pending" +
            "\nSelected EXE: " + (selectedExeFile?.name ?: "none")
        Toast.makeText(this, "Runtime diagnostics updated.", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_EXE = 4102
        init { System.loadLibrary("sajipc") }
    }
}
