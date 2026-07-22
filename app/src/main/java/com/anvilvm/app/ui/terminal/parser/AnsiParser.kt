package com.anvilvm.app.ui.terminal.parser

import androidx.compose.ui.graphics.Color

class AnsiParser(
    private val buffer: TerminalBuffer
) {
    private var state = State.GROUND
    private var currentAttrs = CellAttributes()
    private val params = mutableListOf<Int>()
    private val intermediates = StringBuilder()
    private var currentParam = StringBuilder()

    private enum class State {
        GROUND,
        ESCAPE,
        CSI_ENTRY,
        CSI_PARAM,
        CSI_INTERMEDIATE,
        OSC
    }

    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            process(data[i].toInt().toChar())
        }
    }

    fun feed(text: String) {
        for (c in text) {
            process(c)
        }
    }

    private fun process(c: Char) {
        when (state) {
            State.GROUND -> handleGround(c)
            State.ESCAPE -> handleEscape(c)
            State.CSI_ENTRY -> handleCsiEntry(c)
            State.CSI_PARAM -> handleCsiParam(c)
            State.CSI_INTERMEDIATE -> handleCsiIntermediate(c)
            State.OSC -> handleOsc(c)
        }
    }

    private fun handleGround(c: Char) {
        when (c) {
            '' -> state = State.ESCAPE
            '\n' -> buffer.lineFeed()
            '\r' -> buffer.carriageReturn()
            '\b' -> buffer.backspace()
            '\t' -> buffer.tab()
            '' -> {} // Bell - ignore
            else -> {
                if (c.code >= 32) {
                    buffer.putChar(c, currentAttrs)
                }
            }
        }
    }

    private fun handleEscape(c: Char) {
        when (c) {
            '[' -> {
                state = State.CSI_ENTRY
                params.clear()
                intermediates.clear()
                currentParam.clear()
            }
            ']' -> state = State.OSC
            'c' -> {
                // Reset
                currentAttrs = CellAttributes()
                buffer.eraseInDisplay(2)
                buffer.moveCursor(0, 0)
                state = State.GROUND
            }
            'D' -> { buffer.lineFeed(); state = State.GROUND }
            'E' -> { buffer.carriageReturn(); buffer.lineFeed(); state = State.GROUND }
            'M' -> { /* Reverse index */ state = State.GROUND }
            '7' -> { /* Save cursor */ state = State.GROUND }
            '8' -> { /* Restore cursor */ state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun handleCsiEntry(c: Char) {
        when {
            c in '0'..'9' -> {
                currentParam.append(c)
                state = State.CSI_PARAM
            }
            c == ';' -> {
                params.add(0)
                state = State.CSI_PARAM
            }
            c == '?' -> {
                intermediates.append(c)
                state = State.CSI_PARAM
            }
            else -> {
                dispatchCsi(c)
                state = State.GROUND
            }
        }
    }

    private fun handleCsiParam(c: Char) {
        when {
            c in '0'..'9' -> currentParam.append(c)
            c == ';' -> {
                params.add(if (currentParam.isEmpty()) 0 else currentParam.toString().toInt())
                currentParam.clear()
            }
            c in ' '..'/' -> {
                if (currentParam.isNotEmpty()) {
                    params.add(currentParam.toString().toInt())
                    currentParam.clear()
                }
                intermediates.append(c)
                state = State.CSI_INTERMEDIATE
            }
            else -> {
                if (currentParam.isNotEmpty()) {
                    params.add(currentParam.toString().toInt())
                    currentParam.clear()
                }
                dispatchCsi(c)
                state = State.GROUND
            }
        }
    }

    private fun handleCsiIntermediate(c: Char) {
        when {
            c in ' '..'/' -> intermediates.append(c)
            else -> {
                dispatchCsi(c)
                state = State.GROUND
            }
        }
    }

    private fun handleOsc(c: Char) {
        // Consume until BEL or ST
        if (c == '' || c == '\\') {
            state = State.GROUND
        }
    }

    private fun dispatchCsi(c: Char) {
        val isPrivate = intermediates.startsWith("?")

        when (c) {
            'A' -> { // Cursor Up
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursor(buffer.cursorRow - n, buffer.cursorCol)
            }
            'B' -> { // Cursor Down
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursor(buffer.cursorRow + n, buffer.cursorCol)
            }
            'C' -> { // Cursor Forward
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursor(buffer.cursorRow, buffer.cursorCol + n)
            }
            'D' -> { // Cursor Backward
                val n = params.getOrElse(0) { 1 }.coerceAtLeast(1)
                buffer.moveCursor(buffer.cursorRow, buffer.cursorCol - n)
            }
            'H', 'f' -> { // Cursor Position
                val row = (params.getOrElse(0) { 1 }).coerceAtLeast(1) - 1
                val col = (params.getOrElse(1) { 1 }).coerceAtLeast(1) - 1
                buffer.moveCursor(row, col)
            }
            'J' -> { // Erase in Display
                buffer.eraseInDisplay(params.getOrElse(0) { 0 })
            }
            'K' -> { // Erase in Line
                buffer.eraseInLine(params.getOrElse(0) { 0 })
            }
            'L' -> { // Insert Lines
                buffer.insertLines(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            }
            'M' -> { // Delete Lines
                buffer.deleteLines(params.getOrElse(0) { 1 }.coerceAtLeast(1))
            }
            'm' -> handleSGR()
            'h' -> {
                if (isPrivate && params.getOrElse(0) { 0 } == 25) {
                    buffer.cursorVisible = true
                }
            }
            'l' -> {
                if (isPrivate && params.getOrElse(0) { 0 } == 25) {
                    buffer.cursorVisible = false
                }
            }
            'r' -> { /* Set scrolling region - handled later */ }
        }
    }

    private fun handleSGR() {
        if (params.isEmpty()) {
            currentAttrs = CellAttributes()
            return
        }

        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> currentAttrs = CellAttributes()
                1 -> currentAttrs = currentAttrs.copy(bold = true)
                2 -> currentAttrs = currentAttrs.copy(dim = true)
                3 -> currentAttrs = currentAttrs.copy(italic = true)
                4 -> currentAttrs = currentAttrs.copy(underline = true)
                7 -> currentAttrs = currentAttrs.copy(inverse = true)
                22 -> currentAttrs = currentAttrs.copy(bold = false, dim = false)
                23 -> currentAttrs = currentAttrs.copy(italic = false)
                24 -> currentAttrs = currentAttrs.copy(underline = false)
                27 -> currentAttrs = currentAttrs.copy(inverse = false)
                in 30..37 -> {
                    currentAttrs = currentAttrs.copy(
                        fg = AnsiColor.fromIndex(p - 30, currentAttrs.bold)
                    )
                }
                38 -> {
                    // Extended foreground
                    i = handleExtendedColor(i, true)
                }
                39 -> currentAttrs = currentAttrs.copy(fg = AnsiColor.WHITE)
                in 40..47 -> {
                    currentAttrs = currentAttrs.copy(
                        bg = AnsiColor.fromIndex(p - 40)
                    )
                }
                48 -> {
                    // Extended background
                    i = handleExtendedColor(i, false)
                }
                49 -> currentAttrs = currentAttrs.copy(bg = AnsiColor.BLACK)
                in 90..97 -> {
                    currentAttrs = currentAttrs.copy(
                        fg = AnsiColor.fromIndex(p - 90, bright = true)
                    )
                }
                in 100..107 -> {
                    currentAttrs = currentAttrs.copy(
                        bg = AnsiColor.fromIndex(p - 100, bright = true)
                    )
                }
            }
            i++
        }
    }

    private fun handleExtendedColor(startIndex: Int, isForeground: Boolean): Int {
        var i = startIndex
        if (i + 1 >= params.size) return i

        when (params[i + 1]) {
            5 -> {
                // 256-color mode: ESC[38;5;Nm
                if (i + 2 < params.size) {
                    val color = AnsiColor.fromIndex(params[i + 2])
                    currentAttrs = if (isForeground) {
                        currentAttrs.copy(fg = color)
                    } else {
                        currentAttrs.copy(bg = color)
                    }
                    return i + 2
                }
            }
            2 -> {
                // 24-bit RGB mode: ESC[38;2;R;G;Bm
                if (i + 4 < params.size) {
                    val r = params[i + 2].coerceIn(0, 255)
                    val g = params[i + 3].coerceIn(0, 255)
                    val b = params[i + 4].coerceIn(0, 255)
                    val color = Color(
                        0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
                    )
                    currentAttrs = if (isForeground) {
                        currentAttrs.copy(fg = color)
                    } else {
                        currentAttrs.copy(bg = color)
                    }
                    return i + 4
                }
            }
        }
        return i + 1
    }
}
