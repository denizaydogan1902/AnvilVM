package com.anvilvm.app.ui.display.rfb

import android.graphics.Bitmap
import com.anvilvm.app.ui.display.rfb.encoding.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket

class RfbClient {

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var job: Job? = null

    private val rawEncoding = RawEncoding()
    private val copyRectEncoding = CopyRectEncoding()
    private val hextileEncoding = HextileEncoding()
    private val zrleEncoding = ZrleEncoding()

    var width = 0
        private set
    var height = 0
        private set
    var serverName = ""
        private set

    private var pixelFormat = PixelFormat()
    private var framebuffer: IntArray = IntArray(0)

    private val _bitmap = MutableStateFlow<Bitmap?>(null)
    val bitmap: StateFlow<Bitmap?> = _bitmap

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            input = DataInputStream(socket!!.getInputStream())
            output = DataOutputStream(socket!!.getOutputStream())

            if (!handshake()) return@withContext false
            if (!authenticate()) return@withContext false
            if (!initialize()) return@withContext false

            _connected.value = true
            startFrameLoop()
            true
        } catch (e: IOException) {
            disconnect()
            false
        }
    }

    private fun handshake(): Boolean {
        val serverVersion = ByteArray(12)
        input!!.readFully(serverVersion)

        // Send our version (3.8)
        output!!.write(RfbConstants.VERSION_3_8.toByteArray())
        output!!.flush()
        return true
    }

    private fun authenticate(): Boolean {
        val numSecTypes = input!!.readUnsignedByte()
        if (numSecTypes == 0) {
            // Connection failed
            return false
        }

        val secTypes = ByteArray(numSecTypes)
        input!!.readFully(secTypes)

        // Prefer no authentication
        val useNone = secTypes.any { it.toInt() == RfbConstants.SECURITY_NONE }
        if (useNone) {
            output!!.writeByte(RfbConstants.SECURITY_NONE)
            output!!.flush()

            // Read security result
            val result = input!!.readInt()
            return result == 0
        }

        return false
    }

    private fun initialize(): Boolean {
        // ClientInit: shared flag = true
        output!!.writeByte(1)
        output!!.flush()

        // ServerInit
        width = input!!.readUnsignedShort()
        height = input!!.readUnsignedShort()

        // Read server pixel format
        val pfBytes = ByteArray(16)
        input!!.readFully(pfBytes)
        pixelFormat = PixelFormat(
            bitsPerPixel = pfBytes[0].toInt() and 0xFF,
            depth = pfBytes[1].toInt() and 0xFF,
            bigEndian = pfBytes[2].toInt() != 0,
            trueColour = pfBytes[3].toInt() != 0,
            redMax = ((pfBytes[4].toInt() and 0xFF) shl 8) or (pfBytes[5].toInt() and 0xFF),
            greenMax = ((pfBytes[6].toInt() and 0xFF) shl 8) or (pfBytes[7].toInt() and 0xFF),
            blueMax = ((pfBytes[8].toInt() and 0xFF) shl 8) or (pfBytes[9].toInt() and 0xFF),
            redShift = pfBytes[10].toInt() and 0xFF,
            greenShift = pfBytes[11].toInt() and 0xFF,
            blueShift = pfBytes[12].toInt() and 0xFF
        )

        // Read server name
        val nameLength = input!!.readInt()
        val nameBytes = ByteArray(nameLength)
        input!!.readFully(nameBytes)
        serverName = String(nameBytes)

        // Initialize framebuffer
        framebuffer = IntArray(width * height)

        // Set our preferred pixel format (XRGB8888)
        sendSetPixelFormat()
        sendSetEncodings()

        return true
    }

    private fun sendSetPixelFormat() {
        pixelFormat = PixelFormat() // Our preferred: 32bpp XRGB
        output!!.writeByte(RfbConstants.MSG_SET_PIXEL_FORMAT)
        output!!.write(ByteArray(3)) // padding
        output!!.write(pixelFormat.toBytes())
        output!!.flush()
    }

    private fun sendSetEncodings() {
        val encodings = intArrayOf(
            RfbConstants.ENCODING_ZRLE,
            RfbConstants.ENCODING_HEXTILE,
            RfbConstants.ENCODING_COPYRECT,
            RfbConstants.ENCODING_RAW,
            RfbConstants.ENCODING_DESKTOP_SIZE
        )
        output!!.writeByte(RfbConstants.MSG_SET_ENCODINGS)
        output!!.writeByte(0) // padding
        output!!.writeShort(encodings.size)
        for (enc in encodings) {
            output!!.writeInt(enc)
        }
        output!!.flush()
    }

    fun requestFullUpdate() {
        sendFramebufferUpdateRequest(0, 0, width, height, incremental = false)
    }

    fun requestIncrementalUpdate() {
        sendFramebufferUpdateRequest(0, 0, width, height, incremental = true)
    }

    private fun sendFramebufferUpdateRequest(
        x: Int, y: Int, w: Int, h: Int, incremental: Boolean
    ) {
        try {
            output!!.writeByte(RfbConstants.MSG_FRAMEBUFFER_UPDATE_REQUEST)
            output!!.writeByte(if (incremental) 1 else 0)
            output!!.writeShort(x)
            output!!.writeShort(y)
            output!!.writeShort(w)
            output!!.writeShort(h)
            output!!.flush()
        } catch (_: IOException) {}
    }

    fun sendKeyEvent(down: Boolean, key: Int) {
        try {
            output!!.writeByte(RfbConstants.MSG_KEY_EVENT)
            output!!.writeByte(if (down) 1 else 0)
            output!!.write(ByteArray(2)) // padding
            output!!.writeInt(key)
            output!!.flush()
        } catch (_: IOException) {}
    }

    fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) {
        try {
            output!!.writeByte(RfbConstants.MSG_POINTER_EVENT)
            output!!.writeByte(buttonMask)
            output!!.writeShort(x)
            output!!.writeShort(y)
            output!!.flush()
        } catch (_: IOException) {}
    }

    private fun startFrameLoop() {
        job = CoroutineScope(Dispatchers.IO).launch {
            requestFullUpdate()

            while (isActive && _connected.value) {
                try {
                    handleServerMessage()
                } catch (e: IOException) {
                    _connected.value = false
                    break
                }
            }
        }
    }

    private fun handleServerMessage() {
        val msgType = input!!.readUnsignedByte()
        when (msgType) {
            RfbConstants.MSG_FRAMEBUFFER_UPDATE -> handleFramebufferUpdate()
            RfbConstants.MSG_SET_COLOUR_MAP -> skipColourMap()
            RfbConstants.MSG_BELL -> {} // ignore
            RfbConstants.MSG_SERVER_CUT_TEXT -> skipCutText()
        }
    }

    private fun handleFramebufferUpdate() {
        input!!.readByte() // padding
        val numRects = input!!.readUnsignedShort()

        for (i in 0 until numRects) {
            val x = input!!.readUnsignedShort()
            val y = input!!.readUnsignedShort()
            val w = input!!.readUnsignedShort()
            val h = input!!.readUnsignedShort()
            val encoding = input!!.readInt()

            when (encoding) {
                RfbConstants.ENCODING_RAW ->
                    rawEncoding.decode(input!!, framebuffer, width, x, y, w, h, pixelFormat)
                RfbConstants.ENCODING_COPYRECT ->
                    copyRectEncoding.decode(input!!, framebuffer, width, x, y, w, h, pixelFormat)
                RfbConstants.ENCODING_HEXTILE ->
                    hextileEncoding.decode(input!!, framebuffer, width, x, y, w, h, pixelFormat)
                RfbConstants.ENCODING_ZRLE ->
                    zrleEncoding.decode(input!!, framebuffer, width, x, y, w, h, pixelFormat)
                RfbConstants.ENCODING_DESKTOP_SIZE -> {
                    width = w
                    height = h
                    framebuffer = IntArray(width * height)
                }
                else ->
                    rawEncoding.decode(input!!, framebuffer, width, x, y, w, h, pixelFormat)
            }
        }

        // Update bitmap
        updateBitmap()
        requestIncrementalUpdate()
    }

    private fun updateBitmap() {
        if (width <= 0 || height <= 0) return
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(framebuffer, 0, width, 0, 0, width, height)
        _bitmap.value = bmp
    }

    private fun skipColourMap() {
        input!!.readByte() // padding
        input!!.readUnsignedShort() // first colour
        val numColours = input!!.readUnsignedShort()
        input!!.skipBytes(numColours * 6)
    }

    private fun skipCutText() {
        input!!.skipBytes(3) // padding
        val length = input!!.readInt()
        input!!.skipBytes(length)
    }

    fun disconnect() {
        _connected.value = false
        job?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        input = null
        output = null
    }
}
