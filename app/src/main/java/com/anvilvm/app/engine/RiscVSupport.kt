package com.anvilvm.app.engine

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiscVSupport @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val isAvailable: Boolean
        get() {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            return File("$nativeLibDir/${Architecture.RISCV64.qemuBinary}").exists()
        }

    fun buildRiscVConfig(
        name: String,
        diskImagePath: String,
        memoryMb: Int = 1024,
        cpuCores: Int = 2
    ): VMConfig {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val firmwareDir = File(context.filesDir, "firmware")
        val opensbiPath = File(firmwareDir, "opensbi-riscv64-generic-fw_jump.bin").absolutePath
        val kernelPath = File(firmwareDir, "riscv64-virt-kernel").absolutePath

        return VMConfig(
            name = name,
            qemuBinaryPath = "$nativeLibDir/${Architecture.RISCV64.qemuBinary}",
            diskImagePath = diskImagePath,
            memoryMb = memoryMb,
            cpuCores = cpuCores,
            cpuModel = "rv64",
            arch = Architecture.RISCV64,
            enableVnc = true,
            enableSerial = true,
            extraArgs = buildList {
                add("-machine"); add("virt")

                // OpenSBI firmware (RISC-V bootloader)
                if (File(opensbiPath).exists()) {
                    add("-bios"); add(opensbiPath)
                } else {
                    add("-bios"); add("default")
                }

                // Kernel (if available)
                if (File(kernelPath).exists()) {
                    add("-kernel"); add(kernelPath)
                }

                // Virtio disk
                add("-drive")
                add("file=$diskImagePath,format=qcow2,if=none,id=hd0")
                add("-device")
                add("virtio-blk-device,drive=hd0")

                // Virtio network
                add("-netdev")
                add("user,id=net0,hostfwd=tcp::2222-:22")
                add("-device")
                add("virtio-net-device,netdev=net0")

                // Virtio RNG
                add("-device")
                add("virtio-rng-device")

                // Serial console
                add("-serial")
                add("pty")
            }
        )
    }

    fun getSupportedCpuModels(): List<RiscVCpuModel> {
        return listOf(
            RiscVCpuModel("rv64", "Generic RISC-V 64-bit", setOf("i", "m", "a", "f", "d", "c")),
            RiscVCpuModel("sifive-u54", "SiFive U54 (HiFive Unleashed)", setOf("i", "m", "a", "f", "d", "c")),
            RiscVCpuModel("thead-c906", "T-Head C906", setOf("i", "m", "a", "f", "d", "c", "v")),
        )
    }

    fun getSupportedMachines(): List<RiscVMachine> {
        return listOf(
            RiscVMachine("virt", "QEMU RISC-V Virtual Machine", true),
            RiscVMachine("sifive_u", "SiFive HiFive Unleashed", false),
            RiscVMachine("spike", "RISC-V Spike Simulator", false),
        )
    }
}

data class RiscVCpuModel(
    val id: String,
    val name: String,
    val extensions: Set<String>
) {
    val extensionString: String
        get() = extensions.joinToString("")
    val displayName: String
        get() = "$name (RV64${extensionString.uppercase()})"
}

data class RiscVMachine(
    val id: String,
    val name: String,
    val recommended: Boolean
)
