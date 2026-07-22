package com.anvilvm.app.engine.profiler

import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

data class PerformanceMetrics(
    val cpuUsagePercent: Float = 0f,
    val memoryUsedMb: Long = 0L,
    val memoryTotalMb: Long = 0L,
    val tcgBlocksCompiled: Long = 0L,
    val tcgBlocksExecuted: Long = 0L,
    val tcgCacheHitRate: Float = 0f,
    val vncFps: Float = 0f,
    val diskReadKbps: Float = 0f,
    val diskWriteKbps: Float = 0f,
    val netRxKbps: Float = 0f,
    val netTxKbps: Float = 0f,
    val uptimeMs: Long = 0L,
    val mipsEstimate: Float = 0f
)

data class TcgStats(
    val translatedBlocks: Long = 0L,
    val executedBlocks: Long = 0L,
    val codeGenSize: Long = 0L,
    val cacheSize: Long = 0L,
    val cacheFlushes: Long = 0L,
    val averageBlockSize: Float = 0f
)

@Singleton
class PerformanceProfiler @Inject constructor() {

    private var profilingJob: Job? = null
    private var startTime = 0L
    private var lastCpuTime = 0L
    private var lastCpuTotal = 0L
    private var qemuPid: Int = -1

    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics

    private val _tcgStats = MutableStateFlow(TcgStats())
    val tcgStats: StateFlow<TcgStats> = _tcgStats

    private val _history = MutableStateFlow<List<PerformanceMetrics>>(emptyList())
    val history: StateFlow<List<PerformanceMetrics>> = _history

    private val metricsHistory = mutableListOf<PerformanceMetrics>()
    private val maxHistorySize = 300 // 5 minutes at 1 sample/second

    fun startProfiling(pid: Int) {
        qemuPid = pid
        startTime = SystemClock.elapsedRealtime()
        lastCpuTime = 0L
        lastCpuTotal = 0L
        metricsHistory.clear()

        profilingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val metrics = sampleMetrics()
                _metrics.value = metrics

                metricsHistory.add(metrics)
                if (metricsHistory.size > maxHistorySize) {
                    metricsHistory.removeAt(0)
                }
                _history.value = metricsHistory.toList()

                delay(1000)
            }
        }
    }

    fun stopProfiling() {
        profilingJob?.cancel()
        profilingJob = null
        qemuPid = -1
    }

    fun getReport(): ProfilingReport {
        val history = metricsHistory.toList()
        return ProfilingReport(
            durationMs = SystemClock.elapsedRealtime() - startTime,
            avgCpuPercent = history.map { it.cpuUsagePercent }.average().toFloat(),
            peakCpuPercent = history.maxOfOrNull { it.cpuUsagePercent } ?: 0f,
            avgMemoryMb = history.map { it.memoryUsedMb }.average().toLong(),
            peakMemoryMb = history.maxOfOrNull { it.memoryUsedMb } ?: 0L,
            avgMips = history.map { it.mipsEstimate }.average().toFloat(),
            totalTcgBlocks = _tcgStats.value.translatedBlocks,
            tcgCacheHitRate = _tcgStats.value.let {
                if (it.executedBlocks > 0) {
                    (it.executedBlocks - it.cacheFlushes).toFloat() / it.executedBlocks
                } else 0f
            },
            sampleCount = history.size
        )
    }

    private fun sampleMetrics(): PerformanceMetrics {
        val cpuUsage = readProcessCpuUsage()
        val memInfo = readProcessMemory()
        val uptime = SystemClock.elapsedRealtime() - startTime

        return PerformanceMetrics(
            cpuUsagePercent = cpuUsage,
            memoryUsedMb = memInfo.first,
            memoryTotalMb = memInfo.second,
            uptimeMs = uptime,
            mipsEstimate = estimateMips(cpuUsage)
        )
    }

    private fun readProcessCpuUsage(): Float {
        if (qemuPid <= 0) return 0f

        try {
            val statFile = File("/proc/$qemuPid/stat")
            if (!statFile.exists()) return 0f

            val stat = statFile.readText().split(" ")
            if (stat.size < 17) return 0f

            val utime = stat[13].toLongOrNull() ?: 0L
            val stime = stat[14].toLongOrNull() ?: 0L
            val processTime = utime + stime

            // Read total CPU time
            val cpuStat = File("/proc/stat").readLines().firstOrNull() ?: return 0f
            val cpuParts = cpuStat.split("\\s+".toRegex())
            val totalTime = cpuParts.drop(1).take(7).sumOf { it.toLongOrNull() ?: 0L }

            if (lastCpuTime > 0 && lastCpuTotal > 0) {
                val processDelta = processTime - lastCpuTime
                val totalDelta = totalTime - lastCpuTotal
                if (totalDelta > 0) {
                    lastCpuTime = processTime
                    lastCpuTotal = totalTime
                    return (processDelta.toFloat() / totalDelta * 100f).coerceIn(0f, 100f)
                }
            }

            lastCpuTime = processTime
            lastCpuTotal = totalTime
        } catch (_: Exception) {}

        return 0f
    }

    private fun readProcessMemory(): Pair<Long, Long> {
        if (qemuPid <= 0) return 0L to 0L

        try {
            val statusFile = File("/proc/$qemuPid/status")
            if (!statusFile.exists()) return 0L to 0L

            var vmRss = 0L
            var vmSize = 0L
            statusFile.readLines().forEach { line ->
                when {
                    line.startsWith("VmRSS:") -> {
                        vmRss = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                    }
                    line.startsWith("VmSize:") -> {
                        vmSize = line.split("\\s+".toRegex())[1].toLongOrNull() ?: 0L
                    }
                }
            }

            return (vmRss / 1024) to (vmSize / 1024)
        } catch (_: Exception) {}

        return 0L to 0L
    }

    private fun estimateMips(cpuPercent: Float): Float {
        // Rough estimate based on CPU usage and typical ARM64 MIPS rating
        // Modern Snapdragon 8 Gen 2: ~15,000 DMIPS per core
        // QEMU TCG overhead factor: ~10-50x slowdown
        // At 100% single core: approx 300-1500 effective MIPS for guest
        val coreSpeedMips = 1500f
        val tcgOverhead = 20f
        return (cpuPercent / 100f) * (coreSpeedMips / tcgOverhead)
    }
}

data class ProfilingReport(
    val durationMs: Long,
    val avgCpuPercent: Float,
    val peakCpuPercent: Float,
    val avgMemoryMb: Long,
    val peakMemoryMb: Long,
    val avgMips: Float,
    val totalTcgBlocks: Long,
    val tcgCacheHitRate: Float,
    val sampleCount: Int
) {
    override fun toString(): String = buildString {
        appendLine("=== AnvilVM Performance Report ===")
        appendLine("Duration: ${durationMs / 1000}s")
        appendLine("CPU: avg=${avgCpuPercent.format(1)}%, peak=${peakCpuPercent.format(1)}%")
        appendLine("Memory: avg=${avgMemoryMb}MB, peak=${peakMemoryMb}MB")
        appendLine("MIPS (est): avg=${avgMips.format(1)}")
        appendLine("TCG Blocks: $totalTcgBlocks (cache hit: ${(tcgCacheHitRate * 100).format(1)}%)")
        appendLine("Samples: $sampleCount")
    }

    private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)
}
