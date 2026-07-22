package com.anvilvm.app.engine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VncBridge @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("anvilvm-engine")
        }
    }

    external fun nativeConnect(host: String, port: Int): Boolean
    external fun nativeReadFrame(maxBytes: Int): ByteArray?
    external fun nativeSendInput(data: ByteArray): Boolean
    external fun nativeDisconnect()

    private var connected = false

    fun connect(host: String = "127.0.0.1", port: Int = 5900): Boolean {
        connected = nativeConnect(host, port)
        return connected
    }

    fun readFrame(maxBytes: Int = 65536): ByteArray? {
        if (!connected) return null
        return nativeReadFrame(maxBytes)
    }

    fun sendInput(data: ByteArray): Boolean {
        if (!connected) return false
        return nativeSendInput(data)
    }

    fun disconnect() {
        if (connected) {
            nativeDisconnect()
            connected = false
        }
    }

    fun isConnected(): Boolean = connected
}
