package com.anvilvm.app.ui.terminal.parser

import androidx.compose.ui.graphics.Color

object AnsiColor {
    // Standard 8 colors
    val BLACK = Color(0xFF000000)
    val RED = Color(0xFFCC0000)
    val GREEN = Color(0xFF00CC00)
    val YELLOW = Color(0xFFCCCC00)
    val BLUE = Color(0xFF0000CC)
    val MAGENTA = Color(0xFFCC00CC)
    val CYAN = Color(0xFF00CCCC)
    val WHITE = Color(0xFFCCCCCC)

    // Bright variants
    val BRIGHT_BLACK = Color(0xFF555555)
    val BRIGHT_RED = Color(0xFFFF5555)
    val BRIGHT_GREEN = Color(0xFF55FF55)
    val BRIGHT_YELLOW = Color(0xFFFFFF55)
    val BRIGHT_BLUE = Color(0xFF5555FF)
    val BRIGHT_MAGENTA = Color(0xFFFF55FF)
    val BRIGHT_CYAN = Color(0xFF55FFFF)
    val BRIGHT_WHITE = Color(0xFFFFFFFF)

    private val standardColors = arrayOf(
        BLACK, RED, GREEN, YELLOW, BLUE, MAGENTA, CYAN, WHITE
    )

    private val brightColors = arrayOf(
        BRIGHT_BLACK, BRIGHT_RED, BRIGHT_GREEN, BRIGHT_YELLOW,
        BRIGHT_BLUE, BRIGHT_MAGENTA, BRIGHT_CYAN, BRIGHT_WHITE
    )

    fun fromIndex(index: Int, bright: Boolean = false): Color {
        return when {
            index in 0..7 && bright -> brightColors[index]
            index in 0..7 -> standardColors[index]
            index in 8..15 -> brightColors[index - 8]
            index in 16..231 -> {
                // 216-color cube: 16 + 36*r + 6*g + b
                val adjusted = index - 16
                val r = (adjusted / 36) * 51
                val g = ((adjusted % 36) / 6) * 51
                val b = (adjusted % 6) * 51
                Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
            }
            index in 232..255 -> {
                // Grayscale: 24 steps
                val gray = (index - 232) * 10 + 8
                Color(0xFF000000 or (gray.toLong() shl 16) or (gray.toLong() shl 8) or gray.toLong())
            }
            else -> WHITE
        }
    }
}
