package com.anvilvm.app.ui.terminal.parser

import androidx.compose.ui.graphics.Color

data class CellAttributes(
    val fg: Color = AnsiColor.WHITE,
    val bg: Color = AnsiColor.BLACK,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
    val dim: Boolean = false
) {
    val effectiveFg: Color
        get() = if (inverse) bg else fg

    val effectiveBg: Color
        get() = if (inverse) fg else bg
}

data class TerminalCell(
    val char: Char = ' ',
    val attrs: CellAttributes = CellAttributes()
)
