package com.anvilvm.app.ui.display.rfb.encoding

import com.anvilvm.app.ui.display.rfb.PixelFormat
import java.io.DataInputStream

interface RfbEncoding {
    fun decode(
        input: DataInputStream,
        framebuffer: IntArray,
        fbWidth: Int,
        x: Int, y: Int,
        w: Int, h: Int,
        pixelFormat: PixelFormat
    )
}
