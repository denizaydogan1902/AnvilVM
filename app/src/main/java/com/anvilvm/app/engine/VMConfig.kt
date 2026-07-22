package com.anvilvm.app.engine

data class VMConfig(
    val name: String,
    val qemuBinaryPath: String,
    val diskImagePath: String,
    val diskFormat: String = "qcow2",
    val memoryMb: Int = 1024,
    val cpuCores: Int = 2,
    val cpuModel: String = "max",
    val enableVnc: Boolean = true,
    val vncDisplay: Int = 0,
    val enableSerial: Boolean = true,
    val enableQmp: Boolean = true,
    val qmpPort: Int = 4444,
    val arch: Architecture = Architecture.X86_64,
    val useKvm: Boolean = false,
    val extraArgs: List<String> = emptyList()
)

enum class Architecture(val qemuBinary: String, val displayName: String) {
    X86_64("libqemu-system-x86_64.so", "x86_64 (Intel/AMD)"),
    AARCH64("libqemu-system-aarch64.so", "AArch64 (ARM 64-bit)"),
    RISCV64("libqemu-system-riscv64.so", "RISC-V 64-bit")
}
