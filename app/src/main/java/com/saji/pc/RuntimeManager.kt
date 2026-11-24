package com.saji.pc

import android.content.Context
import android.os.StatFs
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest

class RuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeDir = File(appContext.filesDir, "saji-runtime")
    val rootfsDir = File(runtimeDir, "rootfs")
    val prootBinary = File(runtimeDir, "bin/libproot.so")
    private val readyMarker = File(runtimeDir, "runtime-ready.json")

    private val manifestUrl =
        "https://github.com/sagidsaim001-jpg/SAJI-PC/releases/download/runtime-latest/runtime-manifest.json"

    fun isReady(): Boolean {
        val markerValid = try {
            readyMarker.isFile && JSONObject(readyMarker.readText()).optString("architecture") == "armhf"
        } catch (_: Exception) {
            false
        }
        return markerValid &&
            prootBinary.isFile && prootBinary.canExecute() &&
            File(rootfsDir, "usr/local/bin/box86").canExecute() &&
            File(rootfsDir, "usr/local/bin/saji-desktop").canExecute() &&
            File(rootfsDir, "usr/lib/wine/wine").isFile &&
            File(rootfsDir, "usr/share/novnc/vnc.html").isFile
    }

    @Throws(Exception::class)
    fun install(progress: (String) -> Unit) {
        runtimeDir.mkdirs()
        progress("Checking runtime manifest…")
        val manifest = JSONObject(readUrlText(manifestUrl))
        require(manifest.optString("architecture") == "armhf") {
            "Runtime architecture is not ARMHF."
        }
        require(manifest.optString("windows_architecture") == "x86") {
            "Runtime does not target 32-bit Windows applications."
        }

        val archiveUrl = manifest.getString("download_url")
        val archiveName = manifest.optString("archive", "SAJI-PC-ARMHF-Wine32-RootFS.tar.gz")
        val archiveSize = manifest.getLong("size_bytes")
        val uncompressedSize = manifest.optLong("uncompressed_bytes", archiveSize * 5L)
        val requiredSpace = uncompressedSize + archiveSize + 160L * 1024L * 1024L
        val availableSpace = StatFs(appContext.filesDir.absolutePath).availableBytes
        if (availableSpace < requiredSpace) {
            throw IllegalStateException(
                "Storage kam hai. Runtime ko install karne ke liye lagbhag " +
                    formatBytes(requiredSpace) + " free space chahiye."
            )
        }

        val rootArchive = File(runtimeDir, archiveName)
        val prootArchive = File(runtimeDir, "SAJI-PC-PRoot-Android-ARM32.tar.gz")
        val checksumFile = File(runtimeDir, "proot-checksums.txt")

        progress("Downloading Debian ARMHF + Wine32 runtime…")
        downloadToFile(archiveUrl, rootArchive, archiveSize, "Linux + Wine32", progress)
        val expectedRootSha = manifest.getString("sha256")
        verifySha256(rootArchive, expectedRootSha)

        val prootUrl = manifest.getString("proot_download_url")
        val prootChecksumUrl = manifest.getString("proot_checksum_url")
        progress("Downloading Android ARM32 PRoot…")
        downloadToFile(prootChecksumUrl, checksumFile, -1L, "PRoot checksum", progress)
        val expectedProotSha = checksumFile.readText().trim().split(Regex("\\s+")).firstOrNull()
            ?: throw IllegalStateException("PRoot checksum file is empty.")
        downloadToFile(prootUrl, prootArchive, -1L, "Android PRoot", progress)
        verifySha256(prootArchive, expectedProotSha)

        val stagingRoot = File(runtimeDir, "rootfs.new")
        val stagingBin = File(runtimeDir, "bin.new")
        deleteRecursively(stagingRoot)
        deleteRecursively(stagingBin)
        stagingRoot.mkdirs()
        stagingBin.mkdirs()

        progress("Extracting Linux runtime…")
        extractTarGz(rootArchive, stagingRoot)
        progress("Extracting PRoot launcher…")
        extractTarGz(prootArchive, stagingBin)

        val extractedProot = File(stagingBin, "libproot.so")
        val extractedLoader = File(stagingBin, "libproot-loader.so")
        require(extractedProot.isFile && extractedLoader.isFile) {
            "PRoot archive mein required ARM32 executable nahi mila."
        }
        require(File(stagingRoot, "usr/local/bin/box86").isFile) {
            "Box86 runtime file missing hai."
        }
        require(File(stagingRoot, "usr/local/bin/saji-desktop").isFile) {
            "Desktop launcher missing hai."
        }
        require(File(stagingRoot, "usr/lib/wine/wine").isFile) {
            "32-bit Wine loader missing hai."
        }
        require(File(stagingRoot, "usr/share/novnc/vnc.html").isFile) {
            "noVNC desktop files missing hain."
        }

        val finalRoot = rootfsDir
        val finalBin = File(runtimeDir, "bin")
        deleteRecursively(finalRoot)
        deleteRecursively(finalBin)
        if (!stagingRoot.renameTo(finalRoot)) {
            throw IllegalStateException("Linux runtime ko final folder mein move nahi kar saka.")
        }
        if (!stagingBin.renameTo(finalBin)) {
            deleteRecursively(finalRoot)
            throw IllegalStateException("PRoot executable ko final folder mein move nahi kar saka.")
        }

        prootBinary.setExecutable(true, false)
        File(finalBin, "libproot-loader.so").setExecutable(true, false)
        File(finalRoot, "usr/local/bin/box86").setExecutable(true, false)
        File(finalRoot, "usr/local/bin/saji-desktop").setExecutable(true, false)
        File(finalRoot, "usr/bin/Xvfb").setExecutable(true, false)
        File(finalRoot, "usr/bin/x11vnc").setExecutable(true, false)
        File(finalRoot, "usr/bin/websockify").setExecutable(true, false)

        val marker = JSONObject()
            .put("architecture", manifest.getString("architecture"))
            .put("windows_architecture", manifest.getString("windows_architecture"))
            .put("version", manifest.optString("version", "unknown"))
            .put("installed_at", System.currentTimeMillis())
        readyMarker.writeText(marker.toString())
        rootArchive.delete()
        prootArchive.delete()
        checksumFile.delete()

        if (!isReady()) {
            throw IllegalStateException("Runtime files install ho gaye, lekin final verification fail hui.")
        }
        progress("Runtime installed and verified.")
    }

    private fun readUrlText(url: String): String {
        val connection = openConnection(url)
        try {
            connection.inputStream.use { input ->
                return input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadToFile(
        url: String,
        target: File,
        expectedSize: Long,
        label: String,
        progress: (String) -> Unit
    ) {
        val temporary = File(target.parentFile, target.name + ".part")
        temporary.delete()
        val connection = openConnection(url)
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("$label download failed: HTTP $status")
            }
            val total = if (expectedSize > 0) expectedSize else connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                BufferedOutputStream(FileOutputStream(temporary)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReported = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total > 0) {
                            val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                progress("$label download: $percent%")
                            }
                        } else if (downloaded % (8L * 1024L * 1024L) < buffer.size) {
                            progress("$label downloaded: ${formatBytes(downloaded)}")
                        }
                    }
                    output.flush()
                }
            }
            if (expectedSize > 0 && downloaded != expectedSize) {
                throw IllegalStateException("$label file size mismatch.")
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                throw IllegalStateException("$label download ko save nahi kar saka.")
            }
        } catch (e: Exception) {
            temporary.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SAJI-PC-Android/0.3")
        }
    }

    private fun verifySha256(file: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actual.equals(expected.trim(), ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("Runtime download SHA-256 verification fail hui.")
        }
    }

    private fun extractTarGz(archive: File, destination: File) {
        destination.mkdirs()
        val rootPath = destination.canonicalFile.toPath()
        TarArchiveInputStream(
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))
        ).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val rawName = entry.name.removePrefix("./").trimEnd('/')
                if (rawName.isBlank() || rawName == ".") continue
                val relative = Paths.get(rawName)
                if (relative.isAbsolute || relative.any { it.toString() == ".." }) {
                    throw IllegalStateException("Unsafe runtime archive path.")
                }
                val target = rootPath.resolve(relative).normalize()
                if (!target.startsWith(rootPath)) {
                    throw IllegalStateException("Runtime archive path escapes install folder.")
                }
                val output = target.toFile()

                when {
                    entry.isDirectory -> {
                        output.mkdirs()
                        output.setExecutable(true, false)
                    }
                    entry.isSymbolicLink -> {
                        output.parentFile?.mkdirs()
                        val linkName = entry.linkName
                        val resolvedLink = if (linkName.startsWith("/")) {
                            val guestTarget = rootPath.resolve(linkName.removePrefix("/")).normalize()
                            if (!guestTarget.startsWith(rootPath)) {
                                throw IllegalStateException("Unsafe absolute symbolic link in runtime.")
                            }
                            output.parentFile.toPath().normalize().relativize(guestTarget).toString()
                        } else {
                            val guestTarget = output.parentFile.toPath().resolve(linkName).normalize()
                            if (!guestTarget.startsWith(rootPath)) {
                                throw IllegalStateException("Unsafe symbolic link in runtime.")
                            }
                            linkName
                        }
                        if (output.exists() || Files.isSymbolicLink(output.toPath())) output.delete()
                        Os.symlink(resolvedLink, output.absolutePath)
                    }
                    entry.isLink -> {
                        output.parentFile?.mkdirs()
                        val linkPath = Paths.get(entry.linkName)
                        val source = if (linkPath.isAbsolute) {
                            rootPath.resolve(linkPath.toString().removePrefix("/")).normalize()
                        } else {
                            rootPath.resolve(linkPath).normalize()
                        }
                        if (!source.startsWith(rootPath) || !Files.exists(source)) {
                            throw IllegalStateException("Runtime hard-link target missing.")
                        }
                        if (output.exists()) output.delete()
                        Files.createLink(output.toPath(), source)
                    }
                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(output)).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = tar.read(buffer)
                                if (count < 0) break
                                out.write(buffer, 0, count)
                            }
                        }
                        output.setExecutable((entry.mode and 73) != 0, false)
                    }
                    else -> {
                        // Device nodes and sockets are deliberately not created in app storage.
                    }
                }
            }
        }
    }

    private fun deleteRecursively(file: File) {
        if (!file.exists() && !Files.isSymbolicLink(file.toPath())) return
        if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    private fun formatBytes(bytes: Long): String {
        val mb = bytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
    }
}
