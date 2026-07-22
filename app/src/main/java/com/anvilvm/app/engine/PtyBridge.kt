package com.anvilvm.app.engine

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PtyBridge @Inject constructor() {

    companion object {
        init {
            System.loadLibrary("anvilvm-engine")
        }
    }

    external fun nativeCreatePty(rows: Int, cols: Int): Int
    external fun nativeReadPty(buffer: ByteArray, maxLen: Int): Int
    external fun nativeWritePty(data: ByteArray, len: Int): Int
    external fun nativeResizePty(rows: Int, cols: Int)
    external fun nativeClosePty()
    external fun nativeGetSlaveFd(): Int

    private var isOpen = false

    fun open(rows: Int = 24, cols: Int = 80): Boolean {
        val fd = nativeCreatePty(rows, cols)
        isOpen = fd >= 0
        return isOpen
    }

    fun read(buffer: ByteArray): Int {
        if (!isOpen) return -1
        return nativeReadPty(buffer, buffer.size)
    }

    fun write(data: ByteArray): Int {
        if (!isOpen) return -1
        return nativeWritePty(data, data.size)
    }

    fun write(text: String): Int = write(text.toByteArray())

    fun resize(rows: Int, cols: Int) {
        if (isOpen) nativeResizePty(rows, cols)
    }

    fun close() {
        if (isOpen) {
            nativeClosePty()
            isOpen = false
        }
    }

    fun getSlaveFd(): Int = nativeGetSlaveFd()
}
