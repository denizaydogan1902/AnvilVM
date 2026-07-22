package com.anvilvm.app.engine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QemuEngine @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("anvilvm-engine")
        }
    }

    external fun nativeStartVM(qemuBinaryPath: String, args: Array<String>): Int
    external fun nativeStopVM(): Boolean
    external fun nativeIsRunning(): Boolean

    fun startVM(config: VMConfig): Int {
        val args = buildArgs(config)
        return nativeStartVM(config.qemuBinaryPath, args.toTypedArray())
    }

    fun stopVM(): Boolean = nativeStopVM()

    fun isRunning(): Boolean = nativeIsRunning()

    private fun buildArgs(config: VMConfig): List<String> {
        return buildList {
            add("-machine"); add("virt")
            add("-cpu"); add(config.cpuModel)
            add("-m"); add("${config.memoryMb}")
            add("-smp"); add("${config.cpuCores}")
            add("-drive"); add("file=${config.diskImagePath},format=${config.diskFormat}")

            if (config.enableVnc) {
                add("-vnc"); add(":${config.vncDisplay}")
            }

            if (config.enableSerial) {
                add("-serial"); add("pty")
            }

            add("-nographic")
            config.extraArgs.forEach { add(it) }
        }
    }
}
