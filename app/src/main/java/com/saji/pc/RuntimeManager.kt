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
import java.util.zip.ZipInputStream

class RuntimeManager(context: Context) {
    private val appContext = context.applicationContext
    private val runtimeDir = File(appContext.filesDir, "saji-runtime")
    val rootfsDir = File(runtimeDir, "rootfs")
    val prootBinary = File(runtimeDir, "bin/libproot.so")
    private val readyMarker = File(runtimeDir, "runtime-ready.json")

    private val artifactsUrl =
        "https://api.github.com/repos/sagidsaim001-jpg/SAJI-PC/actions/artifacts?name=SAJI-PC-ARMHF-Wine32-RootFS&per_page=100"

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
        progress("Checking latest ARMHF runtime build...")
        val artifact = findLatestRuntimeArtifact()
        val artifactSize = artifact.getLong("size_in_bytes")
        val artifactDigest = artifact.optString("digest").removePrefix("sha256:")
        require(artifactSize > 0L && artifactDigest.matches(Regex("[0-9a-fA-F]{64}"))) {
            "Runtime artifact size ya checksum valid nahi."
        }

        val minimumSpace = artifactSize + 1024L * 1024L * 1024L
        val availableBeforeDownload = StatFs(appContext.filesDir.absolutePath).availableBytes
        if (availableBeforeDownload < minimumSpace) {
            throw IllegalStateException(
                "Runtime download ke liye kam az kam " + formatBytes(minimumSpace) +
                    " free storage chahiye."
            )
        }

        val artifactZip = File(runtimeDir, "SAJI-PC-runtime-artifact.zip")
        val bundleDir = File(runtimeDir, "artifact-bundle")
        deleteRecursively(bundleDir)
        progress("Downloading latest ARMHF + Wine32 build...")
        downloadToFile(
            artifact.getString("archive_download_url"),
            artifactZip,
            artifactSize,
            "ARMHF + Wine32 bundle",
            progress
        )
        installFromArtifactZip(artifactZip, artifact, progress)
    }

    @Throws(Exception::class)
    fun installFromStream(input: InputStream, progress: (String) -> Unit) {
        runtimeDir.mkdirs()
        val artifactZip = File(runtimeDir, "SAJI-PC-runtime-artifact.zip")
        progress("Copying downloaded runtime ZIP...")
        artifactZip.delete()
        input.use { source ->
            BufferedOutputStream(FileOutputStream(artifactZip)).use { output ->
                val buffer = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    copied += count
                    if (copied % (32L * 1024L * 1024L) < buffer.size) {
                        progress("Runtime ZIP copied: " + formatBytes(copied))
                    }
                }
                output.flush()
            }
        }
        require(artifactZip.isFile && artifactZip.length() > 1024L) {
            "Selected runtime ZIP empty ya invalid hai."
        }
        installFromArtifactZip(artifactZip, null, progress)
    }

    private fun installFromArtifactZip(
        artifactZip: File,
        artifact: JSONObject?,
        progress: (String) -> Unit
    ) {
        if (artifact != null) {
            verifySha256(artifactZip, artifact.optString("digest").removePrefix("sha256:"))
        }
        progress("Checking runtime bundle contents...")
        extractArtifactZip(artifactZip, bundleDir)
        val manifestFile = File(bundleDir, "runtime-manifest.json")
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.optString("architecture") == "armhf") {
            "Runtime architecture is not ARMHF."
        }
        require(manifest.optString("windows_architecture") == "x86") {
            "Runtime does not target 32-bit Windows applications."
        }

        val archiveName = manifest.optString(
            "archive",
            "SAJI-PC-ARMHF-Wine32-RootFS.tar.gz"
        )
        require(archiveName == "SAJI-PC-ARMHF-Wine32-RootFS.tar.gz") {
            "Runtime archive name is not recognized."
        }
        val bundledRootArchive = File(bundleDir, archiveName)
        require(bundledRootArchive.isFile) {
            "Runtime bundle ke andar Linux rootfs archive missing hai."
        }
        val rootArchive = File(runtimeDir, archiveName)
        if (rootArchive.exists()) rootArchive.delete()
        if (!bundledRootArchive.renameTo(rootArchive)) {
            throw IllegalStateException("Linux runtime archive ko prepare nahi kar saka.")
        }
        deleteRecursively(bundleDir)
        artifactZip.delete()

        val archiveSize = manifest.getLong("size_bytes")
        require(rootArchive.length() == archiveSize) {
            "Linux runtime archive size mismatch."
        }
        val uncompressedSize = manifest.optLong("uncompressed_bytes", archiveSize * 5L)
        val requiredSpace = uncompressedSize + archiveSize + 220L * 1024L * 1024L
        val availableSpace = StatFs(appContext.filesDir.absolutePath).availableBytes
        if (availableSpace < requiredSpace) {
            throw IllegalStateException(
                "Runtime extraction ke liye lagbhag " + formatBytes(requiredSpace) +
                    " free storage chahiye."
            )
        }

        progress("Verifying Debian ARMHF + Wine32 archive...")
        val expectedRootSha = manifest.getString("sha256")
        verifySha256(rootArchive, expectedRootSha)

        val prootArchive = File(runtimeDir, "SAJI-PC-PRoot-Android-ARM32.tar.gz")
        val checksumFile = File(runtimeDir, "proot-checksums.txt")

        val prootUrl = manifest.getString("proot_download_url")
        val prootChecksumUrl = manifest.getString("proot_checksum_url")
        progress("Downloading Android ARM32 PRoot...")
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

        progress("Extracting Linux + Wine32 runtime...")
        extractTarGz(rootArchive, stagingRoot)
        progress("Extracting Android PRoot launcher...")
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
            .put("artifact_created_at", artifact?.optString("created_at") ?: "manual-import")
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

    private fun findLatestRuntimeArtifact(): JSONObject {
        val response = JSONObject(readUrlText(artifactsUrl))
        val artifacts = response.optJSONArray("artifacts")
            ?: throw IllegalStateException("GitHub runtime artifact list nahi mila.")
        var newest: JSONObject? = null
        for (index in 0 until artifacts.length()) {
            val item = artifacts.optJSONObject(index) ?: continue
            if (item.optString("name") != "SAJI-PC-ARMHF-Wine32-RootFS") continue
            if (item.optBoolean("expired", true)) continue
            val previous = newest
            if (previous == null ||
                item.optString("created_at") > previous.optString("created_at")) {
                newest = item
            }
        }
        return newest ?: throw IllegalStateException(
            "Abhi koi non-expired ARMHF + Wine32 runtime artifact available nahi hai."
        )
    }

    private fun extractArtifactZip(zipFile: File, destination: File) {
        destination.mkdirs()
        val root = destination.canonicalFile
        val allowed = setOf(
            "runtime-manifest.json",
            "SAJI-PC-ARMHF-Wine32-RootFS.tar.gz",
            "SHA256SUMS"
        )
        val found = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name
                require(name in allowed) {
                    "Runtime artifact mein unknown file mili: $name"
                }
                require(found.add(name)) {
                    "Runtime artifact mein duplicate file hai: $name"
                }
                val output = File(root, name).canonicalFile
                require(output.parentFile == root) {
                    "Runtime artifact path unsafe hai."
                }
                BufferedOutputStream(FileOutputStream(output)).use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        written += count
                        require(written <= 4L * 1024L * 1024L * 1024L) {
                            "Runtime artifact entry unexpected size ki hai."
                        }
                        stream.write(buffer, 0, count)
                    }
                }
                zip.closeEntry()
            }
        }
        require(found.contains("runtime-manifest.json") &&
            found.contains("SAJI-PC-ARMHF-Wine32-RootFS.tar.gz") &&
            found.contains("SHA256SUMS")) {
            "Runtime artifact incomplete hai."
        }

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
            setRequestProperty("Accept", "application/vnd.github+json")
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
