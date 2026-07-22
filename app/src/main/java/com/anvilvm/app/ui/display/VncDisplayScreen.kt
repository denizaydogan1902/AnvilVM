package com.anvilvm.app.ui.display

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun VncDisplayScreen(
    viewModel: VncDisplayViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val bitmap by viewModel.framebuffer.collectAsState()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

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
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = "VM Display",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    viewModel.onTap(
                                        offset.x, offset.y,
                                        canvasSize.width.toFloat(),
                                        canvasSize.height.toFloat()
                                    )
                                }
                            }
                    )
                } else {
                    Text(
                        text = "Waiting for framebuffer...",
                        color = Color(0xFF555555),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

enum class VncConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED
}
