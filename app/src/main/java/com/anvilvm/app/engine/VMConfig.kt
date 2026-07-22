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
    val arch: Architecture = Architecture.X86_64,
    val extraArgs: List<String> = emptyList()
)

enum class Architecture(val qemuBinary: String) {
    X86_64("libqemu-system-x86_64.so"),
    AARCH64("libqemu-system-aarch64.so"),
    RISCV64("libqemu-system-riscv64.so")
}
