package com.anvilvm.app.engine.snapshot

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class QmpClient(private val port: Int = 4444) {

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket("127.0.0.1", port)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
            writer = PrintWriter(socket!!.getOutputStream(), true)

            // Read QMP greeting
            val greeting = reader!!.readLine() ?: return@withContext false

            // Send qmp_capabilities to enter command mode
            sendCommand("""{"execute": "qmp_capabilities"}""")
            val response = readResponse()

            response?.has("return") == true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveVMSnapshot(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Stop VM before snapshot
            sendCommand("""{"execute": "stop"}""")
            readResponse()

            // Create snapshot
            val cmd = """{"execute": "human-monitor-command", "arguments": {"command-line": "savevm $name"}}"""
            sendCommand(cmd)
            val response = readResponse()

            // Resume VM
            sendCommand("""{"execute": "cont"}""")
            readResponse()

            if (response?.optString("return", "")?.contains("Error") == true) {
                Result.failure(Exception(response.getString("return")))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadVMSnapshot(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            sendCommand("""{"execute": "stop"}""")
            readResponse()

            val cmd = """{"execute": "human-monitor-command", "arguments": {"command-line": "loadvm $name"}}"""
            sendCommand(cmd)
            val response = readResponse()

            sendCommand("""{"execute": "cont"}""")
            readResponse()

            if (response?.optString("return", "")?.contains("Error") == true) {
                Result.failure(Exception(response.getString("return")))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVMSnapshot(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cmd = """{"execute": "human-monitor-command", "arguments": {"command-line": "delvm $name"}}"""
            sendCommand(cmd)
            val response = readResponse()

            if (response?.optString("return", "")?.contains("Error") == true) {
                Result.failure(Exception(response.getString("return")))
            } else {
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun querySnapshots(): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = """{"execute": "human-monitor-command", "arguments": {"command-line": "info snapshots"}}"""
            sendCommand(cmd)
            val response = readResponse()
            val output = response?.optString("return", "") ?: ""

            output.lines()
                .drop(2)
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) parts[1] else null
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun disconnect() {
        try {
            writer?.close()
            reader?.close()
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        reader = null
        writer = null
    }

    private fun sendCommand(json: String) {
        writer?.println(json)
        writer?.flush()
    }

    private fun readResponse(): JSONObject? {
        return try {
            val line = reader?.readLine() ?: return null
            JSONObject(line)
        } catch (e: Exception) {
            null
        }
    }
}
