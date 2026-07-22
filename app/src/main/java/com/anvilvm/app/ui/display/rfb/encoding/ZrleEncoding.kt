package com.anvilvm.app.ui.display.rfb.encoding

import com.anvilvm.app.ui.display.rfb.PixelFormat
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.Inflater

class ZrleEncoding : RfbEncoding {

    private val inflater = Inflater()
    private var decompressBuffer = ByteArray(1024 * 1024)

    override fun decode(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        x: Int, y: Int,
        w: Int, h: Int,
        pixelFormat: PixelFormat
    ) {
        val compressedLength = input.readInt()
        val compressedData = ByteArray(compressedLength)
        input.readFully(compressedData)

        // Decompress
        inflater.setInput(compressedData)
        var totalDecompressed = 0
        while (!inflater.needsInput()) {
            if (totalDecompressed >= decompressBuffer.size) {
                decompressBuffer = decompressBuffer.copyOf(decompressBuffer.size * 2)
            }
            val n = inflater.inflate(decompressBuffer, totalDecompressed, decompressBuffer.size - totalDecompressed)
            if (n == 0) break
            totalDecompressed += n
        }

        val zrleStream = DataInputStream(ByteArrayInputStream(decompressBuffer, 0, totalDecompressed))
        val cpixelSize = if (pixelFormat.bitsPerPixel == 32 && pixelFormat.depth <= 24) 3 else pixelFormat.bitsPerPixel / 8

        var tileY = y
        while (tileY < y + h) {
            val tileH = minOf(64, y + h - tileY)
            var tileX = x
            while (tileX < x + w) {
                val tileW = minOf(64, x + w - tileX)
                decodeTile(zrleStream, framebuffer, fbWidth, tileX, tileY, tileW, tileH, cpixelSize, pixelFormat)
                tileX += 64
            }
            tileY += 64
        }
    }

    private fun decodeTile(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        tx: Int, ty: Int,
        tw: Int, th: Int,
        cpixelSize: Int,
        pixelFormat: PixelFormat
    ) {
        val subEncoding = input.readUnsignedByte()

        when {
            subEncoding == 0 -> {
                // Raw CPIXEL data
                for (row in 0 until th) {
                    for (col in 0 until tw) {
                        val pixel = readCPixel(input, cpixelSize, pixelFormat)
                        val idx = (ty + row) * fbWidth + (tx + col)
                        if (idx < framebuffer.size) framebuffer[idx] = pixel
                    }
                }
            }
            subEncoding == 1 -> {
                // Solid tile
                val color = readCPixel(input, cpixelSize, pixelFormat)
                for (row in 0 until th) {
                    for (col in 0 until tw) {
                        val idx = (ty + row) * fbWidth + (tx + col)
                        if (idx < framebuffer.size) framebuffer[idx] = color
                    }
                }
            }
            subEncoding in 2..16 -> {
                // Packed palette
                val paletteSize = subEncoding
                val palette = IntArray(paletteSize) { readCPixel(input, cpixelSize, pixelFormat) }
                val bitsPerIndex = when {
                    paletteSize <= 2 -> 1
                    paletteSize <= 4 -> 2
                    else -> 4
                }
                decodePacked(input, framebuffer, fbWidth, tx, ty, tw, th, palette, bitsPerIndex)
            }
            subEncoding == 128 -> {
                // Plain RLE
                decodePlainRle(input, framebuffer, fbWidth, tx, ty, tw, th, cpixelSize, pixelFormat)
            }
            subEncoding >= 130 -> {
                // Palette RLE
                val paletteSize = subEncoding - 128
                val palette = IntArray(paletteSize) { readCPixel(input, cpixelSize, pixelFormat) }
                decodePaletteRle(input, framebuffer, fbWidth, tx, ty, tw, th, palette)
            }
        }
    }

    private fun decodePacked(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        tx: Int, ty: Int,
        tw: Int, th: Int,
        palette: IntArray,
        bitsPerIndex: Int
    ) {
        val mask = (1 shl bitsPerIndex) - 1
        for (row in 0 until th) {
            var bitPos = 0
            var currentByte = 0
            for (col in 0 until tw) {
                if (bitPos == 0) {
                    currentByte = input.readUnsignedByte()
                    bitPos = 8
                }
                bitPos -= bitsPerIndex
                val index = (currentByte shr bitPos) and mask
                val idx = (ty + row) * fbWidth + (tx + col)
                if (idx < framebuffer.size && index < palette.size) {
                    framebuffer[idx] = palette[index]
                }
            }
        }
    }

    private fun decodePlainRle(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        tx: Int, ty: Int,
        tw: Int, th: Int,
        cpixelSize: Int,
        pixelFormat: PixelFormat
    ) {
        val totalPixels = tw * th
        var pixelsDone = 0
        while (pixelsDone < totalPixels) {
            val color = readCPixel(input, cpixelSize, pixelFormat)
            var runLength = 1
            var b: Int
            do {
                b = input.readUnsignedByte()
                runLength += b
            } while (b == 255)

            for (i in 0 until runLength) {
                val pos = pixelsDone + i
                if (pos >= totalPixels) break
                val row = pos / tw
                val col = pos % tw
                val idx = (ty + row) * fbWidth + (tx + col)
                if (idx < framebuffer.size) framebuffer[idx] = color
            }
            pixelsDone += runLength
        }
    }

    private fun decodePaletteRle(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        tx: Int, ty: Int,
        tw: Int, th: Int,
        palette: IntArray
    ) {
        val totalPixels = tw * th
        var pixelsDone = 0
        while (pixelsDone < totalPixels) {
            val paletteIndex = input.readUnsignedByte()
            val color = if (paletteIndex and 0x80 != 0) {
                // RLE run
                val actualIndex = paletteIndex and 0x7F
                var runLength = 1
                var b: Int
                do {
                    b = input.readUnsignedByte()
                    runLength += b
                } while (b == 255)

                val c = if (actualIndex < palette.size) palette[actualIndex] else 0
                for (i in 0 until runLength) {
                    val pos = pixelsDone + i
                    if (pos >= totalPixels) break
                    val row = pos / tw
                    val col = pos % tw
                    val idx = (ty + row) * fbWidth + (tx + col)
                    if (idx < framebuffer.size) framebuffer[idx] = c
                }
                pixelsDone += runLength
                continue
            } else {
                if (paletteIndex < palette.size) palette[paletteIndex] else 0
            }

            val pos = pixelsDone
            val row = pos / tw
            val col = pos % tw
            val idx = (ty + row) * fbWidth + (tx + col)
            if (idx < framebuffer.size) framebuffer[idx] = color
            pixelsDone++
        }
    }

    private fun readCPixel(input: DataInputStream, cpixelSize: Int, pixelFormat: PixelFormat): Int {
        val data = ByteArray(cpixelSize)
        input.readFully(data)
        return if (cpixelSize == 3) {
            // CPIXEL: 3 bytes in little-endian order for 32bpp true-color
            (0xFF shl 24) or
            ((data[2].toInt() and 0xFF) shl 16) or
            ((data[1].toInt() and 0xFF) shl 8) or
            (data[0].toInt() and 0xFF)
        } else {
            pixelFormat.decodePixel(data, 0)
        }
    }

    fun reset() {
        inflater.reset()
    }
}
