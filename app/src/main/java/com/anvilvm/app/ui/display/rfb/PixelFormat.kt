package com.anvilvm.app.ui.display.rfb

data class PixelFormat(
    val bitsPerPixel: Int = 32,
    val depth: Int = 24,
    val bigEndian: Boolean = false,
    val trueColour: Boolean = true,
    val redMax: Int = 255,
    val greenMax: Int = 255,
    val blueMax: Int = 255,
    val redShift: Int = 16,
    val greenShift: Int = 8,
    val blueShift: Int = 0
) {
    fun toBytes(): ByteArray {
        return byteArrayOf(
            bitsPerPixel.toByte(),
            depth.toByte(),
            if (bigEndian) 1 else 0,
            if (trueColour) 1 else 0,
            (redMax shr 8).toByte(), (redMax and 0xFF).toByte(),
            (greenMax shr 8).toByte(), (greenMax and 0xFF).toByte(),
            (blueMax shr 8).toByte(), (blueMax and 0xFF).toByte(),
            redShift.toByte(),
            greenShift.toByte(),
            blueShift.toByte(),
            0, 0, 0  // padding
        )
    }

    fun decodePixel(data: ByteArray, offset: Int): Int {
        val raw = if (bitsPerPixel == 32) {
            (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)
        } else {
            (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8)
        }

        val r = ((raw shr redShift) and redMax) * 255 / redMax
        val g = ((raw shr greenShift) and greenMax) * 255 / greenMax
        val b = ((raw shr blueShift) and blueMax) * 255 / blueMax

        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
