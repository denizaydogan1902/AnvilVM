package com.anvilvm.app.engine.avf

import android.os.Build
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AvfCapabilities(
    val kvmAvailable: Boolean,
    val pkvmEnabled: Boolean,
    val androidVersion: Int,
    val kernelVersion: String,
    val virtializationExtension: VirtExtension,
    val maxVcpus: Int,
    val supportedFeatures: Set<AvfFeature>
)

enum class VirtExtension {
    NONE, ARM_VHE, ARM_NVHE, PKVM
}

enum class AvfFeature {
    PROTECTED_VM,
    UNPROTECTED_VM,
    VSOCK,
    SHARED_MEMORY,
    VIRTIO_BLK,
    VIRTIO_NET,
    VIRTIO_CONSOLE,
    ASSIGNABLE_DEVICES
}

@Singleton
class AvfDetector @Inject constructor() {

    fun detect(): AvfCapabilities {
        val kvmAvailable = checkKvmDevice()
        val pkvmEnabled = checkPkvm()
        val kernelVersion = readKernelVersion()
        val extension = detectVirtExtension()
        val features = detectFeatures(kvmAvailable, pkvmEnabled)
        val maxVcpus = readMaxVcpus()

        return AvfCapabilities(
            kvmAvailable = kvmAvailable,
            pkvmEnabled = pkvmEnabled,
            androidVersion = Build.VERSION.SDK_INT,
            kernelVersion = kernelVersion,
            virtializationExtension = extension,
            maxVcpus = maxVcpus,
            supportedFeatures = features
        )
    }

    fun isAccelerationPossible(): Boolean {
        return checkKvmDevice() && Build.VERSION.SDK_INT >= 33
    }

    fun getAccelerationType(): AccelerationType {
        if (!checkKvmDevice()) return AccelerationType.TCG_ONLY
        if (checkPkvm()) return AccelerationType.PKVM
        return AccelerationType.KVM
    }

    private fun checkKvmDevice(): Boolean {
        return File("/dev/kvm").exists()
    }

    private fun checkPkvm(): Boolean {
        // pKVM is indicated by specific kernel config or sysfs entries
        val hypervisorType = try {
            File("/sys/hypervisor/type").readText().trim()
        } catch (_: Exception) { "" }

        if (hypervisorType == "pkvm") return true

        // Alternative: check kernel command line
        val cmdline = try {
            File("/proc/cmdline").readText()
        } catch (_: Exception) { "" }

        return cmdline.contains("kvm-arm.mode=protected")
    }

    private fun readKernelVersion(): String {
        return try {
            File("/proc/version").readText().trim()
                .split(" ").getOrNull(2) ?: "unknown"
        } catch (_: Exception) { "unknown" }
    }

    private fun detectVirtExtension(): VirtExtension {
        if (checkPkvm()) return VirtExtension.PKVM

        val cpuInfo = try {
            File("/proc/cpuinfo").readText()
        } catch (_: Exception) { "" }

        return when {
            cpuInfo.contains("vhe") -> VirtExtension.ARM_VHE
            checkKvmDevice() -> VirtExtension.ARM_NVHE
            else -> VirtExtension.NONE
        }
    }

    private fun detectFeatures(kvmAvailable: Boolean, pkvmEnabled: Boolean): Set<AvfFeature> {
        val features = mutableSetOf<AvfFeature>()

        if (!kvmAvailable) return features

        if (pkvmEnabled) {
            features.add(AvfFeature.PROTECTED_VM)
        }
        features.add(AvfFeature.UNPROTECTED_VM)

        // Check for vsock support
        if (File("/dev/vsock").exists()) {
            features.add(AvfFeature.VSOCK)
        }

        // Virtio devices are generally available with KVM
        features.add(AvfFeature.VIRTIO_BLK)
        features.add(AvfFeature.VIRTIO_NET)
        features.add(AvfFeature.VIRTIO_CONSOLE)

        // Check for VFIO (device assignment)
        if (File("/dev/vfio").exists()) {
            features.add(AvfFeature.ASSIGNABLE_DEVICES)
        }

        return features
    }

    private fun readMaxVcpus(): Int {
        return try {
            File("/dev/kvm").let {
                if (it.exists()) {
                    // Default assumption based on hardware
                    Runtime.getRuntime().availableProcessors()
                } else 0
            }
        } catch (_: Exception) { 0 }
    }
}

enum class AccelerationType(val displayName: String, val qemuAccel: String) {
    TCG_ONLY("Software (TCG)", "tcg"),
    KVM("Hardware (KVM)", "kvm"),
    PKVM("Protected KVM (pKVM)", "kvm")
}
