package com.anvilvm.app.ui.terminal.parser

class TerminalBuffer(
    val rows: Int = 24,
    val cols: Int = 80
) {
    private val cells = Array(rows) { Array(cols) { TerminalCell() } }
    private val scrollback = mutableListOf<Array<TerminalCell>>()
    private val maxScrollback = 10000

    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cursorVisible = true

    fun getCell(row: Int, col: Int): TerminalCell {
        if (row !in 0 until rows || col !in 0 until cols) return TerminalCell()
        return cells[row][col]
    }

    fun setCell(row: Int, col: Int, cell: TerminalCell) {
        if (row in 0 until rows && col in 0 until cols) {
            cells[row][col] = cell
        }
    }

    fun putChar(c: Char, attrs: CellAttributes) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        cells[cursorRow][cursorCol] = TerminalCell(c, attrs)
        cursorCol++
    }

    fun moveCursor(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun lineFeed() {
        if (cursorRow == rows - 1) {
            scrollUp()
        } else {
            cursorRow++
        }
    }

    fun carriageReturn() {
        cursorCol = 0
    }

    fun backspace() {
        if (cursorCol > 0) cursorCol--
    }

    fun tab() {
        cursorCol = ((cursorCol / 8) + 1) * 8
        if (cursorCol >= cols) cursorCol = cols - 1
    }

    private fun scrollUp() {
        if (scrollback.size >= maxScrollback) {
            scrollback.removeAt(0)
        }
        scrollback.add(cells[0].copyOf())

        for (i in 0 until rows - 1) {
            cells[i] = cells[i + 1].copyOf()
        }
        cells[rows - 1] = Array(cols) { TerminalCell() }
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // Cursor to end
                for (c in cursorCol until cols) cells[cursorRow][c] = TerminalCell()
                for (r in cursorRow + 1 until rows) {
                    for (c in 0 until cols) cells[r][c] = TerminalCell()
                }
            }
            1 -> { // Start to cursor
                for (r in 0 until cursorRow) {
                    for (c in 0 until cols) cells[r][c] = TerminalCell()
                }
                for (c in 0..cursorCol) cells[cursorRow][c] = TerminalCell()
            }
            2, 3 -> { // Entire display
                for (r in 0 until rows) {
                    for (c in 0 until cols) cells[r][c] = TerminalCell()
                }
            }
        }
    }

    fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> { // Cursor to end
                for (c in cursorCol until cols) cells[cursorRow][c] = TerminalCell()
            }
            1 -> { // Start to cursor
                for (c in 0..cursorCol) cells[cursorRow][c] = TerminalCell()
            }
            2 -> { // Entire line
                for (c in 0 until cols) cells[cursorRow][c] = TerminalCell()
            }
        }
    }

    fun insertLines(count: Int) {
        val n = count.coerceAtMost(rows - cursorRow)
        for (i in rows - 1 downTo cursorRow + n) {
            cells[i] = cells[i - n].copyOf()
        }
        for (i in cursorRow until cursorRow + n) {
            cells[i] = Array(cols) { TerminalCell() }
        }
    }

    fun deleteLines(count: Int) {
        val n = count.coerceAtMost(rows - cursorRow)
        for (i in cursorRow until rows - n) {
            cells[i] = cells[i + n].copyOf()
        }
        for (i in rows - n until rows) {
            cells[i] = Array(cols) { TerminalCell() }
        }
    }
}
