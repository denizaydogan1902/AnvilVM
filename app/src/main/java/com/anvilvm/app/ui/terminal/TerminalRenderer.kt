package com.anvilvm.app.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.anvilvm.app.ui.terminal.parser.AnsiColor
import com.anvilvm.app.ui.terminal.parser.TerminalBuffer

@Composable
fun TerminalRenderer(
    buffer: TerminalBuffer,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cellWidth = with(density) { 8.dp.toPx() }
    val cellHeight = with(density) { 16.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B0D))
    ) {
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = cellHeight * 0.8f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        for (row in 0 until buffer.rows) {
            for (col in 0 until buffer.cols) {
                val cell = buffer.getCell(row, col)
                val x = col * cellWidth
                val y = row * cellHeight

                // Draw background
                if (cell.attrs.effectiveBg != AnsiColor.BLACK) {
                    drawRect(
                        color = cell.attrs.effectiveBg,
                        topLeft = Offset(x, y),
                        size = Size(cellWidth, cellHeight)
                    )
                }

                // Draw character
                if (cell.char != ' ' && cell.char.code != 0) {
                    paint.color = cell.attrs.effectiveFg.toArgb()
                    paint.isFakeBoldText = cell.attrs.bold

                    drawContext.canvas.nativeCanvas.drawText(
                        cell.char.toString(),
                        x,
                        y + cellHeight * 0.75f,
                        paint
                    )
                }
            }
        }

        // Draw cursor
        if (buffer.cursorVisible) {
            val cursorX = buffer.cursorCol * cellWidth
            val cursorY = buffer.cursorRow * cellHeight
            drawRect(
                color = Color(0xFF00E5FF),
                topLeft = Offset(cursorX, cursorY),
                size = Size(cellWidth, cellHeight),
                alpha = 0.7f
            )
        }
    }
}

private fun Color.toArgb(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt()
    )
}
