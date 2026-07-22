package com.anvilvm.app.engine.snapshot

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class Snapshot(
    val id: String,
    val name: String,
    val vmName: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val description: String = ""
)

enum class SnapshotOperation {
    IDLE, SAVING, LOADING, DELETING
}

@Singleton
class SnapshotManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val snapshotDir: File
        get() = File(context.filesDir, "snapshots").also { it.mkdirs() }

    private val _operation = MutableStateFlow(SnapshotOperation.IDLE)
    val operation: StateFlow<SnapshotOperation> = _operation

    private val _snapshots = MutableStateFlow<List<Snapshot>>(emptyList())
    val snapshots: StateFlow<List<Snapshot>> = _snapshots

    init {
        refreshSnapshots()
    }

    suspend fun saveSnapshot(
        vmName: String,
        snapshotName: String,
        diskImagePath: String,
        description: String = ""
    ): Result<Snapshot> = withContext(Dispatchers.IO) {
        _operation.value = SnapshotOperation.SAVING
        try {
            val id = "${vmName}_${System.currentTimeMillis()}"
            val vmSnapshotDir = File(snapshotDir, id).also { it.mkdirs() }

            // Use QEMU's internal savevm via QMP (QEMU Machine Protocol)
            // For offline snapshots, create a QCOW2 internal snapshot
            val process = ProcessBuilder(
                getQemuImgPath(),
                "snapshot",
                "-c", snapshotName,
                diskImagePath
            ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                _operation.value = SnapshotOperation.IDLE
                return@withContext Result.failure(Exception("Snapshot failed: $error"))
            }

            // Save metadata
            val metadata = """
                id=$id
                name=$snapshotName
                vm=$vmName
                timestamp=${System.currentTimeMillis()}
                disk=$diskImagePath
                description=$description
            """.trimIndent()
            File(vmSnapshotDir, "metadata.properties").writeText(metadata)

            val diskFile = File(diskImagePath)
            val snapshot = Snapshot(
                id = id,
                name = snapshotName,
                vmName = vmName,
                timestamp = System.currentTimeMillis(),
                sizeBytes = diskFile.length(),
                description = description
            )

            refreshSnapshots()
            _operation.value = SnapshotOperation.IDLE
            Result.success(snapshot)
        } catch (e: Exception) {
            _operation.value = SnapshotOperation.IDLE
            Result.failure(e)
        }
    }

    suspend fun loadSnapshot(
        snapshotName: String,
        diskImagePath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        _operation.value = SnapshotOperation.LOADING
        try {
            val process = ProcessBuilder(
                getQemuImgPath(),
                "snapshot",
                "-a", snapshotName,
                diskImagePath
            ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                _operation.value = SnapshotOperation.IDLE
                return@withContext Result.failure(Exception("Load snapshot failed: $error"))
            }

            _operation.value = SnapshotOperation.IDLE
            Result.success(Unit)
        } catch (e: Exception) {
            _operation.value = SnapshotOperation.IDLE
            Result.failure(e)
        }
    }

    suspend fun deleteSnapshot(
        snapshotName: String,
        diskImagePath: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        _operation.value = SnapshotOperation.DELETING
        try {
            val process = ProcessBuilder(
                getQemuImgPath(),
                "snapshot",
                "-d", snapshotName,
                diskImagePath
            ).redirectErrorStream(true).start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.inputStream.bufferedReader().readText()
                _operation.value = SnapshotOperation.IDLE
                return@withContext Result.failure(Exception("Delete snapshot failed: $error"))
            }

            refreshSnapshots()
            _operation.value = SnapshotOperation.IDLE
            Result.success(Unit)
        } catch (e: Exception) {
            _operation.value = SnapshotOperation.IDLE
            Result.failure(e)
        }
    }

    suspend fun listDiskSnapshots(diskImagePath: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(
                getQemuImgPath(),
                "snapshot",
                "-l", diskImagePath
            ).redirectErrorStream(true).start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse qemu-img snapshot -l output
            output.lines()
                .drop(2) // Skip header lines
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) parts[1] else null
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun refreshSnapshots() {
        val list = snapshotDir.listFiles()?.mapNotNull { dir ->
            val metaFile = File(dir, "metadata.properties")
            if (!metaFile.exists()) return@mapNotNull null

            val props = metaFile.readLines().associate { line ->
                val (key, value) = line.split("=", limit = 2)
                key.trim() to value.trim()
            }

            Snapshot(
                id = props["id"] ?: dir.name,
                name = props["name"] ?: "Unknown",
                vmName = props["vm"] ?: "Unknown",
                timestamp = props["timestamp"]?.toLongOrNull() ?: 0L,
                sizeBytes = props["disk"]?.let { File(it).length() } ?: 0L,
                description = props["description"] ?: ""
            )
        }?.sortedByDescending { it.timestamp } ?: emptyList()

        _snapshots.value = list
    }

    private fun getQemuImgPath(): String {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        return "$nativeLibDir/libqemu-img.so"
    }
}
