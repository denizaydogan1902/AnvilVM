package com.anvilvm.app.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QemuRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val nativeLibDir: String
        get() = context.applicationInfo.nativeLibraryDir

    val dataDir: String
        get() = context.filesDir.absolutePath

    val imagesDir: File
        get() = File(dataDir, "images").also { it.mkdirs() }

    val firmwareDir: File
        get() = File(dataDir, "firmware").also { it.mkdirs() }

    fun getQemuBinaryPath(arch: Architecture): String {
        return "$nativeLibDir/${arch.qemuBinary}"
    }

    fun isQemuAvailable(arch: Architecture): Boolean {
        return File(getQemuBinaryPath(arch)).exists()
    }

    fun buildFullConfig(config: VMConfig): VMConfig {
        val binaryPath = getQemuBinaryPath(config.arch)
        val firmwarePath = File(firmwareDir, "bios-256k.bin").absolutePath

        val extraArgs = config.extraArgs.toMutableList()

        if (File(firmwarePath).exists()) {
            extraArgs.addAll(listOf("-L", firmwareDir.absolutePath))
        }

        // Use user-mode networking (slirp) - no root required
        extraArgs.addAll(listOf(
            "-netdev", "user,id=net0,hostfwd=tcp::2222-:22",
            "-device", "virtio-net-pci,netdev=net0"
        ))

        // Virtio block device for better I/O
        val driveArgs = "file=${config.diskImagePath},format=${config.diskFormat},if=virtio"

        return config.copy(
            qemuBinaryPath = binaryPath,
            extraArgs = extraArgs
        )
    }
}
