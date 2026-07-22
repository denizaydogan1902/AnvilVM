package com.anvilvm.app.ui.display.rfb.encoding

import com.anvilvm.app.ui.display.rfb.PixelFormat
import java.io.DataInputStream

class RawEncoding : RfbEncoding {
    override fun decode(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        x: Int, y: Int,
        w: Int, h: Int,
        pixelFormat: PixelFormat
    ) {
        val bytesPerPixel = pixelFormat.bitsPerPixel / 8
        val rowBuffer = ByteArray(w * bytesPerPixel)

        for (row in 0 until h) {
            input.readFully(rowBuffer)
            val destY = y + row
            if (destY >= framebuffer.size / fbWidth) continue

            for (col in 0 until w) {
                val destX = x + col
                if (destX >= fbWidth) continue
                val pixel = pixelFormat.decodePixel(rowBuffer, col * bytesPerPixel)
                framebuffer[destY * fbWidth + destX] = pixel
            }
        }
    }
}
