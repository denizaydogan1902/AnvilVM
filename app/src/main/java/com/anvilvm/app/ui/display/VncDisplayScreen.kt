package com.anvilvm.app.ui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun VncDisplayScreen(
    viewModel: VncDisplayViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0D))
    ) {
        when (connectionState) {
            VncConnectionState.DISCONNECTED -> {
                Text(
                    text = "VNC Display - Not Connected",
                    color = Color(0xFF00E5FF),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            VncConnectionState.CONNECTING -> {
                Text(
                    text = "Connecting to VM display...",
                    color = Color(0xFFFFAA00),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            VncConnectionState.CONNECTED -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                viewModel.onTap(offset.x, offset.y)
                            }
                        }
                ) {
                    // Framebuffer rendering will be implemented here
                    // For now, draw a placeholder
                    drawRect(Color(0xFF1A1B1D))
                }
            }
        }
    }
}

enum class VncConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}
