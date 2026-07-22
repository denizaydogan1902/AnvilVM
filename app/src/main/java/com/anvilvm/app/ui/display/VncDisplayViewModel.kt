package com.anvilvm.app.ui.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anvilvm.app.engine.VncBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VncDisplayViewModel @Inject constructor(
    private val vncBridge: VncBridge
) : ViewModel() {

    private val _connectionState = MutableStateFlow(VncConnectionState.DISCONNECTED)
    val connectionState: StateFlow<VncConnectionState> = _connectionState

    fun connect(host: String = "127.0.0.1", port: Int = 5900) {
        viewModelScope.launch(Dispatchers.IO) {
            _connectionState.value = VncConnectionState.CONNECTING
            val success = vncBridge.connect(host, port)
            _connectionState.value = if (success) {
                VncConnectionState.CONNECTED
            } else {
                VncConnectionState.DISCONNECTED
            }
        }
    }

    fun disconnect() {
        vncBridge.disconnect()
        _connectionState.value = VncConnectionState.DISCONNECTED
    }

    fun onTap(x: Float, y: Float) {
        // TODO: Translate screen coordinates to VNC pointer event
    }

    override fun onCleared() {
        super.onCleared()
        vncBridge.disconnect()
    }
}
