package com.anvilvm.app.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anvilvm.app.engine.PtyBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val ptyBridge: PtyBridge
) : ViewModel() {

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    private val outputBuffer = StringBuilder()

    init {
        startReadLoop()
    }

    fun onInputChanged(text: String) {
        _inputText.value = text
    }

    fun submitInput() {
        val text = _inputText.value
        if (text.isNotEmpty()) {
            ptyBridge.write("$text\n")
            _inputText.value = ""
        }
    }

    private fun startReadLoop() {
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(4096)
            while (true) {
                val bytesRead = ptyBridge.read(buffer)
                if (bytesRead > 0) {
                    val text = String(buffer, 0, bytesRead)
                    outputBuffer.append(text)
                    _output.value = outputBuffer.toString()
                }
                kotlinx.coroutines.delay(16) // ~60fps refresh
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ptyBridge.close()
    }
}
