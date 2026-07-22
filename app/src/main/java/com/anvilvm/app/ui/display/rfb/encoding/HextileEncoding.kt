package com.anvilvm.app.ui.display.rfb.encoding

import com.anvilvm.app.ui.display.rfb.PixelFormat
import java.io.DataInputStream

class HextileEncoding : RfbEncoding {

    companion object {
        const val RAW = 1
        const val BACKGROUND_SPECIFIED = 2
        const val FOREGROUND_SPECIFIED = 4
        const val ANY_SUBRECTS = 8
        const val SUBRECTS_COLOURED = 16
    }

    override fun decode(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        x: Int, y: Int,
        w: Int, h: Int,
        pixelFormat: PixelFormat
    ) {
        val bytesPerPixel = pixelFormat.bitsPerPixel / 8
        var bgColor = 0xFF000000.toInt()
        var fgColor = 0xFFFFFFFF.toInt()

        var tileY = y
        while (tileY < y + h) {
            val tileH = minOf(16, y + h - tileY)
            var tileX = x
            while (tileX < x + w) {
                val tileW = minOf(16, x + w - tileX)
                val subEncoding = input.readUnsignedByte()

                if (subEncoding and RAW != 0) {
                    // Raw sub-encoding
                    val rawData = ByteArray(tileW * tileH * bytesPerPixel)
                    input.readFully(rawData)
                    for (row in 0 until tileH) {
                        for (col in 0 until tileW) {
                            val px = pixelFormat.decodePixel(rawData, (row * tileW + col) * bytesPerPixel)
                            val idx = (tileY + row) * fbWidth + (tileX + col)
                            if (idx < framebuffer.size) framebuffer[idx] = px
                        }
                    }
                } else {
                    if (subEncoding and BACKGROUND_SPECIFIED != 0) {
                        val bgData = ByteArray(bytesPerPixel)
                        input.readFully(bgData)
                        bgColor = pixelFormat.decodePixel(bgData, 0)
                    }

                    // Fill tile with background
                    for (row in 0 until tileH) {
                        for (col in 0 until tileW) {
                            val idx = (tileY + row) * fbWidth + (tileX + col)
                            if (idx < framebuffer.size) framebuffer[idx] = bgColor
                        }
                    }

                    if (subEncoding and FOREGROUND_SPECIFIED != 0) {
                        val fgData = ByteArray(bytesPerPixel)
                        input.readFully(fgData)
                        fgColor = pixelFormat.decodePixel(fgData, 0)
                    }

                    if (subEncoding and ANY_SUBRECTS != 0) {
                        val numSubrects = input.readUnsignedByte()
                        val coloured = subEncoding and SUBRECTS_COLOURED != 0

                        for (s in 0 until numSubrects) {
                            val subrectColor = if (coloured) {
                                val colorData = ByteArray(bytesPerPixel)
                                input.readFully(colorData)
                                pixelFormat.decodePixel(colorData, 0)
                            } else {
                                fgColor
                            }

                            val xy = input.readUnsignedByte()
                            val wh = input.readUnsignedByte()
                            val sx = (xy shr 4) and 0x0F
                            val sy = xy and 0x0F
                            val sw = ((wh shr 4) and 0x0F) + 1
                            val sh = (wh and 0x0F) + 1

                            for (row in sy until sy + sh) {
                                for (col in sx until sx + sw) {
                                    val idx = (tileY + row) * fbWidth + (tileX + col)
                                    if (idx < framebuffer.size) framebuffer[idx] = subrectColor
                                }
                            }
                        }
                    }
                }
                tileX += 16
            }
            tileY += 16
        }
    }
}
