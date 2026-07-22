package com.anvilvm.app.ui.display.rfb.encoding

import com.anvilvm.app.ui.display.rfb.PixelFormat
import java.io.DataInputStream

class CopyRectEncoding : RfbEncoding {
    override fun decode(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        x: Int, y: Int,
        w: Int, h: Int,
        pixelFormat: PixelFormat
    ) {
        val srcX = input.readUnsignedShort()
        val srcY = input.readUnsignedShort()

        val fbHeight = framebuffer.size / fbWidth

        // Copy in correct order to handle overlapping regions
        if (srcY < y || (srcY == y && srcX < x)) {
            for (row in h - 1 downTo 0) {
                for (col in w - 1 downTo 0) {
                    val si = (srcY + row) * fbWidth + (srcX + col)
                    val di = (y + row) * fbWidth + (x + col)
                    if (si < framebuffer.size && di < framebuffer.size) {
                        framebuffer[di] = framebuffer[si]
                    }
                }
            }
        } else {
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val si = (srcY + row) * fbWidth + (srcX + col)
                    val di = (y + row) * fbWidth + (x + col)
                    if (si < framebuffer.size && di < framebuffer.size) {
                        framebuffer[di] = framebuffer[si]
                    }
                }
            }
        }
    }
}
