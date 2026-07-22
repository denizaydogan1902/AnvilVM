package com.anvilvm.app.data.imagestore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class OSImageInfo(
    val id: String,
    val name: String,
    val version: String,
    val arch: String,
    val format: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val description: String,
    val category: ImageCategory
)

enum class ImageCategory {
    LINUX_MINIMAL,
    LINUX_DESKTOP,
    LINUX_SERVER,
    CUSTOM_OS,
    EDUCATIONAL
}

data class DownloadProgress(
    val imageId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState
) {
    val progressPercent: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}

enum class DownloadState {
    QUEUED, DOWNLOADING, VERIFYING, COMPLETED, FAILED, CANCELLED
}

@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val imagesDir: File
        get() = File(context.filesDir, "images").also { it.mkdirs() }

    private val _availableImages = MutableStateFlow<List<OSImageInfo>>(emptyList())
    val availableImages: StateFlow<List<OSImageInfo>> = _availableImages

    private val _downloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadProgress>> = _downloads

    private val _installedImages = MutableStateFlow<List<InstalledImage>>(emptyList())
    val installedImages: StateFlow<List<InstalledImage>> = _installedImages

    init {
        loadCatalog()
        scanInstalled()
    }

    private fun loadCatalog() {
        _availableImages.value = listOf(
            OSImageInfo(
                id = "alpine-3.20-x86_64",
                name = "Alpine Linux",
                version = "3.20",
                arch = "x86_64",
                format = "iso",
                downloadUrl = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/x86_64/alpine-virt-3.20.0-x86_64.iso",
                sizeBytes = 63_000_000L,
                sha256 = "",
                description = "Ultra-lightweight Linux (63MB). Perfect for containers, servers, and fast boot testing.",
                category = ImageCategory.LINUX_MINIMAL
            ),
            OSImageInfo(
                id = "debian-12-x86_64",
                name = "Debian",
                version = "12 (Bookworm)",
                arch = "x86_64",
                format = "qcow2",
                downloadUrl = "https://cloud.debian.org/images/cloud/bookworm/latest/debian-12-nocloud-amd64.qcow2",
                sizeBytes = 370_000_000L,
                sha256 = "",
                description = "Stable general-purpose Linux. Cloud image with no-cloud init for immediate use.",
                category = ImageCategory.LINUX_SERVER
            ),
            OSImageInfo(
                id = "void-x86_64",
                name = "Void Linux",
                version = "Rolling",
                arch = "x86_64",
                format = "iso",
                downloadUrl = "https://repo-default.voidlinux.org/live/current/void-live-x86_64-20230628-base.iso",
                sizeBytes = 600_000_000L,
                sha256 = "",
                description = "Independent Linux with runit init system. Rolling release, minimalist philosophy.",
                category = ImageCategory.LINUX_MINIMAL
            ),
            OSImageInfo(
                id = "archlinux-x86_64",
                name = "Arch Linux",
                version = "Rolling",
                arch = "x86_64",
                format = "iso",
                downloadUrl = "https://geo.mirror.pkgbuild.com/iso/latest/archlinux-x86_64.iso",
                sizeBytes = 850_000_000L,
                sha256 = "",
                description = "Rolling-release, DIY Linux. Build your system from scratch.",
                category = ImageCategory.LINUX_MINIMAL
            ),
            OSImageInfo(
                id = "aegisos-x86_64",
                name = "AegisOS",
                version = "0.1.0-alpha",
                arch = "x86_64",
                format = "iso",
                downloadUrl = "",
                sizeBytes = 2_000_000_000L,
                sha256 = "",
                description = "Cyber warfare OS with Qubes-style hardware isolation. Academic/research project.",
                category = ImageCategory.CUSTOM_OS
            ),
            OSImageInfo(
                id = "riscv-fedora",
                name = "Fedora RISC-V",
                version = "Rawhide",
                arch = "riscv64",
                format = "qcow2",
                downloadUrl = "https://dl.fedoraproject.org/pub/alt/risc-v/disk-images/",
                sizeBytes = 1_500_000_000L,
                sha256 = "",
                description = "Fedora for RISC-V architecture. Experimental platform for RISC-V development.",
                category = ImageCategory.EDUCATIONAL
            )
        )
    }

    suspend fun downloadImage(imageInfo: OSImageInfo): Result<File> = withContext(Dispatchers.IO) {
        val destFile = File(imagesDir, "${imageInfo.id}.${imageInfo.format}")

        if (destFile.exists() && destFile.length() == imageInfo.sizeBytes) {
            scanInstalled()
            return@withContext Result.success(destFile)
        }

        updateProgress(imageInfo.id, DownloadProgress(
            imageId = imageInfo.id,
            bytesDownloaded = 0,
            totalBytes = imageInfo.sizeBytes,
            state = DownloadState.DOWNLOADING
        ))

        try {
            val url = URL(imageInfo.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            // Support resume
            if (destFile.exists()) {
                connection.setRequestProperty("Range", "bytes=${destFile.length()}-")
            }

            connection.connect()

            val totalBytes = if (connection.responseCode == 206) {
                destFile.length() + connection.contentLengthLong
            } else {
                connection.contentLengthLong
            }

            val inputStream = connection.inputStream
            val outputStream = if (connection.responseCode == 206) {
                destFile.outputStream().apply { channel.position(destFile.length()) }
            } else {
                destFile.outputStream()
            }

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalRead = if (connection.responseCode == 206) destFile.length() else 0L

            outputStream.use { output ->
                inputStream.use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        // Check cancellation
                        val currentState = _downloads.value[imageInfo.id]?.state
                        if (currentState == DownloadState.CANCELLED) {
                            connection.disconnect()
                            return@withContext Result.failure(Exception("Download cancelled"))
                        }

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        updateProgress(imageInfo.id, DownloadProgress(
                            imageId = imageInfo.id,
                            bytesDownloaded = totalRead,
                            totalBytes = totalBytes,
                            state = DownloadState.DOWNLOADING
                        ))
                    }
                }
            }

            connection.disconnect()

            // Verify checksum if available
            if (imageInfo.sha256.isNotBlank()) {
                updateProgress(imageInfo.id, DownloadProgress(
                    imageId = imageInfo.id,
                    bytesDownloaded = totalRead,
                    totalBytes = totalBytes,
                    state = DownloadState.VERIFYING
                ))

                val fileHash = computeSha256(destFile)
                if (fileHash != imageInfo.sha256) {
                    destFile.delete()
                    updateProgress(imageInfo.id, DownloadProgress(
                        imageId = imageInfo.id,
                        bytesDownloaded = 0,
                        totalBytes = totalBytes,
                        state = DownloadState.FAILED
                    ))
                    return@withContext Result.failure(Exception("SHA256 mismatch"))
                }
            }

            updateProgress(imageInfo.id, DownloadProgress(
                imageId = imageInfo.id,
                bytesDownloaded = totalRead,
                totalBytes = totalBytes,
                state = DownloadState.COMPLETED
            ))

            scanInstalled()
            Result.success(destFile)
        } catch (e: Exception) {
            updateProgress(imageInfo.id, DownloadProgress(
                imageId = imageInfo.id,
                bytesDownloaded = destFile.length(),
                totalBytes = imageInfo.sizeBytes,
                state = DownloadState.FAILED
            ))
            Result.failure(e)
        }
    }

    fun cancelDownload(imageId: String) {
        val current = _downloads.value[imageId] ?: return
        updateProgress(imageId, current.copy(state = DownloadState.CANCELLED))
    }

    suspend fun deleteImage(imageId: String): Boolean = withContext(Dispatchers.IO) {
        val files = imagesDir.listFiles()?.filter { it.nameWithoutExtension == imageId } ?: emptyList()
        files.forEach { it.delete() }
        scanInstalled()
        files.isNotEmpty()
    }

    private fun scanInstalled() {
        val installed = imagesDir.listFiles()?.map { file ->
            val info = _availableImages.value.find { it.id == file.nameWithoutExtension }
            InstalledImage(
                id = file.nameWithoutExtension,
                name = info?.name ?: file.nameWithoutExtension,
                path = file.absolutePath,
                sizeBytes = file.length(),
                format = file.extension,
                arch = info?.arch ?: "unknown"
            )
        } ?: emptyList()

        _installedImages.value = installed
    }

    private fun updateProgress(imageId: String, progress: DownloadProgress) {
        _downloads.value = _downloads.value.toMutableMap().apply {
            put(imageId, progress)
        }
    }

    private fun computeSha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class InstalledImage(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val format: String,
    val arch: String
)
