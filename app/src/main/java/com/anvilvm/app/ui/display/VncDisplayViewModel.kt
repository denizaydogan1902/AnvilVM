package com.anvilvm.app.ui.display

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anvilvm.app.ui.display.rfb.RfbClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VncDisplayViewModel @Inject constructor() : ViewModel() {

    private val rfbClient = RfbClient()

    private val _connectionState = MutableStateFlow(VncConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VncConnectionState> = _connectionState

    val framebuffer: StateFlow<Bitmap?> = rfbClient.bitmap

    val serverWidth: Int get() = rfbClient.width
    val serverHeight: Int get() = rfbClient.height

    fun connect(host: String = "127.0.0.1", port: Int = 5900) {
        viewModelScope.launch {
            _connectionState.value = VncConnectionState.CONNECTING
            val success = rfbClient.connect(host, port)
            _connectionState.value = if (success) {
                VncConnectionState.CONNECTED
            } else {
                VncConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        rfbClient.disconnect()
        _connectionState.value = VncConnectionState.DISCONNECTED
    }

    fun onTap(x: Float, y: Float, canvasWidth: Float, canvasHeight: Float) {
        if (serverWidth <= 0 || serverHeight <= 0) return
        val vncX = (x / canvasWidth * serverWidth).toInt().coerceIn(0, serverWidth - 1)
        val vncY = (y / canvasHeight * serverHeight).toInt().coerceIn(0, serverHeight - 1)
        rfbClient.sendPointerEvent(1, vncX, vncY) // button down
        rfbClient.sendPointerEvent(0, vncX, vncY) // button up
    }

    fun onKeyEvent(down: Boolean, keySym: Int) {
        rfbClient.sendKeyEvent(down, keySym)
    }

    override fun onCleared() {
        super.onCleared()
        rfbClient.disconnect()
    }
}
