package com.anvilvm.app.engine.avf

import com.anvilvm.app.engine.VMConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KvmAccelerator @Inject constructor(
    private val avfDetector: AvfDetector
) {
    fun buildAccelArgs(config: VMConfig): List<String> {
        val accelType = avfDetector.getAccelerationType()

        return when (accelType) {
            AccelerationType.KVM, AccelerationType.PKVM -> buildKvmArgs(config, accelType)
            AccelerationType.TCG_ONLY -> buildTcgArgs(config)
        }
    }

    private fun buildKvmArgs(config: VMConfig, type: AccelerationType): List<String> {
        val capabilities = avfDetector.detect()

        return buildList {
            add("-accel")
            add("kvm")

            // Machine type for KVM on ARM
            add("-machine")
            add("virt,gic-version=3")

            // CPU passthrough (host CPU model)
            add("-cpu")
            add("host")

            // Enable KVM IRQ chip
            add("-machine")
            add("kernel_irqchip=on")

            // vCPU count (bounded by host capability)
            val vcpus = minOf(config.cpuCores, capabilities.maxVcpus)
            add("-smp")
            add("$vcpus")

            // Virtio devices for better KVM performance
            if (AvfFeature.VIRTIO_BLK in capabilities.supportedFeatures) {
                add("-drive")
                add("file=${config.diskImagePath},format=${config.diskFormat},if=none,id=disk0")
                add("-device")
                add("virtio-blk-pci,drive=disk0")
            }

            if (AvfFeature.VIRTIO_NET in capabilities.supportedFeatures) {
                add("-netdev")
                add("user,id=net0")
                add("-device")
                add("virtio-net-pci,netdev=net0")
            }

            // Memory with hugepage hint
            add("-m")
            add("${config.memoryMb}")
        }
    }

    private fun buildTcgArgs(config: VMConfig): List<String> {
        return buildList {
            add("-accel")
            add("tcg,tb-size=256")

            add("-machine")
            add("virt")

            add("-cpu")
            add(config.cpuModel)

            add("-smp")
            add("${config.cpuCores}")

            add("-m")
            add("${config.memoryMb}")
        }
    }

    fun getPerformanceEstimate(config: VMConfig): PerformanceEstimate {
        val accelType = avfDetector.getAccelerationType()

        return when (accelType) {
            AccelerationType.KVM, AccelerationType.PKVM -> PerformanceEstimate(
                overheadFactor = 1.1f,  // ~10% overhead vs native
                estimatedMips = 10000f,
                canPassthroughDevices = true,
                notes = "Hardware acceleration active. Near-native performance for ARM64 guests."
            )
            AccelerationType.TCG_ONLY -> PerformanceEstimate(
                overheadFactor = 20f,  // ~20x slower
                estimatedMips = 500f,
                canPassthroughDevices = false,
                notes = "Software emulation only. Best for lightweight guests (Alpine, CLI tools)."
            )
        }
    }
}

data class PerformanceEstimate(
    val overheadFactor: Float,
    val estimatedMips: Float,
    val canPassthroughDevices: Boolean,
    val notes: String
)
