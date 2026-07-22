package com.anvilvm.app.engine.profiler

import com.anvilvm.app.engine.VMConfig
import javax.inject.Inject
import javax.inject.Singleton

data class TcgOptimizationProfile(
    val tbSize: Int,
    val tbCacheSize: Int,
    val threadCount: Int,
    val mttcg: Boolean,
    val memPrealloc: Boolean,
    val hugepages: Boolean
)

@Singleton
class TcgOptimizer @Inject constructor() {

    fun getOptimalProfile(config: VMConfig, deviceInfo: DeviceInfo): TcgOptimizationProfile {
        val availableRam = deviceInfo.availableRamMb
        val cpuCores = deviceInfo.cpuCores
        val isHighEnd = deviceInfo.isHighEnd

        // TCG Translation Block cache size
        // Larger cache = fewer retranslations but more memory
        val tbCacheSize = when {
            availableRam > 8192 -> 512  // 512MB TB cache for 8GB+ devices
            availableRam > 4096 -> 256  // 256MB for 4-8GB
            availableRam > 2048 -> 128  // 128MB for 2-4GB
            else -> 64                   // 64MB minimum
        }

        // Individual TB size limit (instructions per block)
        val tbSize = when {
            isHighEnd -> 512   // Larger blocks for better locality on fast cores
            else -> 256        // Smaller blocks for lower-end devices
        }

        // MTTCG (Multi-Threaded TCG)
        // Beneficial when guest has multiple vCPUs and host has spare cores
        val guestCores = config.cpuCores
        val mttcg = guestCores > 1 && cpuCores >= guestCores + 2

        // Thread count: leave at least 2 cores for Android system
        val threadCount = when {
            mttcg -> minOf(guestCores, cpuCores - 2).coerceAtLeast(1)
            else -> 1
        }

        // Memory preallocation (reduces page fault overhead)
        val memPrealloc = availableRam > config.memoryMb * 2

        return TcgOptimizationProfile(
            tbSize = tbSize,
            tbCacheSize = tbCacheSize,
            threadCount = threadCount,
            mttcg = mttcg,
            memPrealloc = memPrealloc,
            hugepages = false // Not available on Android
        )
    }

    fun buildQemuArgs(profile: TcgOptimizationProfile): List<String> {
        return buildList {
            add("-accel")
            val accelArg = buildString {
                append("tcg")
                append(",tb-size=${profile.tbCacheSize}")
                if (profile.mttcg) append(",thread=multi")
            }
            add(accelArg)

            if (profile.memPrealloc) {
                add("-mem-prealloc")
            }
        }
    }

    fun analyzeTcgPerformance(metrics: PerformanceMetrics, tcgStats: TcgStats): List<String> {
        val suggestions = mutableListOf<String>()

        if (tcgStats.cacheFlushes > tcgStats.translatedBlocks * 0.1) {
            suggestions.add("High TCG cache flush rate. Consider increasing tb-size parameter.")
        }

        if (metrics.cpuUsagePercent > 90f && metrics.mipsEstimate < 50f) {
            suggestions.add("CPU saturated with low MIPS. Guest may be running tight loops. Consider enabling MTTCG.")
        }

        if (metrics.memoryUsedMb > metrics.memoryTotalMb * 0.8) {
            suggestions.add("Memory pressure detected. Reduce guest RAM or close background apps.")
        }

        if (tcgStats.averageBlockSize < 10f) {
            suggestions.add("Very small translation blocks. Guest code has many branches. Normal for boot/init.")
        }

        return suggestions
    }
}

data class DeviceInfo(
    val cpuCores: Int,
    val availableRamMb: Long,
    val cpuModel: String,
    val isHighEnd: Boolean,
    val supportsSve: Boolean = false,
    val maxFreqMhz: Long = 0L
) {
    companion object {
        fun detect(): DeviceInfo {
            val cores = Runtime.getRuntime().availableProcessors()
            val maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024)

            val cpuInfo = try {
                java.io.File("/proc/cpuinfo").readLines()
                    .firstOrNull { it.startsWith("Hardware") }
                    ?.substringAfter(":")?.trim() ?: "Unknown"
            } catch (_: Exception) { "Unknown" }

            val maxFreq = try {
                java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toLongOrNull()?.div(1000) ?: 0L
            } catch (_: Exception) { 0L }

            val isHighEnd = cores >= 8 && maxFreq > 2500

            return DeviceInfo(
                cpuCores = cores,
                availableRamMb = maxMemory,
                cpuModel = cpuInfo,
                isHighEnd = isHighEnd,
                maxFreqMhz = maxFreq
            )
        }
    }
}
