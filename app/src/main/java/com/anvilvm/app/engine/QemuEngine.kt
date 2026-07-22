package com.anvilvm.app.engine

import com.anvilvm.app.engine.avf.AvfDetector
import com.anvilvm.app.engine.avf.KvmAccelerator
import com.anvilvm.app.engine.profiler.PerformanceProfiler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QemuEngine @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("anvilvm-engine")
        }
    }

    @Inject lateinit var avfDetector: AvfDetector
    @Inject lateinit var kvmAccelerator: KvmAccelerator
    @Inject lateinit var profiler: PerformanceProfiler

    external fun nativeStartVM(qemuBinaryPath: String, args: Array<String>): Int
    external fun nativeStopVM(): Boolean
    external fun nativeIsRunning(): Boolean

    fun startVM(config: VMConfig): Int {
        val args = buildArgs(config)
        val pid = nativeStartVM(config.qemuBinaryPath, args.toTypedArray())
        if (pid > 0) {
            profiler.startProfiling(pid)
        }
        return pid
    }

    fun stopVM(): Boolean {
        profiler.stopProfiling()
        return nativeStopVM()
    }

    fun isRunning(): Boolean = nativeIsRunning()

    private fun buildArgs(config: VMConfig): List<String> {
        return buildList {
            // Acceleration
            if (config.useKvm && avfDetector.isAccelerationPossible()) {
                addAll(kvmAccelerator.buildAccelArgs(config))
            } else {
                add("-machine"); add("virt")
                add("-cpu"); add(config.cpuModel)
                add("-m"); add("${config.memoryMb}")
                add("-smp"); add("${config.cpuCores}")
                add("-accel"); add("tcg,tb-size=256")
            }

            // Storage
            add("-drive"); add("file=${config.diskImagePath},format=${config.diskFormat}")

            // Display
            if (config.enableVnc) {
                add("-vnc"); add(":${config.vncDisplay}")
            }

            // Serial console
            if (config.enableSerial) {
                add("-serial"); add("pty")
            }

            // QMP for live snapshot/control
            if (config.enableQmp) {
                add("-qmp")
                add("tcp:127.0.0.1:${config.qmpPort},server,nowait")
            }

            add("-nographic")

            // Extra args (RISC-V firmware, networking, etc.)
            config.extraArgs.forEach { add(it) }
        }
    }
}
