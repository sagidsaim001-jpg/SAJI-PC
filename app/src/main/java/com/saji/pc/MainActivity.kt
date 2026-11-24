package com.saji.pc

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private external fun nativeEngineStatus(): String

    private lateinit var runtimeManager: RuntimeManager
    private lateinit var runtimeStatus: TextView
    private lateinit var selectedExeLabel: TextView
    private lateinit var desktopView: WebView
    private lateinit var menuScroll: ScrollView
    private lateinit var installButton: Button
    private lateinit var testRuntimeButton: Button
    private lateinit var launchButton: Button
    private lateinit var installedAppsButton: Button
    private lateinit var stopButton: Button
    private lateinit var hideDesktopButton: Button

    private var selectedExeFile: File? = null
    private var selectedExeMachine: Int? = null
    private var runtimeTestPassed = false
    private var runtimeTestMessage = "Device test abhi run nahi hua."
    private var runtimeProcess: Process? = null
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val processLog = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtimeManager = RuntimeManager(this)
        buildInterface()
        refreshRuntimeState()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(13, 17, 27))
            setPadding(dp(14), dp(12), dp(14), dp(8))
        }

        menuScroll = ScrollView(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "SAJI PC"
            textSize = 29f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(2))
        }
        val subtitle = TextView(this).apply {
            text = "32-bit Windows desktop on ARM32 Android"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        runtimeStatus = TextView(this).apply {
            text = nativeEngineStatus()
            textSize = 13f
            setTextColor(Color.rgb(130, 210, 170))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(12))
        }
        installButton = makeButton("Install / Update Windows Runtime") {
            installRuntime()
        }
        val selectButton = makeButton("Select Windows EXE") {
            openExePicker()
        }
        selectedExeLabel = TextView(this).apply {
            text = "No Windows EXE selected"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(8), dp(8), dp(10))
        }
        testRuntimeButton = makeButton("Verify Box86 + Wine Runtime") {
            verifyRuntime()
        }
        testRuntimeButton.isEnabled = false
        launchButton = makeButton("Launch in Desktop") {
            launchWindowsExe()
        }
        launchButton.isEnabled = false
        installedAppsButton = makeButton("Browse Installed Windows Apps") {
            showInstalledApps()
        }
        installedAppsButton.isEnabled = false
        stopButton = makeButton("Stop Desktop Session") {
            stopDesktopSession()
        }
        stopButton.isEnabled = false
        hideDesktopButton = makeButton("Hide Desktop View") {
            desktopView.visibility = View.GONE
            menuScroll.visibility = View.VISIBLE
            hideDesktopButton.visibility = View.GONE
        }

        val note = TextView(this).apply {
            text = "Target: Windows x86 (32-bit) EXE. x64 applications are not supported by this ARM32 build. App compatibility depends on Wine."
            textSize = 11f
            setTextColor(Color.rgb(172, 180, 196))
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(12), dp(8), dp(10))
        }

        panel.addView(title)
        panel.addView(subtitle)
        panel.addView(runtimeStatus)
        panel.addView(installButton, fullWidthParams())
        panel.addView(selectButton, fullWidthParams())
        panel.addView(selectedExeLabel)
        panel.addView(testRuntimeButton, fullWidthParams())
        panel.addView(launchButton, fullWidthParams())
        panel.addView(installedAppsButton, fullWidthParams())
        panel.addView(stopButton, fullWidthParams())
        panel.addView(note)
        menuScroll.addView(panel)
        root.addView(menuScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        hideDesktopButton.visibility = View.GONE
        root.addView(hideDesktopButton, fullWidthParams())

        desktopView = WebView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        root.addView(desktopView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setOnClickListener { action() }
        }
    }

    private fun fullWidthParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(48)
    ).apply {
        bottomMargin = dp(5)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun refreshRuntimeState() {
        val ready = runtimeManager.isReady()
        runtimeStatus.text = if (ready) {
            nativeEngineStatus() + "\nWine32 + Box86 + ARMHF runtime installed\n" + runtimeTestMessage
        } else {
            nativeEngineStatus() + "\nWindows runtime not installed"
        }
        installButton.isEnabled = runtimeProcess?.isAlive != true
        testRuntimeButton.isEnabled = ready && runtimeProcess?.isAlive != true
        launchButton.isEnabled = ready && runtimeTestPassed &&
            selectedExeFile?.isFile == true &&
            selectedExeMachine == 0x014c &&
            runtimeProcess?.isAlive != true
        installedAppsButton.isEnabled = ready && runtimeProcess?.isAlive != true
        stopButton.isEnabled = runtimeProcess?.isAlive == true
    }

    private fun installRuntime() {
        if (runtimeProcess?.isAlive == true) {
            Toast.makeText(this, "Pehle desktop session stop karein.", Toast.LENGTH_LONG).show()
            return
        }
        installButton.isEnabled = false
        launchButton.isEnabled = false
        runtimeTestPassed = false
        runtimeTestMessage = "Runtime install complete hone ke baad device test required hai."
        runtimeStatus.text = "Runtime manifest check ho raha hai…"
        worker.execute {
            try {
                runtimeManager.install { message ->
                    runOnUiThread { runtimeStatus.text = message }
                }
                runOnUiThread {
                    runtimeStatus.text = "Windows runtime installed. 32-bit EXE select karke launch karein."
                    refreshRuntimeState()
                    Toast.makeText(this, "Runtime installation complete.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    runtimeStatus.text = "Runtime install failed: ${error.message ?: "unknown error"}"
                    refreshRuntimeState()
                    Toast.makeText(this, "Runtime install failed. Details status mein hain.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun verifyRuntime() {
        if (!runtimeManager.isReady()) {
            runtimeTestPassed = false
            runtimeTestMessage = "Runtime files missing."
            refreshRuntimeState()
            Toast.makeText(this, "Pehle runtime install karein.", Toast.LENGTH_LONG).show()
            return
        }
        testRuntimeButton.isEnabled = false
        launchButton.isEnabled = false
        runtimeTestPassed = false
        runtimeTestMessage = "Phone par PRoot + ARMHF + Box86 + Wine32 test ho raha hai..."
        runtimeStatus.text = runtimeTestMessage

        worker.execute {
            var process: Process? = null
            val output = StringBuilder()
            try {
                val proot = runtimeManager.prootBinary
                val rootfs = runtimeManager.rootfsDir
                val command = arrayListOf(
                    proot.absolutePath,
                    "--kill-on-exit",
                    "--rootfs=" + rootfs.absolutePath,
                    "--bind=/dev:/dev",
                    "--bind=/proc:/proc",
                    "--cwd=/",
                    "/usr/local/bin/box86",
                    "/usr/lib/wine/wine",
                    "--version"
                )
                val builder = ProcessBuilder(command).redirectErrorStream(true)
                builder.environment()["HOME"] = "/home/saji"
                builder.environment()["WINEPREFIX"] = "/home/saji/.wine"
                builder.environment()["WINEARCH"] = "win32"
                builder.environment()["BOX86_LD_LIBRARY_PATH"] =
                    "/usr/lib/i386-linux-gnu:/lib/i386-linux-gnu:/usr/lib/wine"
                builder.environment()["BOX86_PATH"] = "/usr/lib/wine"
                builder.environment()["BOX86_NOBANNER"] = "1"
                process = builder.start()
                val activeProcess = process
                val reader = Thread {
                    try {
                        BufferedReader(InputStreamReader(activeProcess.inputStream)).use { stream ->
                            while (true) {
                                val line = stream.readLine() ?: break
                                synchronized(output) {
                                    if (output.length > 5000) output.delete(0, 1500)
                                    output.append(line).append(10.toChar())
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                reader.start()
                val finished = activeProcess.waitFor(45, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    activeProcess.destroyForcibly()
                    throw IllegalStateException("PRoot/Wine test 45 seconds mein complete nahi hua.")
                }
                reader.join(2000)
                val resultText = synchronized(output) { output.toString() }.trim()
                if (activeProcess.exitValue() != 0 || !resultText.contains("wine-", ignoreCase = true)) {
                    throw IllegalStateException(
                        "Wine32 test fail hua (exit " + activeProcess.exitValue() + "): " +
                            resultText.takeLast(1000)
                    )
                }
                runOnUiThread {
                    runtimeTestPassed = true
                    runtimeTestMessage = "Device runtime test PASS: " + resultText
                    refreshRuntimeState()
                    Toast.makeText(this, "PRoot + Box86 + Wine32 test PASS.", Toast.LENGTH_LONG).show()
                }
            } catch (error: Exception) {
                process?.destroyForcibly()
                val detail = synchronized(output) { output.toString() }.takeLast(1000)
                runOnUiThread {
                    runtimeTestPassed = false
                    runtimeTestMessage = "Device runtime test FAIL: " +
                        (error.message ?: "unknown error") + " " + detail
                    refreshRuntimeState()
                    Toast.makeText(this, "Is phone par runtime verification fail hui. Details status mein hain.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openExePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_EXE)
    }

    @Deprecated("Deprecated API retained for platform compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
        }

        try {
            val name = safeFileName(queryDisplayName(uri) ?: "selected.exe")
            val target = File(File(filesDir, "windows"), name)
            target.parentFile?.mkdirs()
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Selected file open nahi ho saki." }
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }

            selectExecutable(target, name, "Private app storage mein saved")
            Toast.makeText(
                this,
                if (selectedExeMachine == 0x014c) "EXE import complete." else "File valid 32-bit Windows EXE nahi hai.",
                Toast.LENGTH_LONG
            ).show()
        } catch (error: Exception) {
            selectedExeFile = null
            selectedExeMachine = null
            selectedExeLabel.text = "File import failed: ${error.message ?: "unknown error"}"
            refreshRuntimeState()
            Toast.makeText(this, "Selected file import nahi ho saki.", Toast.LENGTH_LONG).show()
        }
    }

    private fun selectExecutable(file: File, displayName: String, sourceNote: String) {
        val pe = inspectPe(file)
        selectedExeFile = if (pe.isWindowsPe && pe.machine == 0x014c) file else null
        selectedExeMachine = if (pe.isWindowsPe) pe.machine else null
        selectedExeLabel.text = when {
            !pe.isWindowsPe -> "$displayName\\nValid Windows PE file nahi hai."
            pe.machine == 0x014c -> "$displayName\\nWindows x86 (32-bit) EXE detected\\n$sourceNote"
            pe.machine == 0x8664 -> "$displayName\\nWindows x64 EXE detected\\nARM32 target par supported nahi"
            else -> "$displayName\\nPE machine 0x" + pe.machine.toString(16) + "\\nSupported x86 target nahi"
        }
        refreshRuntimeState()
    }

    private fun showInstalledApps() {
        if (!runtimeManager.isReady()) {
            Toast.makeText(this, "Pehle Windows runtime install karein.", Toast.LENGTH_LONG).show()
            return
        }
        val driveC = File(runtimeManager.rootfsDir, "home/saji/.wine/drive_c")
        val searchRoots = listOf(
            File(driveC, "Program Files"),
            File(driveC, "Program Files (x86)"),
            File(driveC, "users")
        )
        val executables = mutableListOf<File>()
        searchRoots.forEach { root -> collectExecutables(root, 0, executables) }
        if (executables.isEmpty()) {
            Toast.makeText(this, "Wine prefix mein installed EXE nahi mila. Pehle installer run karein.", Toast.LENGTH_LONG).show()
            return
        }
        val labels = executables.map { file ->
            file.relativeTo(driveC).invariantSeparatorsPath
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Installed Windows Apps")
            .setItems(labels) { _, which ->
                selectExecutable(executables[which], labels[which], "Wine prefix mein installed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun collectExecutables(directory: File, depth: Int, output: MutableList<File>) {
        if (depth > 7 || output.size >= 250 || !directory.isDirectory) return
        val children = directory.listFiles() ?: return
        children.sortedBy { it.name.lowercase() }.forEach { child ->
            if (output.size >= 250) return
            if (child.isDirectory) {
                collectExecutables(child, depth + 1, output)
            } else if (child.isFile && child.extension.equals("exe", ignoreCase = true) &&
                !child.name.startsWith("unins", ignoreCase = true)) {
                output.add(child)
            }
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
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun launchWindowsExe() {
        val exe = selectedExeFile
        if (exe == null || !exe.isFile || selectedExeMachine != 0x014c) {
            Toast.makeText(this, "Pehle valid 32-bit x86 Windows EXE select karein.", Toast.LENGTH_LONG).show()
            return
        }
        if (!runtimeManager.isReady()) {
            Toast.makeText(this, "Pehle Windows runtime install karein.", Toast.LENGTH_LONG).show()
            refreshRuntimeState()
            return
        }

        try {
            val proot = runtimeManager.prootBinary
            val rootfs = runtimeManager.rootfsDir
            val windowsDir = File(filesDir, "windows")
            val rootPath = rootfs.canonicalFile.toPath()
            val exePath = exe.canonicalFile.toPath()
            val guestExePath = if (exePath.startsWith(rootPath)) {
                "/" + rootPath.relativize(exePath).toString().replace(File.separatorChar, '/')
            } else {
                require(exePath.startsWith(windowsDir.canonicalFile.toPath())) {
                    "Selected EXE PRoot guest filesystem ke andar nahi hai."
                }
                "/mnt/saji/" + windowsDir.canonicalFile.toPath()
                    .relativize(exePath).toString().replace(File.separatorChar, '/')
            }
            val command = arrayListOf(
                proot.absolutePath,
                "--kill-on-exit",
                "--rootfs=${rootfs.absolutePath}",
                "--bind=/dev:/dev",
                "--bind=/proc:/proc",
                "--bind=${windowsDir.absolutePath}:/mnt/saji",
                "--cwd=/",
                "/usr/local/bin/saji-desktop",
                guestExePath
            )
            processLog.setLength(0)
            val builder = ProcessBuilder(command).redirectErrorStream(true)
            builder.environment()["HOME"] = filesDir.absolutePath
            builder.environment()["TMPDIR"] = cacheDir.absolutePath
            builder.environment()["RUST_LOG"] = "info"
            val process = builder.start()
            runtimeProcess = process
            Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            synchronized(processLog) {
                                if (processLog.length > 12_000) processLog.delete(0, 4_000)
                                processLog.append(line).append('\n')
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }.start()

            refreshRuntimeState()
            runtimeStatus.text = "PRoot start ho gaya. Desktop server ready check ho raha hai…"
            worker.execute {
                var ready = false
                for (attempt in 0 until 40) {
                    if (!process.isAlive) break
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress("127.0.0.1", 6080), 500)
                            ready = true
                        }
                        if (ready) break
                    } catch (_: Exception) {
                    }
                    Thread.sleep(500)
                }
                runOnUiThread {
                    if (ready && process.isAlive) {
                        menuScroll.visibility = View.GONE
                        hideDesktopButton.visibility = View.VISIBLE
                        desktopView.visibility = View.VISIBLE
                        desktopView.loadUrl(
                            "http://127.0.0.1:6080/vnc.html?host=127.0.0.1&port=6080&autoconnect=true&resize=scale"
                        )
                        runtimeStatus.text = "Windows desktop session running. Touch aur keyboard noVNC ke through forward honge."
                        refreshRuntimeState()
                    } else {
                        val detail = synchronized(processLog) { processLog.toString().takeLast(900) }
                        runtimeStatus.text = "Desktop start nahi hua. PRoot/Wine log: $detail"
                        refreshRuntimeState()
                    }
                }
            }
        } catch (error: Exception) {
            runtimeStatus.text = "Launch failed: ${error.message ?: "unknown error"}"
            refreshRuntimeState()
        }
    }

    private fun stopDesktopSession() {
        val process = runtimeProcess
        runtimeProcess = null
        desktopView.stopLoading()
        desktopView.loadUrl("about:blank")
        desktopView.visibility = View.GONE
        menuScroll.visibility = View.VISIBLE
        hideDesktopButton.visibility = View.GONE
        if (process != null) {
            worker.execute {
                try {
                    process.destroy()
                    if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                } catch (_: Exception) {
                    process.destroyForcibly()
                }
                runOnUiThread {
                    runtimeStatus.text = "Desktop session stopped."
                    refreshRuntimeState()
                }
            }
        } else {
            refreshRuntimeState()
        }
    }

    override fun onBackPressed() {
        if (::desktopView.isInitialized && desktopView.visibility == View.VISIBLE) {
            desktopView.visibility = View.GONE
            menuScroll.visibility = View.VISIBLE
            hideDesktopButton.visibility = View.GONE
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        runtimeProcess?.destroy()
        worker.shutdownNow()
        if (::desktopView.isInitialized) desktopView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_EXE = 4102

        init {
            System.loadLibrary("sajipc")
        }
    }
}
